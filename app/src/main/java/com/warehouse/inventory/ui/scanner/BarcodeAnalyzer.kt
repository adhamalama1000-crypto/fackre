package com.warehouse.inventory.ui.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * CameraX analyzer that reads barcodes and QR codes from the preview stream.
 *
 * Formats are restricted to the ones actually used on warehouse and retail labels rather
 * than left at "all formats": ML Kit scans a narrower set noticeably faster, which matters
 * on the low-end handsets these apps run on.
 *
 * The first successful read latches [detected] and stops reporting. Without that, a barcode
 * held in frame is decoded on every one of ~30 frames a second and the caller would navigate
 * or submit repeatedly.
 */
class BarcodeAnalyzer(
    private val onDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_CODABAR,
                Barcode.FORMAT_DATA_MATRIX
            )
            .build()
    )

    @Volatile
    private var detected = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val mediaImage = image.image
        if (mediaImage == null || detected) {
            image.close()
            return
        }
        scanner.process(InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees))
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstNotNullOfOrNull { barcode ->
                    barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() }
                }
                if (value != null && !detected) {
                    detected = true
                    onDetected(value)
                }
            }
            // Closing here, not in the success listener, so a decode failure cannot stall
            // the pipeline by leaking the ImageProxy.
            .addOnCompleteListener { image.close() }
    }

    /** Re-arms the analyzer after the caller has handled (and dismissed) a result. */
    fun reset() {
        detected = false
    }

    fun release() {
        runCatching { scanner.close() }
    }
}
