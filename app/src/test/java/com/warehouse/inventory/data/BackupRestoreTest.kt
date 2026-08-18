package com.warehouse.inventory.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.warehouse.inventory.data.local.ALL_MIGRATIONS
import com.warehouse.inventory.data.local.AppDatabase
import com.warehouse.inventory.data.local.entity.CategoryEntity
import com.warehouse.inventory.util.DatabaseBackupManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Backup validation and the restore round trip.
 *
 * These use a file-backed database rather than the in-memory one the other tests share,
 * because backup copies `context.getDatabasePath(...)` off disk — an in-memory database has
 * no file to copy.
 *
 * The rejection cases matter as much as the happy path: restore replaces every row the user
 * has, so "this file is not a backup" has to be decided before anything is touched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupRestoreTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var manager: DatabaseBackupManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getDatabasePath(AppDatabase.DB_NAME).delete()
        db = openDatabase()
        manager = DatabaseBackupManager(context, db)
    }

    @After
    fun tearDown() {
        runCatching { db.close() }
        context.getDatabasePath(AppDatabase.DB_NAME).delete()
    }

    private fun openDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    private suspend fun backupBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        val result = manager.createBackup(out)
        assertTrue("backup failed: $result", result is DatabaseBackupManager.BackupResult.Success)
        return out.toByteArray()
    }

    /** Manifest for a synthetic archive, with only the fields a test wants to control. */
    private fun manifest(
        app: String = context.packageName,
        format: Int = 1,
        schemaVersion: Int = AppDatabase.VERSION,
        sha256: String? = null
    ): String = JSONObject()
        .put("app", app)
        .put("format", format)
        .put("schemaVersion", schemaVersion)
        .apply { if (sha256 != null) put("databaseSha256", sha256) }
        .toString()

    private fun buildZip(block: (ZipOutputStream) -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use(block)
        return out.toByteArray()
    }

    private fun ZipOutputStream.entry(name: String, body: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(body)
        closeEntry()
    }

    // ------------------------------------------------------------ happy path

    @Test
    fun `a backup of this app inspects as valid`() = runTest {
        db.categoryDao().insert(CategoryEntity(name = RAW_MATERIALS))

        val inspection = manager.inspect(ByteArrayInputStream(backupBytes()))

        assertTrue("was $inspection", inspection is DatabaseBackupManager.Inspection.Valid)
        val valid = inspection as DatabaseBackupManager.Inspection.Valid
        assertEquals(AppDatabase.VERSION, valid.schemaVersion)
        assertTrue(valid.databaseBytes > 0)
    }

    @Test
    fun `restore brings back the rows the backup contained`() = runTest {
        db.categoryDao().insert(CategoryEntity(name = RAW_MATERIALS))
        val bytes = backupBytes()

        // Diverge from the backup: drop the original row and add a different one.
        val original = db.categoryDao().observeAll().first().single()
        db.categoryDao().delete(original)
        db.categoryDao().insert(CategoryEntity(name = LATER_CATEGORY))
        assertEquals(LATER_CATEGORY, db.categoryDao().observeAll().first().single().name)

        val result = manager.restore(ByteArrayInputStream(bytes))
        assertEquals(DatabaseBackupManager.RestoreResult.SuccessRequiresRestart, result)

        // restore() closes the injected instance and swaps the file underneath it, which is
        // why production restarts the process. A fresh instance is the equivalent here.
        db = openDatabase()
        val restored = db.categoryDao().observeAll().first()
        assertEquals(1, restored.size)
        assertEquals(RAW_MATERIALS, restored.single().name)
    }

    // ------------------------------------------------------------ rejections

    @Test
    fun `random bytes are not accepted`() = runTest {
        val inspection = manager.inspect(
            ByteArrayInputStream("this is definitely not a zip".toByteArray())
        )

        // Unparseable as a zip at all, which the manager reports as Unreadable rather than
        // NotABackup: the distinction is only for logging, both refuse the file.
        assertTrue(
            "was $inspection",
            inspection is DatabaseBackupManager.Inspection.Unreadable ||
                inspection == DatabaseBackupManager.Inspection.NotABackup
        )
    }

    @Test
    fun `a zip with no manifest is not a backup`() = runTest {
        val zip = buildZip { it.entry("some/file.txt", "hello".toByteArray()) }

        assertEquals(
            DatabaseBackupManager.Inspection.NotABackup,
            manager.inspect(ByteArrayInputStream(zip))
        )
    }

    @Test
    fun `a backup from another app is rejected`() = runTest {
        val zip = buildZip { out ->
            out.entry("manifest.json", manifest(app = "com.someone.else").toByteArray())
            out.entry("database/warehouse.db", SQLITE_HEADER.toByteArray())
        }

        assertEquals(
            DatabaseBackupManager.Inspection.ForeignApp,
            manager.inspect(ByteArrayInputStream(zip))
        )
    }

    @Test
    fun `a backup from a newer schema is rejected`() = runTest {
        val future = AppDatabase.VERSION + 5
        val zip = buildZip { out ->
            out.entry("manifest.json", manifest(schemaVersion = future).toByteArray())
            out.entry("database/warehouse.db", SQLITE_HEADER.toByteArray())
        }

        // Refused rather than attempted: a newer file may contain tables this build cannot
        // read, and a half-understood restore is worse than none.
        assertEquals(
            DatabaseBackupManager.Inspection.TooNew(future),
            manager.inspect(ByteArrayInputStream(zip))
        )
    }

    @Test
    fun `a backup whose database does not match its checksum is corrupt`() = runTest {
        val zip = buildZip { out ->
            out.entry("manifest.json", manifest(sha256 = "0".repeat(64)).toByteArray())
            out.entry("database/warehouse.db", (SQLITE_HEADER + "payload").toByteArray())
        }

        assertEquals(
            DatabaseBackupManager.Inspection.Corrupt,
            manager.inspect(ByteArrayInputStream(zip))
        )
    }

    @Test
    fun `restoring a rejected file leaves the current data alone`() = runTest {
        db.categoryDao().insert(CategoryEntity(name = RAW_MATERIALS))

        val result = manager.restore(
            ByteArrayInputStream("not a backup at all".toByteArray())
        )

        assertTrue(result is DatabaseBackupManager.RestoreResult.Rejected)
        // Same instance, same row: nothing was closed, deleted or swapped.
        assertEquals(RAW_MATERIALS, db.categoryDao().observeAll().first().single().name)
    }

    @Test
    fun `an entry trying to escape the staging directory cannot write outside it`() = runTest {
        val marker = File(context.filesDir, "zipslip-marker.txt")
        marker.delete()

        val zip = buildZip { out ->
            out.entry("manifest.json", manifest().toByteArray())
            // Classic zip-slip: a path that walks up out of the extraction directory. The
            // manager keeps only the basename, so this must land inside staging or nowhere.
            out.entry("images/../../../files/zipslip-marker.txt", "escaped".toByteArray())
        }

        manager.inspect(ByteArrayInputStream(zip))

        assertFalse("archive escaped the staging directory", marker.exists())
    }

    private companion object {
        /** Every SQLite file starts with this; enough to pass the header check. */
        const val SQLITE_HEADER = "SQLite format 3 "

        // Arabic fixtures, kept as constants so the assertions read cleanly.
        const val RAW_MATERIALS = "مواد خام"
        const val LATER_CATEGORY = "فئة لاحقة"
    }
}
