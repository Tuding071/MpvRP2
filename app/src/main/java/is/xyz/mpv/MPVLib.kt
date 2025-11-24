package is.xyz.mpv

import android.content.Context
import android.view.Surface

object MPVLib {
    external fun create(ctx: Context)
    external fun attachSurface(surface: Surface)
    external fun detachSurface()
    external fun command(command: Array<String>)
    external fun destroy()
}
