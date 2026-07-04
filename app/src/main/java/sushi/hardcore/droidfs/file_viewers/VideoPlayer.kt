package sushi.hardcore.droidfs.file_viewers

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.abs
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.databinding.ActivityVideoPlayerBinding

class VideoPlayer: MediaPlayer(true) {
    private var firstPlay = true
    private val autoFit by lazy {
        sharedPrefs.getBoolean("autoFit", false)
    }
    private lateinit var binding: ActivityVideoPlayerBinding

    // 划屏快进/快退相关状态
    private var isLongPressSpeeding = false
    private var originalPlaybackSpeed = 1f
    private val longPressSpeedMultiplier = 3f // 长按加速倍数,想要3倍速就改成3f
    private val longPressTimeoutMs = 350L // 按住多久判定为"长按"而不是点击,单位毫秒
    private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeSeek() {
        binding.videoPlayer.setOnTouchListener { view, event ->
            val player = binding.videoPlayer.player
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    swipeSeekStartX = event.x
                    swipeSeekStartY = event.y
                    swipeSeekStartPositionMs = player?.currentPosition ?: 0L
                    isSwipeSeeking = false
                    isLongPressSpeeding = false

                    // 开始计时,如果在超时时间内没有移动/松手,就判定为长按加速
                    longPressRunnable = Runnable {
                        if (!isSwipeSeeking && player != null) {
                            isLongPressSpeeding = true
                            originalPlaybackSpeed = player.playbackParameters.speed
                            player.setPlaybackSpeed(originalPlaybackSpeed * longPressSpeedMultiplier)
                        }
                    }
                    longPressHandler.postDelayed(longPressRunnable!!, longPressTimeoutMs)
                    false // 不消费,单击/双击照常走原来的逻辑
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - swipeSeekStartX
                    val dy = event.y - swipeSeekStartY
                    if (!isSwipeSeeking &&
                        abs(dx) > swipeSeekThresholdPx &&
                        abs(dx) > abs(dy)
                    ) {
                        isSwipeSeeking = true
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) } // 开始拖动了,取消长按加速判定
                        binding.videoPlayer.showController()
                    }
                    if (isSwipeSeeking && player != null) {
                        val duration = player.duration
                        if (duration > 0) {
                            // 拖满一屏宽度 = 跳转视频全长,不再封顶,长视频/短视频手感比例一致
                            val deltaMs = (dx / view.width * duration).toLong()
                            val target = (swipeSeekStartPositionMs + deltaMs).coerceIn(0, duration)
                            player.seekTo(target)
                        }
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    val wasSeeking = isSwipeSeeking
                    val wasSpeeding = isLongPressSpeeding
                    if (wasSpeeding && player != null) {
                        player.setPlaybackSpeed(originalPlaybackSpeed)
                    }
                    isSwipeSeeking = false
                    isLongPressSpeeding = false
                    wasSeeking || wasSpeeding // 拖动快进或者长按加速期间,消费掉UP事件,避免松手时被误判成一次点击
                }
                else -> false
            }
        }
    }

    override fun bindPlayer(player: ExoPlayer) {
        binding.videoPlayer.player = player
    }

    override fun onNewFileName(fileName: String) {
        binding.textFileName.text = fileName
    }

    override fun getFileType(): String {
        return "video"
    }

    override fun onVideoSizeChanged(width: Int, height: Int) {
        if (firstPlay && autoFit) {
            requestedOrientation = if (width < height)
                ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            else
                ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            firstPlay = false
        }
    }
}
