package is.xyz.mpv

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
        
        // Initialize mpv in two steps as per main.cpp
        MPVLib.create(this)  // First create
        MPVLib.init()        // Then initialize
        
        // Create and set the surface view
        mpvView = MPVSurfaceView(this)
        setContentView(mpvView)
        
        // Load video from intent
        intent?.data?.let { uri ->
            MPVLib.loadFile(uri.toString())
        } ?: run {
            // You can load a default file here for testing
            // MPVLib.loadFile("/sdcard/test.mp4")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MPVLib.destroy()
    }
}

// SurfaceView that handles mpv rendering
class MPVSurfaceView(context: android.content.Context) : SurfaceView(context), SurfaceHolder.Callback {
    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        MPVLib.attachSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Surface changed
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        MPVLib.detachSurface()
    }
}
