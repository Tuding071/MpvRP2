package `is`.xyz.mpv

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.preference.PreferenceManager.getDefaultSharedPreferences

class CustomPlayer : Activity() {
    companion object {
        init {
            System.loadLibrary("mpv")
        }
        
        private const val TAG = "CustomPlayer"
    }

    private lateinit var mpvView: MPVSurfaceView
    private var isMpvInitialized = false
    private var activityIsForeground = true
    private var didResumeBackgroundPlayback = false

    // Settings
    private var backgroundPlayMode = "never" // "never", "audio-only", "always"
    private var autoRotationMode = "auto" // "auto", "landscape", "portrait", "manual"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "CustomPlayer onCreate")
        
        readSettings()
        
        try {
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
            MPVLib.setOptionString("keep-open", "yes") // Keep player open when file ends
            MPVLib.setOptionString("video-aspect-override", "no") // Don't override aspect ratio
            MPVLib.setOptionString("correct-pts", "yes") // Better timestamp handling
            
            // Initialize mpv
            MPVLib.init()

            // Create surface view
            mpvView = MPVSurfaceView(this)
            setContentView(mpvView)

            // Load video from intent - only if fresh start
            if (savedInstanceState == null) {
                intent?.data?.let { uri ->
                    Log.d(TAG, "Loading video: $uri")
                    MPVLib.command(arrayOf("loadfile", uri.toString()))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize mpv", e)
            finish()
        }
    }

    private fun readSettings() {
        val prefs = getDefaultSharedPreferences(applicationContext)
        val getString: (String, String) -> String = { key, default ->
            prefs.getString(key, default) ?: default
        }

        this.backgroundPlayMode = getString("background_play", "never")
        this.autoRotationMode = getString("auto_rotation", "auto")
    }

    private fun shouldBackground(): Boolean {
        if (isFinishing) // about to exit?
            return false
        return when (backgroundPlayMode) {
            "always" -> true
            "audio-only" -> isPlayingAudioOnly()
            else -> false // "never"
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
        
        val shouldBackground = shouldBackground()
        
        if (!shouldBackground) {
            // Pause playback when leaving app
            MPVLib.setPropertyBoolean("pause", true)
        }
        
        didResumeBackgroundPlayback = shouldBackground
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        
        activityIsForeground = true
        
        // Note: We DON'T automatically resume playback here
        // The video stays paused until user manually plays
        // This matches what you wanted - stay paused when returning
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isMpvInitialized) {
            MPVLib.destroy()
            isMpvInitialized = false
        }
    }

    // Handle configuration changes (orientation) ourselves
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Orientation changed: ${newConfig.orientation}")
        
        updateOrientation(newConfig)
        
        // mpv should handle aspect ratio automatically, but we can force recalculation
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
        val ratio = MPVLib.getPropertyDouble("video-params/aspect") ?: 0.0
        if (ratio == 0.0) return
        
        requestedOrientation = if (ratio > 1.0)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
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
        
        // Set correct aspect ratio to prevent stretching
        if (width > 0 && height > 0) {
            MPVLib.setPropertyString("video-aspect", "-1") // -1 means no aspect ratio override
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
