package com.warehouse.inventory.util

import android.content.Context
import com.warehouse.inventory.BuildConfig
import com.warehouse.inventory.data.local.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local backup and restore of everything the app owns: the Room database and the product
 * photos, packaged as a single `.zip` the user saves wherever they like via the system file
 * picker.
 *
 * The archive layout is:
 * ```
 * manifest.json          metadata + SHA-256 of the database
 * database/warehouse.db  the SQLite file, WAL-checkpointed before copying
 * images/<file>          product photos
 * ```
 *
 * Restore is validated before anything is touched. A file is only accepted if it carries a
 * manifest naming this app, an archive format this build understands, a schema version not
 * newer than this build's, a database whose SHA-256 matches the manifest, and a real SQLite
 * header. It is extracted to a staging directory first and only swapped in once every check
 * passes, so a truncated or foreign zip cannot leave a half-restored database behind.
 */
@Singleton
class DatabaseBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase
) {

    /** What a candidate backup file turned out to be. */
    sealed interface Inspection {
        data class Valid(
            val createdAt: Long,
            val schemaVersion: Int,
            val appVersion: String,
            val imageCount: Int,
            val databaseBytes: Long
        ) : Inspection

        /** Not a zip, or no manifest inside — probably not a backup at all. */
        data object NotABackup : Inspection

        /** A backup, but from a different app. */
        data object ForeignApp : Inspection

        /** Written by a newer build whose schema this version cannot read. */
        data class TooNew(val schemaVersion: Int) : Inspection

        /** Manifest and payload disagree, or the database is not valid SQLite. */
        data object Corrupt : Inspection

        data class Unreadable(val message: String?) : Inspection
    }

    sealed interface BackupResult {
        data class Success(val bytesWritten: Long, val imageCount: Int) : BackupResult
        data class Failed(val message: String?) : BackupResult
    }

    sealed interface RestoreResult {
        /**
         * The database on disk has been replaced. The Room instance held by the DI graph is
         * now closed and stale, so the process must restart before anything reads again.
         */
        data object SuccessRequiresRestart : RestoreResult
        data class Rejected(val inspection: Inspection) : RestoreResult
        data class Failed(val message: String?) : RestoreResult
    }

    // ---------------------------------------------------------------- backup

    suspend fun createBackup(target: OutputStream): BackupResult = withContext(Dispatchers.IO) {
        try {
            // Fold the write-ahead log into the main database file first; without this the
            // copied .db can be missing the most recent commits, which are still in the WAL.
            checkpointWal()

            val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
            if (!dbFile.exists()) return@withContext BackupResult.Failed("no_database")

            val images = imagesDir().listFiles()?.filter { it.isFile }.orEmpty()
            val digest = sha256(dbFile)
            var written = 0L

            ZipOutputStream(target.buffered()).use { zip ->
                val manifest = JSONObject().apply {
                    put(KEY_APP, context.packageName)
                    put(KEY_FORMAT, ARCHIVE_FORMAT)
                    put(KEY_SCHEMA, AppDatabase.VERSION)
                    put(KEY_CREATED_AT, System.currentTimeMillis())
                    put(KEY_APP_VERSION, BuildConfig.VERSION_NAME)
                    put(KEY_DB_SHA256, digest)
                    put(KEY_DB_BYTES, dbFile.length())
                    put(KEY_IMAGE_COUNT, images.size)
                }
                zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
                zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry(ENTRY_DB))
                dbFile.inputStream().use { written += it.copyTo(zip) }
                zip.closeEntry()

                images.forEach { image ->
                    zip.putNextEntry(ZipEntry("$ENTRY_IMAGE_DIR${image.name}"))
                    image.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            BackupResult.Success(written, images.size)
        } catch (e: Exception) {
            BackupResult.Failed(e.message)
        }
    }

    // --------------------------------------------------------------- restore

    /**
     * Extracts [source] to a staging directory and validates it, without touching the live
     * database. The staging directory is returned alongside the verdict so [commit] can
     * finish the job without re-reading the (already consumed) stream.
     */
    private fun stage(source: InputStream): Pair<Inspection, File?> {
        val staging = File(context.cacheDir, STAGING_DIR)
        staging.deleteRecursively()
        staging.mkdirs()

        var manifest: JSONObject? = null
        var dbFile: File? = null
        val imagesStaging = File(staging, "images").apply { mkdirs() }

        try {
            ZipInputStream(source.buffered()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == ENTRY_MANIFEST -> {
                            manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        }
                        name == ENTRY_DB -> {
                            dbFile = File(staging, "warehouse.db").also { file ->
                                file.outputStream().use { zip.copyTo(it) }
                            }
                        }
                        name.startsWith(ENTRY_IMAGE_DIR) && !entry.isDirectory -> {
                            // Guard against zip-slip: only ever keep the basename, so an
                            // entry like "images/../../databases/x" cannot escape staging.
                            val safeName = File(name).name
                            if (safeName.isNotBlank()) {
                                File(imagesStaging, safeName).outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            staging.deleteRecursively()
            return Inspection.Unreadable(e.message) to null
        }

        val json = manifest ?: run {
            staging.deleteRecursively()
            return Inspection.NotABackup to null
        }
        val db = dbFile ?: run {
            staging.deleteRecursively()
            return Inspection.Corrupt to null
        }

        if (json.optString(KEY_APP) != context.packageName) {
            staging.deleteRecursively()
            return Inspection.ForeignApp to null
        }
        val schema = json.optInt(KEY_SCHEMA, -1)
        val format = json.optInt(KEY_FORMAT, -1)
        if (format > ARCHIVE_FORMAT || schema > AppDatabase.VERSION || schema <= 0) {
            staging.deleteRecursively()
            return Inspection.TooNew(schema) to null
        }
        if (json.optString(KEY_DB_SHA256) != sha256(db) || !isSqliteFile(db)) {
            staging.deleteRecursively()
            return Inspection.Corrupt to null
        }

        val inspection = Inspection.Valid(
            createdAt = json.optLong(KEY_CREATED_AT, 0L),
            schemaVersion = schema,
            appVersion = json.optString(KEY_APP_VERSION),
            imageCount = json.optInt(KEY_IMAGE_COUNT, 0),
            databaseBytes = db.length()
        )
        return inspection to staging
    }

    /** Validates a candidate file without modifying anything, for the confirmation dialog. */
    suspend fun inspect(source: InputStream): Inspection = withContext(Dispatchers.IO) {
        val (inspection, staging) = stage(source)
        staging?.deleteRecursively()
        inspection
    }

    /**
     * Validates and then installs a backup, replacing the live database and product photos.
     *
     * Caller must confirm with the user first: this overwrites current data. On success the
     * app has to restart, because the Room instance the DI graph is holding is closed here
     * and its file has been swapped underneath it.
     */
    suspend fun restore(source: InputStream): RestoreResult = withContext(Dispatchers.IO) {
        val (inspection, staging) = stage(source)
        if (inspection !is Inspection.Valid || staging == null) {
            staging?.deleteRecursively()
            return@withContext RestoreResult.Rejected(inspection)
        }
        try {
            checkpointWal()
            database.close()

            val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
            // The sidecar files describe the OLD database; leaving either in place next to a
            // replaced .db would corrupt it, so both go.
            File(dbFile.parentFile, "${AppDatabase.DB_NAME}-wal").delete()
            File(dbFile.parentFile, "${AppDatabase.DB_NAME}-shm").delete()
            dbFile.delete()

            dbFile.parentFile?.mkdirs()
            File(staging, "warehouse.db").inputStream().use { input ->
                dbFile.outputStream().use { input.copyTo(it) }
            }

            val restoredImages = File(staging, "images")
            if (restoredImages.exists()) {
                val target = imagesDir()
                target.deleteRecursively()
                target.mkdirs()
                restoredImages.listFiles()?.forEach { image ->
                    image.inputStream().use { input ->
                        File(target, image.name).outputStream().use { input.copyTo(it) }
                    }
                }
            }

            RestoreResult.SuccessRequiresRestart
        } catch (e: Exception) {
            RestoreResult.Failed(e.message)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Suggested filename for a new backup. */
    fun suggestedFileName(): String = "rasid_backup_${DateUtils.fileStamp()}.zip"

    // ---------------------------------------------------------------- helpers

    private fun checkpointWal() {
        runCatching {
            database.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { it.moveToFirst() }
        }
    }

    private fun imagesDir(): File = File(context.getExternalFilesDir("Pictures"), "products")

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Every SQLite database begins with this 16-byte magic string. */
    private fun isSqliteFile(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(16)
            if (input.read(header) != 16) return false
            header.toString(Charsets.US_ASCII).startsWith("SQLite format 3")
        }
    }.getOrDefault(false)

    private companion object {
        const val ARCHIVE_FORMAT = 1
        const val STAGING_DIR = "restore_staging"

        const val ENTRY_MANIFEST = "manifest.json"
        const val ENTRY_DB = "database/warehouse.db"
        const val ENTRY_IMAGE_DIR = "images/"

        const val KEY_APP = "app"
        const val KEY_FORMAT = "format"
        const val KEY_SCHEMA = "schemaVersion"
        const val KEY_CREATED_AT = "createdAt"
        const val KEY_APP_VERSION = "appVersionName"
        const val KEY_DB_SHA256 = "databaseSha256"
        const val KEY_DB_BYTES = "databaseBytes"
        const val KEY_IMAGE_COUNT = "imageCount"
    }
}
