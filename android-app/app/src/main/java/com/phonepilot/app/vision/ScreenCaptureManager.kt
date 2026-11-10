package com.phonepilot.app.vision

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.OrientationEventListener
import android.view.Surface
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

sealed class ScreenCaptureResult {
    data class Success(
        val bitmap: Bitmap,
        val width: Int,
        val height: Int,
        val timestamp: Long = System.currentTimeMillis(),
    ) : ScreenCaptureResult()

    data class Failure(val error: CaptureError, val errorMessage: String) : ScreenCaptureResult()

    enum class CaptureError {
        SERVICE_UNAVAILABLE, PERMISSION_DENIED, SECURE_WINDOW,
        MEDIA_PROJECTION_STOPPED, NO_CAPTURE_METHOD, DECODING_FAILED,
        CONVERSION_FAILED, INTERNAL_ERROR, CANCELLED,
    }
}

/** Manages screenshot capture through AccessibilityService or MediaProjection. */
class ScreenCaptureManager(
    private val context: Context,
    private val config: VisionConfig = VisionConfig(),
) {
    private val tag = "ScreenCaptureManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val displayMetrics = DisplayMetrics()
    private var currentRotation = 0
    private var isCapturing = false
    private var accessibilityServiceRef: WeakReference<AccessibilityService>? = null
    private var orientationListener: OrientationEventListener? = null
    private var pendingPermissionCallback: ((ScreenCaptureResult) -> Unit)? = null

    init {
        (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplay(Display.DEFAULT_DISPLAY)?.getRealMetrics(displayMetrics)
    }

    fun setAccessibilityService(service: AccessibilityService) {
        accessibilityServiceRef = WeakReference(service)
    }

    fun isCaptureAvailable(): Boolean =
        (Build.VERSION.SDK_INT >= 30 && accessibilityServiceRef?.get() != null) || Build.VERSION.SDK_INT >= 21

    fun isMediaProjectionActive(): Boolean = mediaProjection != null

    @RequiresApi(30)
    suspend fun captureViaAccessibilityService(): ScreenCaptureResult = suspendCancellableCoroutine { cont ->
        val service = accessibilityServiceRef?.get()
        if (service == null) {
            resumeCapture(cont, ScreenCaptureResult.Failure(
                ScreenCaptureResult.CaptureError.SERVICE_UNAVAILABLE,
                "Accessibility service not available",
            ))
            return@suspendCancellableCoroutine
        }
        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                val bitmap = try {
                    Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                } catch (e: Exception) {
                    Log.e(tag, "Could not convert accessibility screenshot", e)
                    null
                } finally {
                    result.hardwareBuffer.close()
                }
                val capture = if (bitmap == null) {
                    ScreenCaptureResult.Failure(
                        ScreenCaptureResult.CaptureError.DECODING_FAILED,
                        "Failed to decode screenshot",
                    )
                } else {
                    ScreenCaptureResult.Success(bitmap, bitmap.width, bitmap.height)
                }
                resumeCapture(cont, capture)
            }

            override fun onFailure(errorCode: Int) {
                Log.e(tag, "Accessibility screenshot failed: $errorCode")
                resumeCapture(cont, ScreenCaptureResult.Failure(
                    ScreenCaptureResult.CaptureError.INTERNAL_ERROR,
                    "Accessibility screenshot failed (reason: $errorCode)",
                ))
            }
        }
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, context.mainExecutor, callback)
        } catch (e: Exception) {
            Log.e(tag, "Failed to queue accessibility screenshot", e)
            resumeCapture(cont, ScreenCaptureResult.Failure(
                ScreenCaptureResult.CaptureError.INTERNAL_ERROR,
                "Failed to queue screenshot request: ${e.message}",
            ))
        }
    }

    private fun resumeCapture(
        continuation: CancellableContinuation<ScreenCaptureResult>,
        result: ScreenCaptureResult,
    ) {
        if (continuation.isActive) continuation.resume(result, onCancellation = null)
    }

    fun createMediaProjectionPermissionIntent(): Intent? {
        if (Build.VERSION.SDK_INT < 21) return null
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return manager.createScreenCaptureIntent()
    }

    fun initMediaProjection(resultCode: Int, data: Intent?): Boolean {
        if (Build.VERSION.SDK_INT < 21 || resultCode != Activity.RESULT_OK || data == null) {
            pendingPermissionCallback?.invoke(ScreenCaptureResult.Failure(
                ScreenCaptureResult.CaptureError.PERMISSION_DENIED,
                "User denied screen capture permission",
            ))
            pendingPermissionCallback = null
            return false
        }
        return try {
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = manager.getMediaProjection(resultCode, data)
            val projection = mediaProjection ?: return false
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(tag, "MediaProjection stopped")
                    cleanupMediaProjection()
                }
            }, handler)
            setupImageReader()
            setupVirtualDisplay()
            startOrientationListener()
            pendingPermissionCallback?.invoke(ScreenCaptureResult.Success(
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                displayMetrics.widthPixels,
                displayMetrics.heightPixels,
            ))
            pendingPermissionCallback = null
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize MediaProjection", e)
            cleanupMediaProjection()
            pendingPermissionCallback?.invoke(ScreenCaptureResult.Failure(
                ScreenCaptureResult.CaptureError.INTERNAL_ERROR,
                "MediaProjection initialization failed: ${e.message}",
            ))
            pendingPermissionCallback = null
            false
        }
    }

    suspend fun requestMediaProjectionPermission(activity: Activity): ScreenCaptureResult = withContext(Dispatchers.IO) {
        if (isMediaProjectionActive()) {
            return@withContext ScreenCaptureResult.Success(
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                displayMetrics.widthPixels,
                displayMetrics.heightPixels,
            )
        }
        val intent = createMediaProjectionPermissionIntent() ?: return@withContext ScreenCaptureResult.Failure(
            ScreenCaptureResult.CaptureError.NO_CAPTURE_METHOD,
            "MediaProjection not supported on this device",
        )
        suspendCancellableCoroutine { cont ->
            pendingPermissionCallback = { result -> resumeCapture(cont, result) }
            cont.invokeOnCancellation {
                pendingPermissionCallback = null
            }
            try {
                activity.startActivityForResult(intent, 0)
            } catch (e: Exception) {
                pendingPermissionCallback = null
                resumeCapture(cont, ScreenCaptureResult.Failure(
                    ScreenCaptureResult.CaptureError.INTERNAL_ERROR,
                    "Failed to start permission activity: ${e.message}",
                ))
            }
        }
    }

    fun captureViaMediaProjection(): ScreenCaptureResult {
        if (mediaProjection == null || imageReader == null) return ScreenCaptureResult.Failure(
            ScreenCaptureResult.CaptureError.MEDIA_PROJECTION_STOPPED,
            "MediaProjection not initialized or stopped",
        )
        val image = imageReader?.acquireLatestImage() ?: return ScreenCaptureResult.Failure(
            ScreenCaptureResult.CaptureError.INTERNAL_ERROR,
            "No image available from ImageReader",
        )
        return try {
            val bitmap = imageToBitmap(image) ?: return ScreenCaptureResult.Failure(
                ScreenCaptureResult.CaptureError.CONVERSION_FAILED,
                "Failed to convert image to bitmap",
            )
            ScreenCaptureResult.Success(bitmap, bitmap.width, bitmap.height)
        } catch (e: Exception) {
            Log.e(tag, "MediaProjection capture failed", e)
            ScreenCaptureResult.Failure(
                ScreenCaptureResult.CaptureError.INTERNAL_ERROR,
                "MediaProjection capture failed: ${e.message}",
            )
        } finally {
            image.close()
        }
    }

    suspend fun capture(): ScreenCaptureResult = withContext(Dispatchers.IO) {
        isCapturing = true
        try {
            if (Build.VERSION.SDK_INT >= 30 && accessibilityServiceRef?.get() != null) {
                val result = captureViaAccessibilityService()
                if (result is ScreenCaptureResult.Success ||
                    (result is ScreenCaptureResult.Failure && result.error == ScreenCaptureResult.CaptureError.SECURE_WINDOW)
                ) return@withContext result
            }
            if (mediaProjection != null && imageReader != null) return@withContext captureViaMediaProjection()
            ScreenCaptureResult.Failure(
                ScreenCaptureResult.CaptureError.NO_CAPTURE_METHOD,
                "No screen capture method available. Enable accessibility service or grant MediaProjection permission.",
            )
        } finally {
            isCapturing = false
        }
    }

    @RequiresApi(21)
    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        if (currentRotation == 0) return cropped
        val matrix = Matrix().apply { postRotate((currentRotation * 90).toFloat()) }
        return Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
    }

    @RequiresApi(21)
    private fun setupImageReader() {
        imageReader?.close()
        imageReader = ImageReader.newInstance(
            displayMetrics.widthPixels.coerceAtLeast(1),
            displayMetrics.heightPixels.coerceAtLeast(1),
            PixelFormat.RGBA_8888,
            2,
        )
    }

    @RequiresApi(21)
    private fun setupVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "PhonePilotScreenCapture",
            displayMetrics.widthPixels,
            displayMetrics.heightPixels,
            displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler,
        )
    }

    private fun startOrientationListener() {
        orientationListener?.disable()
        orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45..134 -> 1
                    in 135..224 -> 2
                    in 225..314 -> 3
                    else -> 0
                }
                if (rotation != currentRotation) {
                    currentRotation = rotation
                    setupVirtualDisplay()
                }
            }
        }.also { it.enable() }
    }

    private fun cleanupMediaProjection() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        orientationListener?.disable()
        orientationListener = null
    }

    fun releaseCapture(result: ScreenCaptureResult.Success) {
        if (!result.bitmap.isRecycled) result.bitmap.recycle()
    }

    fun shutdown() {
        cleanupMediaProjection()
        scope.cancel()
    }
}
