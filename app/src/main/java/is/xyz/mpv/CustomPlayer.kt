package is.xyz.mpv

import android.content.Context
import android.os.Bundle
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

class CustomPlayer : ComponentActivity() {
    companion object {
        init {
            System.loadLibrary("mpv")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize mpv
        MPVLib.create(this)
        
        setContent {
            VideoSurface()
        }
        
        // Load video from intent
        intent?.data?.let { uri ->
            loadFile(uri.toString())
        }
    }
    
    @Composable
    private fun VideoSurface() {
        AndroidView(
            factory = { context ->
                MPVSurfaceView(context).apply {
                    // Surface view will handle video rendering
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
    
    private fun loadFile(path: String) {
        MPVLib.command(arrayOf("loadfile", path))
    }

    override fun onDestroy() {
        super.onDestroy()
        MPVLib.destroy()
    }
}

// Simple SurfaceView for mpv
class MPVSurfaceView(context: Context) : SurfaceView(context) {
    init {
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                MPVLib.attachSurface(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                // Handle surface changes if needed
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                MPVLib.detachSurface()
            }
        })
    }
}
