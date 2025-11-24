package `is`.xyz.mpv

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.preference.PreferenceManager.getDefaultSharedPreferences
import android.content.pm.PackageManager
import android.os.Build

class CustomPlayer : AppCompatActivity() {
    companion object {
        init {
            System.loadLibrary("mpv")
        }
        
        private const val TAG = "CustomPlayer"
        private const val REQUEST_STORAGE_PERMISSION = 100
        private const val REQUEST_FILE_PICKER = 101
    }

    private lateinit var mpvView: MPVSurfaceView
    private var isMpvInitialized = false
    private var activityIsForeground = true
    private var didResumeBackgroundPlayback = false

    // Settings
    private var backgroundPlayMode = "never"
    private var autoRotationMode = "auto"
    private var shouldSavePosition = false

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openFilePicker()
        } else {
            Toast.makeText(this, "Storage permission is required to play videos", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // File picker launcher
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                playVideo(uri)
            }
        } else {
            // No file selected, finish activity
            if (!isMpvInitialized) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "CustomPlayer onCreate")
        
        setupWindowFlags()
        setupSystemUI()
        
        readSettings()
        
        // Check if we have a video URI from intent
        val videoUri = intent?.data
        if (videoUri != null) {
            // Play video from intent
            initializeMPV()
            playVideo(videoUri)
        } else {
            // Check permissions and open file picker
            checkPermissionsAndOpenPicker()
        }
    }

    private fun setupWindowFlags() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupSystemUI() {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.hide(WindowInsetsCompat.Type.navigationBars())
    }

    private fun checkPermissionsAndOpenPicker() {
        if (hasStoragePermission()) {
            openFilePicker()
        } else {
            requestStoragePermission()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                showPermissionRationale()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Storage Permission Needed")
            .setMessage("This app needs storage permission to play video files from your device.")
            .setPositiveButton("Grant") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            .setNegativeButton("Deny") { _, _ ->
                finish()
            }
            .show()
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        
        // Also include GET_CONTENT for broader compatibility
        val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        
        val chooserIntent = Intent.createChooser(intent, "Select Video File").apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(pickIntent))
        }
        
        filePickerLauncher.launch(chooserIntent)
    }

    private fun initializeMPV() {
        if (isMpvInitialized) return
        
        try {
            // Copy assets (important for mpv initialization)
            Utils.copyAssets(this)
            
            // Initialize mpv
            MPVLib.create(this)
            isMpvInitialized = true

            // Set basic options
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("hwdec", "auto")
            MPVLib.setOptionString("input-default-bindings", "yes")
            MPVLib.setOptionString("input-vo-keyboard", "yes")
            
            // IMPORTANT: Save and restore playback position
            MPVLib.setOptionString("save-position-on-quit", "yes")
            MPVLib.setOptionString("watch-later-directory", cacheDir.absolutePath)
            
            // Additional options for better behavior
            MPVLib.setOptionString("keep-open", "yes")
            MPVLib.setOptionString("video-aspect-override", "no")
            MPVLib.setOptionString("correct-pts", "yes")
            
            // FIX ASPECT RATIO STRETCHING - CRITICAL SETTINGS
            MPVLib.setOptionString("video-aspect", "-1") // Let video decide aspect ratio
            MPVLib.setOptionString("video-unscaled", "no")
            MPVLib.setOptionString("panscan", "0.0")
            MPVLib.setOptionString("keepaspect", "yes")
            MPVLib.setOptionString("keepaspect-window", "yes")
            
            // Initialize mpv
            MPVLib.init()

            // Create surface view
            mpvView = MPVSurfaceView(this)
            setContentView(mpvView)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize mpv", e)
            Toast.makeText(this, "Failed to initialize video player", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun playVideo(uri: Uri) {
        if (!isMpvInitialized) {
            initializeMPV()
        }
        
        val filepath = resolveUri(uri)
        if (filepath != null) {
            Log.d(TAG, "Loading video: $filepath")
            MPVLib.command(arrayOf("loadfile", filepath))
        } else {
            Toast.makeText(this, "Failed to resolve video URI", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun resolveUri(data: Uri): String? {
        return when (data.scheme) {
            "file" -> data.path
            "content" -> {
                // For content URIs, we need to handle them properly
                try {
                    contentResolver.openFileDescriptor(data, "r")?.use { pfd ->
                        val path = Utils.findRealPath(pfd.fd)
                        if (path != null) {
                            Log.v(TAG, "Found real file path: $path")
                            return path
                        }
                    }
                    // Fallback: let mpv handle the content URI directly
                    data.toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open content URI: $e")
                    data.toString()
                }
            }
            "http", "https", "rtmp", "rtsp" -> data.toString()
            else -> {
                Log.e(TAG, "Unknown scheme: ${data.scheme}")
                null
            }
        }
    }

    private fun readSettings() {
        val prefs = getDefaultSharedPreferences(applicationContext)
        val getString: (String, String) -> String = { key, default ->
            prefs.getString(key, default) ?: default
        }

        this.backgroundPlayMode = getString("background_play", "never")
        this.autoRotationMode = getString("auto_rotation", "auto")
        this.shouldSavePosition = prefs.getBoolean("save_position", false)
    }

    private fun shouldBackground(): Boolean {
        if (isFinishing) return false
        return when (backgroundPlayMode) {
            "always" -> true
            "audio-only" -> isPlayingAudioOnly()
            else -> false
        }
    }

    private fun isPlayingAudioOnly(): Boolean {
        val haveAudio = MPVLib.getPropertyBoolean("current-tracks/audio/selected") ?: false
        val image = MPVLib.getPropertyString("current-tracks/video/image")
        return haveAudio && (image.isNullOrEmpty() || image == "yes")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause - pausing playback")
        
        activityIsForeground = false
        
        if (isMpvInitialized) {
            val shouldBackground = shouldBackground()
            
            if (!shouldBackground) {
                // Pause playback when leaving app
                MPVLib.setPropertyBoolean("pause", true)
            }
            
            didResumeBackgroundPlayback = shouldBackground
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        
        activityIsForeground = true
        
        // Note: We DON'T automatically resume playback here
        // The video stays paused until user manually plays
        setupWindowFlags()
        setupSystemUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        
        if (isMpvInitialized) {
            // Save position before destroying
            if (shouldSavePosition) {
                MPVLib.command(arrayOf("write-watch-later-config"))
            }
            
            MPVLib.destroy()
            isMpvInitialized = false
        }
        
        // Restore system UI
        restoreSystemUI()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
        
        if (isMpvInitialized && shouldSavePosition) {
            MPVLib.command(arrayOf("write-watch-later-config"))
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Orientation changed: ${newConfig.orientation}")
        
        updateOrientation(newConfig)
        
        // Force aspect ratio recalculation
        if (isMpvInitialized) {
            MPVLib.command(arrayOf("video-reload"))
        }
    }

    private fun updateOrientation(newConfig: Configuration? = null) {
        val config = newConfig ?: resources.configuration
        
        if (autoRotationMode != "auto") {
            requestedOrientation = when (autoRotationMode) {
                "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            return
        }
        
        // Auto orientation based on video aspect ratio
        if (isMpvInitialized) {
            val ratio = MPVLib.getPropertyDouble("video-params/aspect") ?: 0.0
            if (ratio > 0.0) {
                requestedOrientation = if (ratio > 1.0)
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }
    }

    private fun restoreSystemUI() {
        try {
            window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            insetsController.show(WindowInsetsCompat.Type.navigationBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT

            WindowCompat.setDecorFitsSystemWindows(window, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring system UI", e)
        }
    }

    override fun onBackPressed() {
        // Save position and exit
        if (isMpvInitialized && shouldSavePosition) {
            MPVLib.command(arrayOf("write-watch-later-config"))
        }
        super.onBackPressed()
    }
}

class MPVSurfaceView(context: android.content.Context) : 
    SurfaceView(context), SurfaceHolder.Callback {
    
    companion object {
        private const val TAG = "MPVSurfaceView"
    }
    
    private var surfaceAttached = false

    init {
        holder.addCallback(this)
        setZOrderOnTop(false)
        keepScreenOn = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "Surface created")
        if (!surfaceAttached) {
            MPVLib.attachSurface(holder.surface)
            surfaceAttached = true
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "Surface changed: $width x $height")
        
        // CRITICAL: Set correct aspect ratio handling to prevent stretching
        if (width > 0 && height > 0) {
            // These properties ensure the video maintains its aspect ratio
            MPVLib.setPropertyString("video-aspect", "-1") // No override
            MPVLib.setPropertyString("keepaspect", "yes")
            MPVLib.setPropertyString("keepaspect-window", "yes")
            MPVLib.setPropertyDouble("panscan", 0.0)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "Surface destroyed")
        if (surfaceAttached) {
            MPVLib.detachSurface()
            surfaceAttached = false
        }
    }
}
