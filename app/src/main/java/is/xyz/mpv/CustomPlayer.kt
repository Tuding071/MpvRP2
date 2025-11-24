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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MPV", "CustomPlayer onCreate")
        
        // Initialize mpv (this should match the original app's flow)
        MPVLib.create(this)
        
        // Set basic options like the original app would
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("hwdec", "auto")
        
        MPVLib.init()
        
        // Create surface view
        mpvView = MPVSurfaceView(this)
        setContentView(mpvView)
        
        // Load video
        intent?.data?.let { uri ->
            Log.d("MPV", "Loading video: $uri")
            MPVLib.command(arrayOf("loadfile", uri.toString()))
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
        // Ensure we have a transparent background
        setZOrderOnTop(false)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("MPV", "Surface created")
        MPVLib.attachSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("MPV", "Surface changed: $width x $height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("MPV", "Surface destroyed")
        MPVLib.detachSurface()
    }
}
