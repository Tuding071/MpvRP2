package `is`.xyz.mpv

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.content.pm.ActivityInfo
import android.content.res.Configuration

class CustomPlayer : Activity() {
    companion object {
        init {
            System.loadLibrary("mpv")
        }
        
        private const val TAG = "CustomPlayer"
    }

    private lateinit var mpvView: MPVSurfaceView
    private var isMpvInitialized = false
    private var wasPlaying = false
    private var currentPosition = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "CustomPlayer onCreate")
        
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
            } else {
                // Restore state if we have saved instance
                wasPlaying = savedInstanceState.getBoolean("wasPlaying", false)
                currentPosition = savedInstanceState.getDouble("currentPosition", 0.0)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize mpv", e)
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (isMpvInitialized) {
            // Save playback state
            outState.putBoolean("wasPlaying", !MPVLib.getPropertyBoolean("pause") ?: false)
            outState.putDouble("currentPosition", MPVLib.getPropertyDouble("time-pos") ?: 0.0)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause - pausing playback")
        if (isMpvInitialized) {
            // Save current state
            wasPlaying = !(MPVLib.getPropertyBoolean("pause") ?: true)
            currentPosition = MPVLib.getPropertyDouble("time-pos") ?: 0.0
            
            // Pause playback but DON'T destroy
            MPVLib.setPropertyBoolean("pause", true)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - wasPlaying: $wasPlaying, position: $currentPosition")
        // Note: We DON'T automatically resume playback here
        // The video stays paused until user manually plays
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
        
        // mpv should handle aspect ratio automatically, but we can force recalculation
        if (isMpvInitialized) {
            MPVLib.command(arrayOf("video-reload"))
        }
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
