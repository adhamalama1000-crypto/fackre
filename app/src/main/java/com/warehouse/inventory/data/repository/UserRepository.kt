package com.warehouse.inventory.data.repository

import com.warehouse.inventory.data.local.dao.CategoryDao
import com.warehouse.inventory.data.local.dao.UserDao
import com.warehouse.inventory.data.local.entity.AuditAction
import com.warehouse.inventory.data.local.entity.CategoryEntity
import com.warehouse.inventory.data.local.entity.UserEntity
import com.warehouse.inventory.data.local.entity.UserRole
import com.warehouse.inventory.util.PasswordHasher
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LoginResult {
    data class Success(val user: UserEntity) : LoginResult
    data object InvalidCredentials : LoginResult
}

/** Minimum password length accepted when creating or changing a password. */
const val MIN_PASSWORD_LENGTH = 6

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
    private val categoryDao: CategoryDao,
    private val warehouseRepository: WarehouseRepository,
    private val permissionChecker: PermissionChecker,
    private val auditLogRepository: AuditLogRepository
) {

    fun observeUsers(): Flow<List<UserEntity>> = userDao.observeAll()

    suspend fun getById(id: Long): UserEntity? = userDao.getById(id)

    suspend fun login(email: String, password: String): LoginResult {
        val user = userDao.getByEmail(email.trim().lowercase())
            ?: return LoginResult.InvalidCredentials
        if (!PasswordHasher.verify(password, user.salt, user.passwordHash)) {
            return LoginResult.InvalidCredentials
        }
        // Recorded against the user who just authenticated: there is no session yet, so
        // PermissionChecker could not resolve an actor here.
        auditLogRepository.recordAs(
            user, AuditAction.LOGIN, "user", user.id, user.name,
            "تسجيل دخول بنجاح"
        )
        return LoginResult.Success(user)
    }

    suspend fun recordLogout(user: UserEntity?) {
        auditLogRepository.recordAs(
            user, AuditAction.LOGOUT, "user", user?.id, user?.name.orEmpty()
        )
    }

    /**
     * Creates a login account. Admin-only.
     *
     * The password is never stored: PBKDF2-HMAC-SHA256 over a fresh per-user salt, and only
     * the derived hash and that salt are persisted.
     */
    suspend fun addUser(
        name: String,
        email: String,
        password: String,
        role: UserRole
    ): SaveResult {
        val cleanName = name.trim()
        val normalizedEmail = email.trim().lowercase()
        if (cleanName.isEmpty() || normalizedEmail.isEmpty()) return SaveResult.MissingRequiredField
        if (password.length < MIN_PASSWORD_LENGTH) return SaveResult.MissingRequiredField

        val actor = permissionChecker.requireAdmin("user_create") ?: return SaveResult.NotAuthorized

        if (userDao.getByEmail(normalizedEmail) != null) {
            return SaveResult.Duplicate(DuplicateField.EMAIL)
        }

        val salt = PasswordHasher.generateSalt()
        return try {
            val id = userDao.insert(
                UserEntity(
                    name = cleanName,
                    email = normalizedEmail,
                    passwordHash = PasswordHasher.hash(password, salt),
                    salt = salt,
                    role = role
                )
            )
            auditLogRepository.recordAs(
                actor, AuditAction.USER_CREATED, "user", id, cleanName,
                "الصلاحية: ${role.name}"
            )
            SaveResult.Success(id)
        } catch (e: Exception) {
            SaveResult.Duplicate(DuplicateField.EMAIL)
        }
    }

    /**
     * Updates a user's name, role and optionally password. Admin-only.
     *
     * Refuses to demote the last admin, which would otherwise lock every administrative
     * function — including user management — out of the app permanently.
     * Passing a blank [newPassword] leaves the existing credentials untouched.
     */
    suspend fun updateUser(
        id: Long,
        name: String,
        role: UserRole,
        newPassword: String = ""
    ): SaveResult {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return SaveResult.MissingRequiredField
        if (newPassword.isNotEmpty() && newPassword.length < MIN_PASSWORD_LENGTH) {
            return SaveResult.MissingRequiredField
        }

        val actor = permissionChecker.requireAdmin("user_edit") ?: return SaveResult.NotAuthorized
        val existing = userDao.getById(id) ?: return SaveResult.NotFound

        if (existing.role == UserRole.ADMIN && role != UserRole.ADMIN && userDao.adminCount() <= 1) {
            return SaveResult.Failed("last_admin")
        }

        return try {
            val updated = if (newPassword.isNotEmpty()) {
                val salt = PasswordHasher.generateSalt()
                existing.copy(
                    name = cleanName,
                    role = role,
                    salt = salt,
                    passwordHash = PasswordHasher.hash(newPassword, salt),
                    updatedAt = System.currentTimeMillis(),
                    synced = false
                )
            } else {
                existing.copy(
                    name = cleanName,
                    role = role,
                    updatedAt = System.currentTimeMillis(),
                    synced = false
                )
            }
            userDao.update(updated)
            auditLogRepository.recordAs(
                actor, AuditAction.USER_EDITED, "user", id, cleanName,
                buildString {
                    append("الصلاحية: ${role.name}")
                    if (newPassword.isNotEmpty()) append(" • تم تغيير كلمة المرور")
                }
            )
            SaveResult.Success(id)
        } catch (e: Exception) {
            SaveResult.Failed(e.message)
        }
    }

    /**
     * Deletes a login account. Admin-only.
     *
     * Blocks two self-inflicted lockouts: deleting the account you are signed in as, and
     * deleting the last remaining admin.
     */
    suspend fun deleteUser(user: UserEntity): DeleteResult {
        val actor = permissionChecker.requireAdmin("user_delete")
            ?: return DeleteResult.NotAuthorized
        if (actor.id == user.id) return DeleteResult.InUse("self")
        if (user.role == UserRole.ADMIN && userDao.adminCount() <= 1) {
            return DeleteResult.InUse("last_admin")
        }
        return try {
            userDao.delete(user)
            auditLogRepository.recordAs(
                actor, AuditAction.USER_DELETED, "user", user.id, user.name
            )
            DeleteResult.Success
        } catch (e: Exception) {
            DeleteResult.Failed(e.message)
        }
    }

    /**
     * First-launch seeding: a default admin, starter categories and the default warehouse
     * that stock needs somewhere to live in.
     *
     * Runs before anyone has signed in, so it writes through the DAO directly rather than
     * through the admin-guarded [addUser] — there is no session for a permission check to
     * resolve yet.
     *
     * Default admin -> email: admin@warehouse.com  password: admin123
     */
    suspend fun seedIfEmpty() {
        if (userDao.count() == 0) {
            val salt = PasswordHasher.generateSalt()
            runCatching {
                userDao.insert(
                    UserEntity(
                        name = "Admin",
                        email = "admin@warehouse.com",
                        passwordHash = PasswordHasher.hash("admin123", salt),
                        salt = salt,
                        role = UserRole.ADMIN
                    )
                )
            }
        }
        if (categoryDao.count() == 0) {
            listOf(
                "الكاميرات", "المنزل الذكي", "الإنتركم",
                "التحكم بالدخول", "الشبكات", "الأدوات", "الإكسسوارات"
            ).forEach { name ->
                runCatching { categoryDao.insert(CategoryEntity(name = name)) }
            }
        }
        runCatching { warehouseRepository.ensureDefaultWarehouse() }
    }
}
