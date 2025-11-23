package `is`.xyz.mpv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

class PlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            ComposeMPVView(
                modifier = Modifier.fillMaxSize(),
                context = this
            ).also { view ->
                // Initialize MPV
                val configDir = filesDir.absolutePath
                val cacheDir = cacheDir.absolutePath
                view.initialize(configDir, cacheDir)
                
                // Play the file from intent
                val filePath = intent.dataString ?: ""
                if (filePath.isNotEmpty()) {
                    view.playFile(filePath)
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Note: We'd need to handle destroy properly here
    }
}
