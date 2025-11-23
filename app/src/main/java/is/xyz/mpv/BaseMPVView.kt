package `is`.xyz.mpv

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

abstract class BaseMPVView(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs), SurfaceHolder.Callback {
    
    // Surface view for video output
    private val surfaceView: SurfaceView
    private val gestureDetector: GestureDetector
    
    // UI Components from your overlay
    private lateinit var seekBar: ProgressBar
    private lateinit var timeText: TextView
    private lateinit var totalTimeText: TextView
    private lateinit var feedbackText: TextView
    private lateinit var videoInfoText: TextView
    private lateinit var controlsContainer: LinearLayout
    
    // State variables from your overlay
    private var showSeekbar = true
    private var userInteracting = false
    private var hideSeekbarJob: Job? = null
    private var videoInfoJob: Job? = null
    private var feedbackJob: Job? = null
    private var quickSeekJob: Job? = null
    
    // Gesture tracking
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartTime = 0L
    private var isHorizontalSwipe = false
    private var isVerticalSwipe = false
    private var isLongTap = false
    private var longTapJob: Job? = null
    private var isSeeking = false
    private var seekStartX = 0f
    private var seekStartPosition = 0.0
    private var wasPlayingBeforeSeek = false
    private var seekDirection = ""
    
    // Thresholds
    private val longTapThreshold = 300L
    private val horizontalSwipeThreshold = 30f
    private val verticalSwipeThreshold = 40f
    private val maxVerticalMovement = 50f
    private val maxHorizontalMovement = 50f
    private val quickSeekAmount = 5
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    init {
        // Create surface view
        surfaceView = SurfaceView(context).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            holder.addCallback(this@BaseMPVView)
        }
        addView(surfaceView)
        
        // Initialize gesture detector
        gestureDetector = GestureDetector(context, GestureListener())
        
        // Initialize UI components
        initOverlayUI()
    }
    
    private fun initOverlayUI() {
        // Inflate or create overlay UI programmatically
        val overlayView = LayoutInflater.from(context).inflate(R.layout.player_overlay, this, false)
        addView(overlayView)
        
        // Find views
        seekBar = findViewById(R.id.seekBar)
        timeText = findViewById(R.id.timeText)
        totalTimeText = findViewById(R.id.totalTimeText)
        feedbackText = findViewById(R.id.feedbackText)
        videoInfoText = findViewById(R.id.videoInfoText)
        controlsContainer = findViewById(R.id.controlsContainer)
        
        // Setup seek bar listener
        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    handleProgressBarDrag(progress.toDouble())
                }
            }
            
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                cancelAutoHide()
                isSeeking = true
                wasPlayingBeforeSeek = MPVLib.getPropertyBoolean("pause") == false
                if (wasPlayingBeforeSeek) {
                    MPVLib.setPropertyBoolean("pause", true)
                }
            }
            
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                if (wasPlayingBeforeSeek) {
                    coroutineScope.launch {
                        delay(100)
                        MPVLib.setPropertyBoolean("pause", false)
                    }
                }
                isSeeking = false
                wasPlayingBeforeSeek = false
                seekDirection = ""
                scheduleSeekbarHide()
            }
        })
        
        // Start time updates
        startTimeUpdates()
    }
    
    private fun startTimeUpdates() {
        coroutineScope.launch {
            var lastSeconds = -1
            while (true) {
                val currentPos = MPVLib.getPropertyDouble("time-pos") ?: 0.0
                val duration = MPVLib.getPropertyDouble("duration") ?: 1.0
                val currentSeconds = currentPos.toInt()
                
                if (currentSeconds != lastSeconds && !isSeeking) {
                    timeText.text = formatTimeSimple(currentPos)
                    totalTimeText.text = formatTimeSimple(duration)
                    lastSeconds = currentSeconds
                }
                
                if (!isSeeking) {
                    val progress = ((currentPos / duration) * 1000).toInt().coerceIn(0, 1000)
                    seekBar.progress = progress
                }
                
                delay(100)
            }
        }
    }
    
    // Gesture handling
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                touchStartTime = System.currentTimeMillis()
                startLongTapDetection()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isHorizontalSwipe && !isVerticalSwipe && !isLongTap) {
                    when (checkForSwipeDirection(event.x, event.y)) {
                        "horizontal" -> startHorizontalSeeking(event.x)
                        "vertical" -> startVerticalSwipe(event.y)
                    }
                } else if (isHorizontalSwipe) {
                    handleHorizontalSeeking(event.x)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                endTouch()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    private fun startLongTapDetection() {
        longTapJob?.cancel()
        longTapJob = coroutineScope.launch {
            delay(longTapThreshold)
            if (!isHorizontalSwipe && !isVerticalSwipe) {
                isLongTap = true
                MPVLib.setPropertyDouble("speed", 2.0)
                showFeedback("2X")
            }
        }
    }
    
    private fun checkForSwipeDirection(currentX: Float, currentY: Float): String {
        val deltaX = kotlin.math.abs(currentX - touchStartX)
        val deltaY = kotlin.math.abs(currentY - touchStartY)
        
        if (deltaX > horizontalSwipeThreshold && deltaX > deltaY && deltaY < maxVerticalMovement) {
            return "horizontal"
        }
        
        if (deltaY > verticalSwipeThreshold && deltaY > deltaX && deltaX < maxHorizontalMovement) {
            return "vertical"
        }
        
        return ""
    }
    
    private fun startHorizontalSeeking(startX: Float) {
        isHorizontalSwipe = true
        cancelAutoHide()
        seekStartX = startX
        seekStartPosition = MPVLib.getPropertyDouble("time-pos") ?: 0.0
        wasPlayingBeforeSeek = MPVLib.getPropertyBoolean("pause") == false
        isSeeking = true
        
        if (wasPlayingBeforeSeek) {
            MPVLib.setPropertyBoolean("pause", true)
        }
    }
    
    private fun startVerticalSwipe(startY: Float) {
        isVerticalSwipe = true
        cancelAutoHide()
        val deltaY = startY - touchStartY
        
        if (deltaY < 0) {
            seekDirection = "+"
            performQuickSeek(quickSeekAmount)
        } else {
            seekDirection = "-"
            performQuickSeek(-quickSeekAmount)
        }
    }
    
    private fun handleHorizontalSeeking(currentX: Float) {
        if (!isSeeking) return
        
        val deltaX = currentX - seekStartX
        val pixelsPerSecond = 4f / 0.016f
        val timeDeltaSeconds = deltaX / pixelsPerSecond
        val newPositionSeconds = seekStartPosition + timeDeltaSeconds
        val duration = MPVLib.getPropertyDouble("duration") ?: 0.0
        val clampedPosition = newPositionSeconds.coerceIn(0.0, duration)
        
        seekDirection = if (deltaX > 0) "+" else "-"
        
        timeText.text = formatTimeSimple(clampedPosition)
        showFeedback("${formatTimeSimple(clampedPosition)} $seekDirection")
        
        performRealTimeSeek(clampedPosition)
    }
    
    private fun performRealTimeSeek(targetPosition: Double) {
        MPVLib.command(arrayOf("seek", targetPosition.toString(), "absolute", "exact"))
    }
    
    private fun performQuickSeek(seconds: Int) {
        val currentPos = MPVLib.getPropertyDouble("time-pos") ?: 0.0
        val duration = MPVLib.getPropertyDouble("duration") ?: 0.0
        val newPosition = (currentPos + seconds).coerceIn(0.0, duration)
        
        showFeedback(if (seconds > 0) "+$seconds" else "$seconds")
        MPVLib.command(arrayOf("seek", seconds.toString(), "relative", "exact"))
    }
    
    private fun handleProgressBarDrag(targetPosition: Double) {
        val duration = MPVLib.getPropertyDouble("duration") ?: 1.0
        val actualPosition = (targetPosition / 1000.0) * duration
        performRealTimeSeek(actualPosition)
        timeText.text = formatTimeSimple(actualPosition)
    }
    
    private fun endTouch() {
        val touchDuration = System.currentTimeMillis() - touchStartTime
        longTapJob?.cancel()
        
        if (isLongTap) {
            isLongTap = false
            MPVLib.setPropertyDouble("speed", 1.0)
        } else if (isHorizontalSwipe) {
            endHorizontalSeeking()
            isHorizontalSwipe = false
        } else if (isVerticalSwipe) {
            isVerticalSwipe = false
            scheduleSeekbarHide()
        } else if (touchDuration < 150) {
            handleTap()
        }
        
        isHorizontalSwipe = false
        isVerticalSwipe = false
        isLongTap = false
    }
    
    private fun endHorizontalSeeking() {
        if (isSeeking) {
            if (wasPlayingBeforeSeek) {
                coroutineScope.launch {
                    delay(100)
                    MPVLib.setPropertyBoolean("pause", false)
                }
            }
            isSeeking = false
            wasPlayingBeforeSeek = false
            seekDirection = ""
            scheduleSeekbarHide()
        }
    }
    
    private fun handleTap() {
        val currentPaused = MPVLib.getPropertyBoolean("pause") ?: false
        if (currentPaused) {
            coroutineScope.launch {
                val currentPos = MPVLib.getPropertyDouble("time-pos") ?: 0.0
                MPVLib.command(arrayOf("seek", currentPos.toString(), "absolute", "exact"))
                delay(100)
                MPVLib.setPropertyBoolean("pause", false)
            }
            showFeedback("Resume")
        } else {
            MPVLib.setPropertyBoolean("pause", true)
            showFeedback("Pause")
        }
        toggleSeekbar()
    }
    
    private fun toggleSeekbar() {
        if (showSeekbar) {
            showSeekbar = false
            controlsContainer.isVisible = false
        } else {
            showSeekbarWithTimeout()
        }
    }
    
    private fun scheduleSeekbarHide() {
        if (userInteracting) return
        hideSeekbarJob?.cancel()
        hideSeekbarJob = coroutineScope.launch {
            delay(4000)
            showSeekbar = false
            controlsContainer.isVisible = false
        }
    }
    
    private fun cancelAutoHide() {
        userInteracting = true
        hideSeekbarJob?.cancel()
        coroutineScope.launch {
            delay(100)
            userInteracting = false
        }
    }
    
    private fun showSeekbarWithTimeout() {
        showSeekbar = true
        controlsContainer.isVisible = true
        scheduleSeekbarHide()
    }
    
    private fun showFeedback(text: String) {
        feedbackJob?.cancel()
        feedbackText.text = text
        feedbackText.isVisible = true
        feedbackJob = coroutineScope.launch {
            delay(1000)
            feedbackText.isVisible = false
        }
    }
    
    private fun toggleVideoInfo() {
        val show = !videoInfoText.isVisible
        videoInfoText.isVisible = show
        if (show) {
            videoInfoJob?.cancel()
            videoInfoJob = coroutineScope.launch {
                delay(4000)
                videoInfoText.isVisible = false
            }
        }
    }
    
    private fun formatTimeSimple(seconds: Double): String {
        val totalSeconds = seconds.toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return if (hours > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs) 
               else String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
    }
    
    // Inner class for gesture detection
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            toggleVideoInfo()
            return true
        }
    }

    /**
     * Initialize libmpv.
     *
     * Call this once before the view is shown.
     */
    fun initialize(configDir: String, cacheDir: String) {
        MPVLib.create(context)

        /* set normal options (user-supplied config can override) */
        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", configDir)
        for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir"))
            MPVLib.setOptionString(opt, cacheDir)
        initOptions()

        MPVLib.init()

        /* set hardcoded options */
        postInitOptions()
        // could mess up VO init before surfaceCreated() is called
        MPVLib.setOptionString("force-window", "no")
        // need to idle at least once for playFile() logic to work
        MPVLib.setOptionString("idle", "once")

        surfaceView.holder.addCallback(this)
        observeProperties()
        
        // Show initial video info
        coroutineScope.launch {
            delay(1000)
            videoInfoText.text = getVideoTitle()
            videoInfoText.isVisible = true
            videoInfoJob = launch {
                delay(4000)
                videoInfoText.isVisible = false
            }
        }
    }

    /**
     * Deinitialize libmpv.
     *
     * Call this once before the view is destroyed.
     */
    fun destroy() {
        // Disable surface callbacks to avoid using unintialized mpv state
        surfaceView.holder.removeCallback(this)

        MPVLib.destroy()
        
        // Cancel all coroutines
        hideSeekbarJob?.cancel()
        videoInfoJob?.cancel()
        feedbackJob?.cancel()
        quickSeekJob?.cancel()
        longTapJob?.cancel()
    }

    protected abstract fun initOptions()
    protected abstract fun postInitOptions()

    protected abstract fun observeProperties()

    private var filePath: String? = null

    /**
     * Set the first file to be played once the player is ready.
     */
    fun playFile(filePath: String) {
        this.filePath = filePath
    }

    private var voInUse: String = "gpu"

    /**
     * Sets the VO to use.
     * It is automatically disabled/enabled when the surface dis-/appears.
     */
    fun setVo(vo: String) {
        voInUse = vo
        MPVLib.setOptionString("vo", vo)
    }

    // Surface callbacks

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.w(TAG, "attaching surface")
        MPVLib.attachSurface(holder.surface)
        // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
        MPVLib.setOptionString("force-window", "yes")

        if (filePath != null) {
            MPVLib.command(arrayOf("loadfile", filePath as String))
            filePath = null
        } else {
            // We disable video output when the context disappears, enable it back
            MPVLib.setPropertyString("vo", voInUse)
        }
        
        // Show controls initially
        showSeekbarWithTimeout()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.w(TAG, "detaching surface")
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setPropertyString("force-window", "no")
        // Note that before calling detachSurface() we need to be sure that libmpv
        // is done using the surface.
        // FIXME: There could be a race condition here, because I don't think
        // setting a property will wait for VO deinit.
        MPVLib.detachSurface()
        
        // Hide controls
        controlsContainer.isVisible = false
        showSeekbar = false
    }
    
    private fun getVideoTitle(): String {
        val mediaTitle = MPVLib.getPropertyString("media-title")
        if (mediaTitle != null && mediaTitle != "Video" && mediaTitle.isNotBlank()) {
            return mediaTitle.substringBeforeLast(".")
        }
        val mpvPath = MPVLib.getPropertyString("path")
        if (mpvPath != null && mpvPath.isNotBlank()) {
            return mpvPath.substringAfterLast("/").substringBeforeLast(".").ifEmpty { "Video" }
        }
        return "Video"
    }

    companion object {
        private const val TAG = "mpv"
    }
}
