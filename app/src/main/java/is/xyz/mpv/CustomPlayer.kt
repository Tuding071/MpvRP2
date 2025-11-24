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
        
        // State constants
        private const val STATE_VIDEO_PATH = "video_path"
        private const val STATE_PLAYBACK_POSITION = "playback_position"
        private const val STATE_IS_PAUSED = "is_paused"
    }

    private lateinit var mpvView: MPVSurfaceView
    private var currentVideoPath: String? = null
    private var isMpvInitialized = false
    
    // Restoration flags
    var shouldRestorePlayback = false
    var savedPlaybackPosition = 0.0
    var wasPaused = false
    private var isFreshStart = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MPV", "CustomPlayer onCreate - isMpvInitialized: $isMpvInitialized")
        
        // Restore state if available (from configuration changes)
        if (savedInstanceState != null) {
            currentVideoPath = savedInstanceState.getString(STATE_VIDEO_PATH)
            savedPlaybackPosition = savedInstanceState.getDouble(STATE_PLAYBACK_POSITION, 0.0)
            wasPaused = savedInstanceState.getBoolean(STATE_IS_PAUSED, false)
            shouldRestorePlayback = savedPlaybackPosition > 0
            Log.d("MPV", "Restoring from savedInstanceState - position: $savedPlaybackPosition, paused: $wasPaused")
        } else {
            // Fresh start - get video from intent
            intent?.data?.let { uri ->
                currentVideoPath = uri.toString()
                Log.d("MPV", "Fresh video URI received: $currentVideoPath")
                isFreshStart = true
            }
        }

        // Initialize mpv only once per app lifetime
        if (!isMpvInitialized) {
            initializeMpv()
            isMpvInitialized = true
        }
        
        // Create surface view
        mpvView = MPVSurfaceView(this)
        setContentView(mpvView)
    }

    private fun initializeMpv() {
        Log.d("MPV", "Initializing MPV library")
        MPVLib.create(this)
        
        // Set video output options
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("hwdec", "auto") // Let mpv decide best hardware decoding
        MPVLib.setOptionString("gpu-context", "android")
        
        // FIX STRETCH: Set proper video scaling and aspect ratio
        MPVLib.setOptionString("keepaspect", "yes") // Maintain aspect ratio
        MPVLib.setOptionString("video-aspect", "-1") // Use video's native aspect ratio
        MPVLib.setOptionString("video-zoom", "0.0") // No zoom
        MPVLib.setOptionString("panscan", "0.0") // No pan-and-scan
        
        // Better performance options
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("untimed", "no")
        MPVLib.setOptionString("video-sync", "display-resample")
        MPVLib.setOptionString("interpolation", "no")
        
        MPVLib.init()
    }

    fun loadCurrentVideo(restorePosition: Boolean = false) {
        currentVideoPath?.let { path ->
            Log.d("MPV", "Loading video: $path, restorePosition: $restorePosition, isFreshStart: $isFreshStart")
            
            if (isFreshStart) {
                // First time loading this video
                MPVLib.command(arrayOf("loadfile", path))
                isFreshStart = false
            } else if (restorePosition) {
                // Restoring existing video with position
                MPVLib.command(arrayOf("loadfile", path, "replace"))
                
                if (savedPlaybackPosition > 0) {
                    // Small delay to ensure video is loaded before seeking
                    mpvView.postDelayed({
                        Log.d("MPV", "Restoring playback position: $savedPlaybackPosition")
                        MPVLib.setPropertyDouble("time-pos", savedPlaybackPosition)
                    }, 200)
                }
                
                // Restore pause state
                if (wasPaused) {
                    mpvView.postDelayed({
                        MPVLib.setPropertyBoolean("pause", true)
                        Log.d("MPV", "Restored pause state")
                    }, 250)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d("MPV", "Saving instance state for configuration change")
        
        // Save current playback state for orientation changes
        if (isMpvInitialized && currentVideoPath != null) {
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
        
        // Handle new video selection from external apps
        intent?.data?.let { uri ->
            val newPath = uri.toString()
            if (newPath != currentVideoPath) {
                currentVideoPath = newPath
                Log.d("MPV", "New video selected: $currentVideoPath")
                
                // Reset restoration flags for new video
                shouldRestorePlayback = false
                savedPlaybackPosition = 0.0
                wasPaused = false
                isFreshStart = true
                
                // Stop current video and load new one
                MPVLib.command(arrayOf("stop"))
                
                // Load new video if surface is ready
                if (mpvView.isSurfaceAvailable()) {
                    loadCurrentVideo()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("MPV", "onPause - saving playback state")
        
        // Save state when going to background
        if (isMpvInitialized) {
            try {
                savedPlaybackPosition = MPVLib.getPropertyDouble("time-pos") ?: 0.0
                wasPaused = MPVLib.getPropertyBoolean("pause") ?: false
                shouldRestorePlayback = true
                
                Log.d("MPV", "Pause state saved - position: $savedPlaybackPosition, paused: $wasPaused")
                
                // Only pause if not already paused
                if (!wasPaused) {
                    MPVLib.setPropertyBoolean("pause", true)
                }
            } catch (e: Exception) {
                Log.e("MPV", "Error saving pause state", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MPV", "onResume")
        
        if (isMpvInitialized) {
            // Don't automatically unpause - let the surface handling manage this
            // This prevents unwanted playback when returning to app
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("MPV", "onStop")
        // Additional safety save
        if (isMpvInitialized) {
            try {
                val position = MPVLib.getPropertyDouble("time-pos") ?: 0.0
                val paused = MPVLib.getPropertyBoolean("pause") ?: false
                if (position > 0) {
                    savedPlaybackPosition = position
                    wasPaused = paused
                    shouldRestorePlayback = true
                    Log.d("MPV", "Stop state saved - position: $savedPlaybackPosition, paused: $wasPaused")
                }
            } catch (e: Exception) {
                Log.e("MPV", "Error saving stop state", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MPV", "onDestroy - isFinishing: ${isFinishing}")
        
        // Only destroy mpv when activity is really finishing (not orientation change)
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
        
        // Reset restoration flag after attempting restore
        if (player.shouldRestorePlayback) {
            player.shouldRestorePlayback = false
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("MPV", "Surface changed: $width x $height")
        // Force video to re-render with correct aspect ratio
        MPVLib.setPropertyBoolean("video-reload", true)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("MPV", "Surface destroyed")
        surfaceAvailable = false
        MPVLib.detachSurface()
    }
    
    fun isSurfaceAvailable(): Boolean = surfaceAvailable
}
