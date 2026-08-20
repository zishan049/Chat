package com.chat.app.pairing.presentation

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.EnumMap
import java.util.concurrent.Executors

@Composable
fun CameraQrScanner(
    onQrCodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor, QrImageAnalyzer(onQrCodeDetected))
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalyzer
                        )
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private class QrImageAnalyzer(
    private val onQrDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastDetectedTime = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val reader = MultiFormatReader().apply {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
            put(DecodeHintType.TRY_HARDER, java.lang.Boolean.TRUE)
        }
        setHints(hints)
    }

    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastDetectedTime < 1500L) {
            imageProxy.close()
            return
        }

        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = imageProxy.width
        val height = imageProxy.height

        val nv21 = extractLuminance(buffer, width, height, rowStride, pixelStride)
        val rotation = imageProxy.imageInfo.rotationDegrees

        val rotatedData = if (rotation == 90 || rotation == 270) {
            rotateYUV420Degree(nv21, width, height, rotation)
        } else if (rotation == 180) {
            rotateYUV180(nv21, width, height)
        } else {
            nv21
        }

        val finalWidth = if (rotation == 90 || rotation == 270) height else width
        val finalHeight = if (rotation == 90 || rotation == 270) width else height

        val source = PlanarYUVLuminanceSource(
            rotatedData,
            finalWidth,
            finalHeight,
            0,
            0,
            finalWidth,
            finalHeight,
            false
        )

        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        try {
            val result = reader.decodeWithState(binaryBitmap)
            if (result != null && result.text.isNotBlank()) {
                lastDetectedTime = System.currentTimeMillis()
                mainHandler.post {
                    onQrDetected(result.text)
                }
            }
        } catch (_: NotFoundException) {
            // Normal when no barcode in frame
        } catch (_: Exception) {
        } finally {
            reader.reset()
            imageProxy.close()
        }
    }

    private fun extractLuminance(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    ): ByteArray {
        val data = ByteArray(width * height)
        if (rowStride == width && pixelStride == 1) {
            buffer.position(0)
            buffer.get(data, 0, width * height)
            return data
        }

        var outPos = 0
        val row = ByteArray(rowStride)
        for (y in 0 until height) {
            buffer.position(y * rowStride)
            if (pixelStride == 1) {
                buffer.get(data, outPos, width)
            } else {
                buffer.get(row, 0, minOf(rowStride, buffer.remaining()))
                for (x in 0 until width) {
                    data[outPos + x] = row[x * pixelStride]
                }
            }
            outPos += width
        }
        return data
    }

    private fun rotateYUV420Degree(data: ByteArray, width: Int, height: Int, rotation: Int): ByteArray {
        val rotated = ByteArray(data.size)
        if (rotation == 90) {
            var i = 0
            for (x in 0 until width) {
                for (y in height - 1 downTo 0) {
                    rotated[i++] = data[y * width + x]
                }
            }
            return rotated
        } else if (rotation == 270) {
            var i = 0
            for (x in width - 1 downTo 0) {
                for (y in 0 until height) {
                    rotated[i++] = data[y * width + x]
                }
            }
            return rotated
        }
        return data
    }

    private fun rotateYUV180(data: ByteArray, width: Int, height: Int): ByteArray {
        val rotated = ByteArray(data.size)
        var i = 0
        for (y in height - 1 downTo 0) {
            for (x in width - 1 downTo 0) {
                rotated[i++] = data[y * width + x]
            }
        }
        return rotated
    }
}
