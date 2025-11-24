package `is`.xyz.mpv

import android.app.Activity
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

class CustomPlayer : Activity() {
    companion object {
        init {
            System.loadLibrary("mpv")
        }
        
        // State constants
        private const val STATE_VIDEO_PATH = "video_path"
        private const val STATE_PLAYBACK_POSITION = "playback_position"
        private const val STATE_IS_PAUSED = "is_paused"
    }

    private lateinit var mpvView: MPVSurfaceView
    private var currentVideoPath: String? = null
    private var isMpvInitialized = false
    private var shouldRestorePlayback = false
    private var savedPlaybackPosition = 0.0
    private var wasPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MPV", "CustomPlayer onCreate - isMpvInitialized: $isMpvInitialized")
        
        // Restore state if available
        savedInstanceState?.let {
            currentVideoPath = it.getString(STATE_VIDEO_PATH)
            savedPlaybackPosition = it.getDouble(STATE_PLAYBACK_POSITION, 0.0)
            wasPaused = it.getBoolean(STATE_IS_PAUSED, false)
            shouldRestorePlayback = savedPlaybackPosition > 0
            Log.d("MPV", "Restoring state - position: $savedPlaybackPosition, paused: $wasPaused")
        }

        // Initialize mpv only once
        if (!isMpvInitialized) {
            initializeMpv()
            isMpvInitialized = true
        }
        
        // Create surface view
        mpvView = MPVSurfaceView(this)
        setContentView(mpvView)
        
        // Load video from intent (only if fresh start)
        if (currentVideoPath == null) {
            intent?.data?.let { uri ->
                currentVideoPath = uri.toString()
                Log.d("MPV", "Fresh video URI received: $currentVideoPath")
            }
        } else {
            Log.d("MPV", "Using existing video: $currentVideoPath")
        }
    }

    private fun initializeMpv() {
        Log.d("MPV", "Initializing MPV library")
        MPVLib.create(this)
        
        // Set video output options
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("hwdec", "no") 
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("profile", "fast")
        
        // Enable seeking and time position reporting
        MPVLib.setOptionString("input-ipc-server", "/tmp/mpvsocket")
        
        MPVLib.init()
    }

    fun loadCurrentVideo(restorePosition: Boolean = false) {
        currentVideoPath?.let { path ->
            Log.d("MPV", "Loading video: $path, restorePosition: $restorePosition")
            MPVLib.command(arrayOf("loadfile", path))
            
            if (restorePosition && savedPlaybackPosition > 0) {
                // Small delay to ensure video is loaded before seeking
                mpvView.postDelayed({
                    Log.d("MPV", "Restoring playback position: $savedPlaybackPosition")
                    MPVLib.setPropertyDouble("time-pos", savedPlaybackPosition)
                }, 100)
            }
            
            // Restore pause state
            if (wasPaused) {
                mpvView.postDelayed({
                    MPVLib.setPropertyBoolean("pause", true)
                }, 150)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d("MPV", "Saving instance state")
        
        // Save current playback state
        if (isMpvInitialized) {
            try {
                val position = MPVLib.getPropertyDouble("time-pos") ?: 0.0
                val paused = MPVLib.getPropertyBoolean("pause") ?: false
                
                outState.putString(STATE_VIDEO_PATH, currentVideoPath)
                outState.putDouble(STATE_PLAYBACK_POSITION, position)
                outState.putBoolean(STATE_IS_PAUSED, paused)
                
                Log.d("MPV", "Saved state - position: $position, paused: $paused")
            } catch (e: Exception) {
                Log.e("MPV", "Error saving state", e)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        Log.d("MPV", "onNewIntent - new video selected")
        
        // Handle new video selection
        intent?.data?.let { uri ->
            val newPath = uri.toString()
            if (newPath != currentVideoPath) {
                currentVideoPath = newPath
                Log.d("MPV", "New video selected: $currentVideoPath")
                
                // Reset restoration flags for new video
                shouldRestorePlayback = false
                savedPlaybackPosition = 0.0
                wasPaused = false
                
                // Stop current video and load new one
                MPVLib.command(arrayOf("stop"))
                loadCurrentVideo()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("MPV", "onPause")
        if (isMpvInitialized) {
            MPVLib.setPropertyBoolean("pause", true)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MPV", "onResume")
        if (isMpvInitialized) {
            MPVLib.setPropertyBoolean("pause", false)
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("MPV", "onStop")
        // Save playback state when going to background
        if (isMpvInitialized) {
            try {
                savedPlaybackPosition = MPVLib.getPropertyDouble("time-pos") ?: 0.0
                wasPaused = MPVLib.getPropertyBoolean("pause") ?: false
                Log.d("MPV", "Saved for background - position: $savedPlaybackPosition, paused: $wasPaused")
            } catch (e: Exception) {
                Log.e("MPV", "Error saving background state", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MPV", "onDestroy - isFinishing: ${isFinishing}")
        
        // Only destroy mpv when activity is really finishing
        if (isFinishing && isMpvInitialized) {
            Log.d("MPV", "Destroying MPV library")
            MPVLib.destroy()
            isMpvInitialized = false
        }
    }
}

class MPVSurfaceView(context: android.content.Context) : SurfaceView(context), SurfaceHolder.Callback {
    private var surfaceAvailable = false
    
    init {
        holder.addCallback(this)
        setZOrderOnTop(false)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("MPV", "Surface created - attaching to mpv")
        surfaceAvailable = true
        MPVLib.attachSurface(holder.surface)
        
        // Load video ONLY after surface is ready
        val player = context as CustomPlayer
        player.loadCurrentVideo(player.shouldRestorePlayback)
        player.shouldRestorePlayback = false // Reset after first restore attempt
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("MPV", "Surface changed: $width x $height")
        // Video should automatically adapt to new surface size
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("MPV", "Surface destroyed")
        surfaceAvailable = false
        MPVLib.detachSurface()
    }
}
