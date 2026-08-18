package com.warehouse.inventory.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Handles product photos. Images are copied into the app's private external
 * Pictures directory so the stored path stays valid after the source (camera temp
 * file or gallery item) is gone.
 */
object ImageStorage {

    private fun imagesDir(context: Context): File {
        val dir = File(context.getExternalFilesDir("Pictures"), "products")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Creates a temp file + content URI to hand to the camera intent. */
    fun createCaptureTarget(context: Context): Pair<Uri, File> {
        val file = File(imagesDir(context), "IMG_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return uri to file
    }

    /** Copies a gallery Uri into app storage and returns the persistent file path. */
    fun copyFromUri(context: Context, source: Uri): String? {
        return try {
            val dest = File(imagesDir(context), "IMG_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(source)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun deleteIfExists(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}
