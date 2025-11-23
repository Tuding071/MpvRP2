package `is`.xyz.mpv

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.reflect.KProperty

class MPVView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseMPVView(context, attrs) {
    
    override fun initOptions() {
        // Basic mpv configuration
        MPVLib.setOptionString("hwdec", "auto")
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        MPVLib.setOptionString("input-default-bindings", "yes")
        
        // Performance optimizations
        MPVLib.setOptionString("vd-lavc-fast", "yes")
        MPVLib.setOptionString("vd-lavc-skiploopfilter", "nonkey")
        MPVLib.setOptionString("demuxer-max-bytes", "${64 * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${64 * 1024 * 1024}")
    }

    override fun postInitOptions() {
        // Post-init configuration
        MPVLib.setOptionString("save-position-on-quit", "no")
    }

    override fun observeProperties() {
        // Observe essential properties for the overlay
        MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("speed", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("media-title", MPVLib.MpvFormat.MPV_FORMAT_STRING)
        MPVLib.observeProperty("path", MPVLib.MpvFormat.MPV_FORMAT_STRING)
    }

    // Property getters/setters for external access
    var paused: Boolean?
        get() = MPVLib.getPropertyBoolean("pause")
        set(paused) = MPVLib.setPropertyBoolean("pause", paused!!)

    var timePos: Double?
        get() = MPVLib.getPropertyDouble("time-pos")
        set(progress) = MPVLib.setPropertyDouble("time-pos", progress!!)

    var playbackSpeed: Double?
        get() = MPVLib.getPropertyDouble("speed")
        set(speed) = MPVLib.setPropertyDouble("speed", speed!!)

    // Commands for external control
    fun cyclePause() = MPVLib.command(arrayOf("cycle", "pause"))
    fun cycleAudio() = MPVLib.command(arrayOf("cycle", "audio"))
    fun cycleSub() = MPVLib.command(arrayOf("cycle", "sub"))

    fun cycleSpeed() {
        val speeds = arrayOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
        val currentSpeed = playbackSpeed ?: 1.0
        val index = speeds.indexOfFirst { it > currentSpeed }
        playbackSpeed = speeds[if (index == -1) 0 else index]
    }

    // Track management (simplified)
    data class Track(val mpvId: Int, val name: String)
    
    fun loadTracks(): Map<String, List<Track>> {
        val tracks = mutableMapOf<String, MutableList<Track>>(
            "audio" to mutableListOf(),
            "video" to mutableListOf(),
            "sub" to mutableListOf()
        )
        
        // Add "off" option for audio/subs
        tracks["audio"]?.add(Track(-1, "Disable"))
        tracks["sub"]?.add(Track(-1, "Disable"))
        
        val count = MPVLib.getPropertyInt("track-list/count") ?: 0
        for (i in 0 until count) {
            val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
            if (!tracks.containsKey(type)) continue
            
            val mpvId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
            val lang = MPVLib.getPropertyString("track-list/$i/lang")
            val title = MPVLib.getPropertyString("track-list/$i/title")
            
            val trackName = when {
                !lang.isNullOrEmpty() && !title.isNullOrEmpty() -> "$title ($lang)"
                !lang.isNullOrEmpty() -> lang
                !title.isNullOrEmpty() -> title
                else -> "Track $mpvId"
            }
            
            tracks[type]?.add(Track(mpvId, trackName))
        }
        
        return tracks
    }

    // Playlist management
    data class PlaylistItem(val index: Int, val filename: String, val title: String?)
    
    fun loadPlaylist(): List<PlaylistItem> {
        val playlist = mutableListOf<PlaylistItem>()
        val count = MPVLib.getPropertyInt("playlist-count") ?: 0
        
        for (i in 0 until count) {
            val filename = MPVLib.getPropertyString("playlist/$i/filename") ?: continue
            val title = MPVLib.getPropertyString("playlist/$i/title")
            playlist.add(PlaylistItem(i, filename, title))
        }
        
        return playlist
    }

    // Track property delegates
    class TrackDelegate(private val name: String) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            val v = MPVLib.getPropertyString(name)
            return v?.toIntOrNull() ?: -1
        }
        
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            if (value == -1) {
                MPVLib.setPropertyString(name, "no")
            } else {
                MPVLib.setPropertyInt(name, value)
            }
        }
    }

    var vid: Int by TrackDelegate("vid")
    var sid: Int by TrackDelegate("sid")
    var aid: Int by TrackDelegate("aid")

    // Key and pointer event handling (if needed by your app)
    fun onKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_MULTIPLE) return false
        if (KeyEvent.isModifierKey(event.keyCode)) return false
        
        // Handle key events if needed
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> cyclePause()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> performQuickSeek(10)
            KeyEvent.KEYCODE_MEDIA_REWIND -> performQuickSeek(-10)
            else -> return false
        }
        
        return true
    }

    companion object {
        private const val TAG = "mpv"
    }
}
