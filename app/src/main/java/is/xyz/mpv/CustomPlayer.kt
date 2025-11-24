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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MPV", "CustomPlayer onCreate")
        
        // Initialize mpv
        MPVLib.create(this)
        
        // Set video output options - CRITICAL for video display
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("hwdec", "no")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("profile", "fast")
        
        MPVLib.init()
        
        // Create surface view
        mpvView = MPVSurfaceView(this)
        setContentView(mpvView)
        
        // Load video from intent - FIX: Load on surface ready, not here
        intent?.data?.let { uri ->
            currentVideoPath = uri.toString()
            Log.d("MPV", "Video URI received: $currentVideoPath")
            // Don't load here - wait for surface
        }
    }

    private fun loadCurrentVideo() {
        currentVideoPath?.let { path ->
            Log.d("MPV", "Loading video: $path")
            MPVLib.command(arrayOf("loadfile", path))
            // Force refresh surface
            MPVLib.setPropertyBoolean("pause", false)
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        Log.d("MPV", "onNewIntent - new video selected")
        
        // Handle new video selection - FIX: This allows playing ANY video
        intent?.data?.let { uri ->
            currentVideoPath = uri.toString()
            Log.d("MPV", "New video selected: $currentVideoPath")
            
            // Stop current video and load new one
            MPVLib.command(arrayOf("stop"))
            loadCurrentVideo()
        }
    }

    override fun onPause() {
        super.onPause()
        MPVLib.setPropertyBoolean("pause", true)
    }

    override fun onResume() {
        super.onResume()
        MPVLib.setPropertyBoolean("pause", false)
    }

    override fun onDestroy() {
        super.onDestroy()
        MPVLib.destroy()
    }
}

class MPVSurfaceView(context: android.content.Context) : SurfaceView(context), SurfaceHolder.Callback {
    init {
        holder.addCallback(this)
        setZOrderOnTop(false)
        // Ensure surface type is correct for video
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("MPV", "Surface created - attaching to mpv")
        MPVLib.attachSurface(holder.surface)
        
        // FIX: Load video ONLY after surface is ready
        (context as? CustomPlayer)?.loadCurrentVideo()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("MPV", "Surface changed: $width x $height")
        // Force video refresh on surface change
        MPVLib.setPropertyBoolean("video-reload", true)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("MPV", "Surface destroyed")
        MPVLib.detachSurface()
    }
}
