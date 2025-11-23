package `is`.xyz.mpv

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel

class ComposeMPVView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private lateinit var baseMpvView: BaseMPVView

    fun initialize(configDir: String, cacheDir: String) {
        baseMpvView.initialize(configDir, cacheDir)
    }

    fun destroy() {
        baseMpvView.destroy()
    }

    fun playFile(filePath: String) {
        baseMpvView.playFile(filePath)
    }

    @Composable
    override fun Content() {
        val viewModel: PlayerViewModel = viewModel()
        
        AndroidView(
            factory = { ctx ->
                baseMpvView = object : BaseMPVView(ctx, attrs) {
                    override fun initOptions() {
                        // Initialize any MPV options here
                    }

                    override fun postInitOptions() {
                        // Post-init options
                    }

                    override fun observeProperties() {
                        // Observe MPV properties if needed
                    }
                }
                baseMpvView
            },
            modifier = Modifier.fillMaxSize()
        )
        
        PlayerOverlay(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )
    }
}
