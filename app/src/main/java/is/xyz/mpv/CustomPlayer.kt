package `is`.xyz.mpv

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

class CustomPlayer : Activity() {
    companion object {
        init {
            System.loadLibrary("mpv")
        }
    }

    private lateinit var mpvView: MPVSurfaceView
    private var currentVideoPath: String? = null
    private var isMpvInitialized = false
    private var activityIsForeground = false
    
    // Restoration state
    private var shouldRestorePlayback = false
    private var savedPlaybackPosition = 0.0
    private var wasPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MPV", "CustomPlayer onCreate")
        
        // Restore state from configuration changes
        if (savedInstanceState != null) {
            currentVideoPath = savedInstanceState.getString("video_path")
            savedPlaybackPosition = savedInstanceState.getDouble("playback_position", 0.0)
            wasPaused = savedInstanceState.getBoolean("was_paused", false)
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
        
        // Get video from intent if fresh start
        if (currentVideoPath == null) {
            intent?.data?.let { uri ->
                currentVideoPath = uri.toString()
                Log.d("MPV", "Fresh video URI received: $currentVideoPath")
            }
        }
    }

    private fun initializeMpv() {
        Log.d("MPV", "Initializing MPV library")
        MPVLib.create(this)
        
        // Set video output options - FIX ASPECT RATIO
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("hwdec", "auto")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("keepaspect", "yes") // Maintain aspect ratio
        MPVLib.setOptionString("video-aspect", "-1") // Use video's native aspect ratio
        
        MPVLib.init()
    }

    fun loadCurrentVideo(restorePosition: Boolean = false) {
        currentVideoPath?.let { path ->
            Log.d("MPV", "Loading video: $path, restorePosition: $restorePosition")
            
            if (restorePosition && savedPlaybackPosition > 0) {
                // Load and seek to position
                MPVLib.command(arrayOf("loadfile", path, "replace"))
                mpvView.postDelayed({
                    Log.d("MPV", "Restoring playback position: $savedPlaybackPosition")
                    MPVLib.setPropertyDouble("time-pos", savedPlaybackPosition)
                    if (wasPaused) {
                        MPVLib.setPropertyBoolean("pause", true)
                    }
                }, 200)
            } else {
                // Fresh load
                MPVLib.command(arrayOf("loadfile", path))
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
                
                outState.putString("video_path", currentVideoPath)
                outState.putDouble("playback_position", position)
                outState.putBoolean("was_paused", paused)
                
                Log.d("MPV", "Saved state - position: $position, paused: $paused")
            } catch (e: Exception) {
                Log.e("MPV", "Error saving state", e)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        Log.d("MPV", "onNewIntent - new video selected")
        
        intent?.data?.let { uri ->
            currentVideoPath = uri.toString()
            Log.d("MPV", "New video selected: $currentVideoPath")
            
            // Reset restoration flags
            shouldRestorePlayback = false
            savedPlaybackPosition = 0.0
            wasPaused = false
            
            MPVLib.command(arrayOf("stop"))
            if (mpvView.isSurfaceAvailable) {
                loadCurrentVideo()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MPV", "onResume")
        activityIsForeground = true
    }

    override fun onPause() {
        super.onPause()
        Log.d("MPV", "onPause")
        activityIsForeground = false
        
        // Save state when going to background
        if (isMpvInitialized) {
            try {
                savedPlaybackPosition = MPVLib.getPropertyDouble("time-pos") ?: 0.0
                wasPaused = MPVLib.getPropertyBoolean("pause") ?: false
                shouldRestorePlayback = true
                Log.d("MPV", "Pause state saved - position: $savedPlaybackPosition, paused: $wasPaused")
            } catch (e: Exception) {
                Log.e("MPV", "Error saving pause state", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MPV", "onDestroy - isFinishing: ${isFinishing}")
        
        // Only destroy when really finishing
        if (isFinishing && isMpvInitialized) {
            MPVLib.destroy()
            isMpvInitialized = false
        }
    }
}

class MPVSurfaceView(context: android.content.Context) : SurfaceView(context), SurfaceHolder.Callback {
    var isSurfaceAvailable = false
        private set
    
    init {
        holder.addCallback(this)
        setZOrderOnTop(false)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("MPV", "Surface created - attaching to mpv")
        isSurfaceAvailable = true
        MPVLib.attachSurface(holder.surface)
        
        val player = context as CustomPlayer
        player.loadCurrentVideo(player.shouldRestorePlayback)
        player.shouldRestorePlayback = false
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("MPV", "Surface changed: $width x $height")
        // Force video to re-render with correct aspect ratio
        MPVLib.setPropertyBoolean("video-reload", true)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("MPV", "Surface destroyed")
        isSurfaceAvailable = false
        MPVLib.detachSurface()
    }
}
