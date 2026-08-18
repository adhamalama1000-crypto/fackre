package com.warehouse.inventory.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns report data into a CSV or PDF file and hands back a shareable [Uri].
 *
 * Files are written into `cacheDir/exports` and exposed through the existing
 * `FileProvider`, then offered via the system share sheet. That deliberately avoids
 * `WRITE_EXTERNAL_STORAGE` (unavailable from API 29 anyway) while still letting the user
 * put the file wherever they want — Files, Drive, email, WhatsApp — in one step.
 */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** A file that has been written and is ready to share. */
    data class Export(val uri: Uri, val fileName: String, val mimeType: String)

    suspend fun exportCsv(baseName: String, csv: CsvWriter): Export = withContext(Dispatchers.IO) {
        val file = newFile(baseName, "csv")
        file.outputStream().use { it.write(csv.toByteArray()) }
        Export(uriFor(file), file.name, MIME_CSV)
    }

    suspend fun exportPdf(baseName: String, document: PdfReportWriter.Document): Export =
        withContext(Dispatchers.IO) {
            val file = newFile(baseName, "pdf")
            file.outputStream().use { PdfReportWriter().write(it, document) }
            Export(uriFor(file), file.name, MIME_PDF)
        }

    /** Share sheet intent for a finished [Export]. */
    fun shareIntent(export: Export, subject: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = export.mimeType
            putExtra(Intent.EXTRA_STREAM, export.uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** Viewer intent, for opening a PDF directly in a reader. */
    fun openIntent(export: Export): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(export.uri, export.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun newFile(baseName: String, extension: String): File {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        pruneOldExports(dir)
        // Timestamped so repeated exports never overwrite an earlier one the user is still
        // sharing, and so the filename says when the data was pulled.
        return File(dir, "${baseName}_${DateUtils.fileStamp()}.$extension")
    }

    private fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Keeps the export cache bounded. Exports are transient — once shared, the copy in our
     * cache has no further purpose — so anything older than a day goes.
     */
    private fun pruneOldExports(dir: File) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MILLIS
        runCatching {
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) file.delete()
            }
        }
    }

    private companion object {
        const val EXPORT_DIR = "exports"
        const val MIME_CSV = "text/csv"
        const val MIME_PDF = "application/pdf"
        const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
    }
}
