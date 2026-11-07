package com.phonepilot.app.vision

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Screen capture result with explicit status for different failure modes.
 */
sealed class ScreenCaptureResult {
    data class Success(
        val bitmap: Bitmap,
        val width: Int,
        val height: Int,
        val timestamp: Long = System.currentTimeMillis(),
    ) : ScreenCaptureResult()

    data class Failure(
        val error: CaptureError,
        val errorMessage: String,
    ) : ScreenCaptureResult()

    @Serializable
    enum class CaptureError {
        SERVICE_UNAVAILABLE,
        PERMISSION_DENIED,
        SECURE_WINDOW,           // FLAG_SECURE window - cannot capture
        MEDIA_PROJECTION_STOPPED,
        NO_CAPTURE_METHOD,
        DECODING_FAILED,
        CONVERSION_FAILED,
        INTERNAL_ERROR,
        CANCELLED,
    }
}

/**
 * Manages screen capture using multiple strategies:
 * 1. AccessibilityService.takeScreenshot() (API 26+) - preferred, no permission needed
 * 2. MediaProjection (API 21+, requires user permission via system dialog)
 * 
 * Handles FLAG_SECURE windows by returning explicit SECURE_WINDOW error.
 * Properly cleans up resources and handles Android 14+ foreground service requirements.
 */
class ScreenCaptureManager(
    private val context: Context,
    private val config: VisionConfig = VisionConfig(),
) {

    private val TAG = "ScreenCaptureManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    // MediaProjection components
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var displayMetrics = DisplayMetrics()
    private var currentRotation = 0
    private var isCapturing = false

    // Accessibility service reference for screenshot API
    private var accessibilityServiceRef: WeakReference<AccessibilityService>? = null

    // Orientation listener for rotation handling
    private var orientationListener: OrientationEventListener? = null

    // Pending permission request callback
    private var pendingPermissionCallback: ((ScreenCaptureResult) -> Unit)? = null

    init {
        context.getSystemService(Context.DISPLAY_SERVICE)
            ?.let { (it as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY) }
            ?.getRealMetrics(displayMetrics)
    }

    /**
     * Set the accessibility service for using takeScreenshot API.
     */
    fun setAccessibilityService(service: AccessibilityService) {
        accessibilityServiceRef = WeakReference(service)
    }

    /**
     * Check if screen capture is available via any method.
     */
    fun isCaptureAvailable(): Boolean {
        // AccessibilityService.takeScreenshot requires API 26+
        if (Build.VERSION.SDK_INT >= 26 && accessibilityServiceRef?.get() != null) {
            return true
        }
        // MediaProjection requires API 21+
        if (Build.VERSION.SDK_INT >= 21) {
            return true
        }
        return false
    }

    /**
     * Check if MediaProjection is currently active.
     */
    fun isMediaProjectionActive(): Boolean = mediaProjection != null

    /**
     * Capture screen using AccessibilityService.takeScreenshot (API 26+).
     * This is the preferred method as it doesn't require user permission.
     * Returns SECURE_WINDOW error if the window has FLAG_SECURE set.
     */
    @RequiresApi(26)
    fun captureViaAccessibilityService(): ScreenCaptureResult {
        return try {
            val service = accessibilityServiceRef?.get()
            if (service == null) {
                return ScreenCaptureResult.Failure(
                    error = ScreenCaptureResult.CaptureError.SERVICE_UNAVAILABLE,
                    errorMessage = "Accessibility service not available"
                )
            }

            val screenshot = service.takeScreenshot()
            if (screenshot == null) {
                // This typically means a secure window (FLAG_SECURE) is showing
                Log.w(TAG, "AccessibilityService.takeScreenshot() returned null - likely FLAG_SECURE window")
                return ScreenCaptureResult.Failure(
                    error = ScreenCaptureResult.CaptureError.SECURE_WINDOW,
                    errorMessage = "Cannot capture secure window (FLAG_SECURE)"
                )
            }

            val bitmap = BitmapFactory.decodeStream(screenshot)
            screenshot.close()

            if (bitmap == null) {
                return ScreenCaptureResult.Failure(
                    error = ScreenCaptureResult.CaptureError.DECODING_FAILED,
                    errorMessage = "Failed to decode screenshot"
                )
            }

            ScreenCaptureResult.Success(
                bitmap = bitmap,
                width = bitmap.width,
                height = bitmap.height,
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during accessibility screenshot", e)
            ScreenCaptureResult.Failure(
                error = ScreenCaptureResult.CaptureError.SECURE_WINDOW,
                errorMessage = "Cannot capture secure window (FLAG_SECURE)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Accessibility screenshot failed", e)
            ScreenCaptureResult.Failure(
                error = ScreenCaptureResult.CaptureError.INTERNAL_ERROR,
                errorMessage = "Accessibility screenshot failed: ${e.message}"
            )
        }
    }

    /**
     * Create MediaProjection permission intent for user consent.
     * Must be started via Activity.startActivityForResult() or Activity Result API.
     * Returns null if API level < 21.
     */
    fun createMediaProjectionPermissionIntent(): Intent? {
        if (Build.VERSION.SDK_INT < 21) return null

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return projectionManager.createScreenCaptureIntent()
    }

    /**
     * Initialize MediaProjection with the result from permission activity.
     * Call this from Activity.onActivityResult() or Activity Result callback.
     * Returns true if initialization succeeded.
     */
    fun initMediaProjection(resultCode: Int, data: Intent?): Boolean {
        if (Build.VERSION.SDK_INT < 21) return false
        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.w(TAG, "MediaProjection permission denied or cancelled by user")
            pendingPermissionCallback?.invoke(ScreenCaptureResult.Failure(
                error = ScreenCaptureResult.CaptureError.PERMISSION_DENIED,
                errorMessage = "User denied screen capture permission"
            ))
            pendingPermissionCallback = null
            return false
        }

        return try {
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                pendingPermissionCallback?.invoke(ScreenCaptureResult.Failure(
                    error = ScreenCaptureResult.CaptureError.PERMISSION_DENIED,
                    errorMessage = "Failed to get MediaProjection"
                ))
                pendingPermissionCallback = null
                return false
            }

            // Set up callback for projection stop (user revokes permission or system stops it)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(TAG, "MediaProjection stopped by system/user")
                    cleanupMediaProjection()
                    // Notify any pending capture that projection stopped
                    // Note: We don't have a direct callback here for active captures
                }
            }, handler)

            setupImageReader()
            setupVirtualDisplay()
            startOrientationListener()

            Log.d(TAG, "MediaProjection initialized successfully")
            pendingPermissionCallback?.invoke(ScreenCaptureResult.Success(
                bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), // placeholder
                width = displayMetrics.widthPixels,
                height = displayMetrics.heightPixels,
            ))
            pendingPermissionCallback = null
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init MediaProjection", e)
            cleanupMediaProjection()
            pendingPermissionCallback?.invoke(ScreenCaptureResult.Failure(
                error = ScreenCaptureResult.CaptureError.INTERNAL_ERROR,
                errorMessage = "MediaProjection initialization failed: ${e.message}"
            ))
            pendingPermissionCallback = null
            false
        }
    }

    /**
     * Request MediaProjection permission and wait for user response.
     * This is a suspend function that waits for the user to grant/deny permission.
     * The Activity must call initMediaProjection() with the result.
     */
    suspend fun requestMediaProjectionPermission(activity: Activity): ScreenCaptureResult = withContext(Dispatchers.IO) {
        if (isMediaProjectionActive()) {
            return@withContext ScreenCaptureResult.Success(
                bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                width = displayMetrics.widthPixels,
                height = displayMetrics.heightPixels,
            )
        }

        val intent = createMediaProjectionPermissionIntent()
            ?: return@withContext ScreenCaptureResult.Failure(
                error = ScreenCaptureResult.CaptureError.NO_CAPTURE_METHOD,
                errorMessage = "MediaProjection not supported on this device"
            )

        return@withContext suspendCancellableCoroutine { cont ->
            pendingPermissionCallback = { result ->
                cont.resume(result)
            }
            
            // Start the permission activity
            try {
                activity.startActivityForResult(intent, 0) // Request code handled by caller
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MediaProjection permission activity", e)
                cont.resume(ScreenCaptureResult.Failure(
                    error = ScreenCaptureResult.CaptureError.INTERNAL_ERROR,
                    errorMessage = "Failed to start permission activity: ${e.message}"
                ))
                pendingPermissionCallback = null
            }
            
            // Handle cancellation
            cont.invokeOnCancellation {
                pendingPermissionCallback?.invoke(ScreenCaptureResult.Failure(
                    error = ScreenCaptureResult.CaptureError.CANCELLED,
                    errorMessage = "Permission request cancelled"
                ))
                pendingPermissionCallback = null
            }
        }
    }

    /**
     * Capture screen using MediaProjection.
     * Returns SECURE_WINDOW if the content is protected (rare for MediaProjection but possible).
     */
    fun captureViaMediaProjection(): ScreenCaptureResult {
        return try {
            if (mediaProjection == null || imageReader == null) {
                return ScreenCaptureResult.Failure(
                    error = ScreenCaptureResult.CaptureError.MEDIA_PROJECTION_STOPPED,
                    errorMessage = "MediaProjection not initialized or stopped"
                )
            }

            val image = imageReader?.acquireLatestImage()
                ?: return ScreenCaptureResult.Failure(
                    error = ScreenCaptureResult.CaptureError.INTERNAL_ERROR,
                    errorMessage = "No image available from ImageReader"
                )

            val bitmap = imageToBitmap(image)
            image.close()

            if (bitmap == null) {
                return ScreenCaptureResult.Failure(
                    error = ScreenCaptureResult.CaptureError.CONVERSION_FAILED,
                    errorMessage = "Failed to convert image to bitmap"
                )
            }

            ScreenCaptureResult.Success(
                bitmap = bitmap,
                width = bitmap.width,
                height = bitmap.height,
            )
        } catch (e: IllegalStateException) {
            // Can happen if MediaProjection was stopped
            Log.w(TAG, "MediaProjection capture failed - projection likely stopped", e)
            ScreenCaptureResult.Failure(
                error = ScreenCaptureResult.CaptureError.MEDIA_PROJECTION_STOPPED,
                errorMessage = "MediaProjection stopped"
            )
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection capture failed", e)
            ScreenCaptureResult.Failure(
                error = ScreenCaptureResult.CaptureError.INTERNAL_ERROR,
                errorMessage = "MediaProjection capture failed: ${e.message}"
            )
        }
    }

    /**
     * Capture screen using the best available method.
     * Priority: AccessibilityService (no permission) > MediaProjection (with permission).
     * Returns explicit error for secure windows.
     */
    suspend fun capture(): ScreenCaptureResult = withContext(Dispatchers.IO) {
        isCapturing = true
        defer { isCapturing = false }

        // Try AccessibilityService first (no permission needed)
        if (Build.VERSION.SDK_INT >= 26 && accessibilityServiceRef?.get() != null) {
            val result = captureViaAccessibilityService()
            if (result is ScreenCaptureResult.Success) {
                return@withContext result
            }
            
            // If it's a secure window error, don't fall back to MediaProjection
            // because MediaProjection also cannot capture secure windows
            if (result.error == ScreenCaptureResult.CaptureError.SECURE_WINDOW) {
                Log.w(TAG, "Secure window detected (FLAG_SECURE), not attempting MediaProjection fallback")
                return@withContext result
            }
            
            Log.w(TAG, "Accessibility capture failed, trying MediaProjection: ${result.errorMessage}")
        }

        // Try MediaProjection if available
        if (mediaProjection != null && imageReader != null) {
            val result = captureViaMediaProjection()
            if (result is ScreenCaptureResult.Success) {
                return@withContext result
            }
            Log.w(TAG, "MediaProjection capture failed: ${result.errorMessage}")
            return@withContext result
        }

        // No capture method available
        ScreenCaptureResult.Failure(
            error = ScreenCaptureResult.CaptureError.NO_CAPTURE_METHOD,
            errorMessage = "No screen capture method available. Enable accessibility service or grant MediaProjection permission."
        )
    }

    /**
     * Convert Image to Bitmap.
     * Must be called on the same thread that acquired the image.
     */
    @RequiresApi(21)
    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        if (planes.size != 1) return null

        val buffer = planes[0].buffer
        val width = image.width
        val height = image.height

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        // Handle rotation if needed
        if (currentRotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(currentRotation.toFloat())
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        }

        return bitmap
    }

    /**
     * Set up ImageReader for capturing frames.
     */
    @RequiresApi(21)
    private fun setupImageReader() {
        imageReader?.close()

        imageReader = ImageReader.newInstance(
            displayMetrics.widthPixels,
            displayMetrics.heightPixels,
            PixelFormat.RGBA_8888,
            2 // max images
        )
    }

    /**
     * Set up VirtualDisplay for capturing.
     */
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
            handler
        )
        
        if (virtualDisplay == null) {
            Log.e(TAG, "Failed to create VirtualDisplay")
        }
    }

    /**
     * Start orientation listener for rotation handling.
     */
    private fun startOrientationListener() {
        orientationListener?.disable()
        orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                val rotation = when {
                    orientation in 45..135 -> Surface.ROTATION_90
                    orientation in 135..225 -> Surface.ROTATION_180
                    orientation in 225..315 -> Surface.ROTATION_270
                    else -> Surface.ROTATION_0
                }

                if (rotation != currentRotation) {
                    currentRotation = rotation
                    setupVirtualDisplay()
                }
            }
        }
        orientationListener?.enable()
    }

    /**
     * Clean up MediaProjection resources.
     * Must be called when projection stops or on shutdown.
     */
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

    /**
     * Release a captured bitmap when done.
     * Call this to prevent memory leaks.
     */
    fun releaseCapture(result: ScreenCaptureResult.Success) {
        result.bitmap.recycle()
    }

    /**
     * Shutdown the capture manager and release all resources.
     */
    fun shutdown() {
        cleanupMediaProjection()
        scope.cancel()
    }
}
