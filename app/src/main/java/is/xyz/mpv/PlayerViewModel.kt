package `is`.xyz.mpv

import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(context: Context) : ViewModel() {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    
    private val _currentVolume = MutableStateFlow(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    val currentVolume: StateFlow<Int> = _currentVolume
    
    fun setVolume(volume: Int) {
        val newVolume = volume.coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        _currentVolume.value = newVolume
    }
    
    fun adjustVolume(direction: Int) {
        val current = _currentVolume.value
        val newVolume = (current + direction).coerceIn(0, maxVolume)
        setVolume(newVolume)
    }
}
