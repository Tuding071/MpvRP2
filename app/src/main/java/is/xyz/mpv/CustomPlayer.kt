package `is`.xyz.mpv

import android.app.Activity
import android.os.Bundle
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
        
        // Initialize mpv
        MPVLib.create(this)
        MPVLib.init()
        
        // Create and set the surface view
        mpvView = MPVSurfaceView(this)
        setContentView(mpvView)
        
        // Load video from intent
        intent?.data?.let { uri ->
            MPVLib.command(arrayOf("loadfile", uri.toString()))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MPVLib.destroy()
    }
}

// Simple SurfaceView for mpv
class MPVSurfaceView(context: android.content.Context) : SurfaceView(context), SurfaceHolder.Callback {
    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        MPVLib.attachSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Handle surface changes
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        MPVLib.detachSurface()
    }
}
