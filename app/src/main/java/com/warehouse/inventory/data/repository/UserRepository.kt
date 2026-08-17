package com.warehouse.inventory.data.repository

import com.warehouse.inventory.data.local.dao.CategoryDao
import com.warehouse.inventory.data.local.dao.UserDao
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

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
    private val categoryDao: CategoryDao
) {

    fun observeUsers(): Flow<List<UserEntity>> = userDao.observeAll()

    suspend fun login(email: String, password: String): LoginResult {
        val user = userDao.getByEmail(email.trim().lowercase()) ?: return LoginResult.InvalidCredentials
        return if (PasswordHasher.verify(password, user.salt, user.passwordHash)) {
            LoginResult.Success(user)
        } else {
            LoginResult.InvalidCredentials
        }
    }

    /** Returns true on success, false if the email already exists. */
    suspend fun addUser(name: String, email: String, password: String, role: UserRole): Boolean {
        val normalized = email.trim().lowercase()
        if (userDao.getByEmail(normalized) != null) return false
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hash(password, salt)
        return try {
            userDao.insert(
                UserEntity(
                    name = name.trim(),
                    email = normalized,
                    passwordHash = hash,
                    salt = salt,
                    role = role
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteUser(user: UserEntity) = userDao.delete(user)

    /**
     * Seeds a default admin account and starter categories the first time the app runs.
     * Default admin -> email: admin@warehouse.com  password: admin123
     */
    suspend fun seedIfEmpty() {
        if (userDao.count() == 0) {
            addUser(
                name = "Admin",
                email = "admin@warehouse.com",
                password = "admin123",
                role = UserRole.ADMIN
            )
        }
        if (categoryDao.count() == 0) {
            listOf(
                "الكاميرات", "المنزل الذكي", "الإنتركم",
                "التحكم بالدخول", "الشبكات", "الأدوات", "الإكسسوارات"
            ).forEach { name ->
                runCatching { categoryDao.insert(CategoryEntity(name = name)) }
            }
        }
    }
}
