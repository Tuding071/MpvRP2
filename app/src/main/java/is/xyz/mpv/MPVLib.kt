package is.xyz.mpv
import android.content.Context
import android.view.Surface

object MPVLib {
    // Exact JNI functions from main.cpp
    @JvmStatic
    external fun create(appctx: Context)
    
    @JvmStatic
    external fun init()
    
    @JvmStatic
    external fun destroy()
    
    @JvmStatic
    external fun command(jarray: Array<String>)
    
    // These are probably in render.cpp based on typical mpv Android structure
    @JvmStatic
    external fun attachSurface(surface: Surface)
    
    @JvmStatic
    external fun detachSurface()
    
    @JvmStatic
    external fun setOptionString(name: String, value: String)
    
    // Helper functions
    fun loadFile(path: String) {
        command(arrayOf("loadfile", path))
    }
    
    fun playPause() {
        command(arrayOf("cycle", "pause"))
    }
    
    fun stop() {
        command(arrayOf("stop"))
    }
}
