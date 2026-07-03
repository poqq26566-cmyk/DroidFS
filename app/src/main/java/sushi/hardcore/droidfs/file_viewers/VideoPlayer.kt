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
    private var swipeSeekStartX = 0f
    private var swipeSeekStartY = 0f
    private var swipeSeekStartPositionMs = 0L
    private var isSwipeSeeking = false
    private val swipeSeekThresholdPx by lazy { resources.displayMetrics.density * 24 } // 超过24dp才判定为拖动手势,避免跟点击冲突

    override fun viewFile() {
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.topBar.fitsSystemWindows = true
        binding.videoPlayer.doubleTapOverlay = binding.doubleTapOverlay
        val bottomBar = findViewById<FrameLayout>(R.id.exo_bottom_bar)
        val progressBar = findViewById<View>(R.id.exo_progress)
        ViewCompat.setOnApplyWindowInsetsListener(binding.videoPlayer) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            bottomBar.apply {
                updatePadding(left = insets.left, right = insets.right, bottom = insets.bottom)
                updateLayoutParams<FrameLayout.LayoutParams> {
                    @SuppressLint("PrivateResource")
                    height = resources.getDimensionPixelSize(R.dimen.exo_styled_bottom_bar_height) + insets.bottom
                }
            }
            progressBar.apply {
                updatePadding(left = insets.left, right = insets.right)
                updateLayoutParams<FrameLayout.LayoutParams> {
                    @SuppressLint("PrivateResource")
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.exo_styled_progress_margin_bottom) + insets.bottom
                }
            }
            windowInsets
        }

        binding.videoPlayer.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            binding.topBar.visibility = visibility
            if (visibility == View.VISIBLE) {
                showPartialSystemUi()
            } else {
                hideSystemUi()
            }
        })
        binding.rotateButton.setOnClickListener {
            requestedOrientation =
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                }
        }
        setupSwipeSeek()
        super.viewFile()
    }

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
                    false // 不消费,单击/双击照常走原来的逻辑
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - swipeSeekStartX
                    val dy = event.y - swipeSeekStartY
                    if (!isSwipeSeeking &&
                        abs(dx) > swipeSeekThresholdPx &&
                        abs(dx) > abs(dy) // 横向为主才算快进手势,避免跟其他垂直手势冲突
                    ) {
                        isSwipeSeeking = true
                        binding.videoPlayer.showController() // 拖动时把控制条(含时间显示)唤出来
                    }
                    if (isSwipeSeeking && player != null) {
                        val duration = player.duration
                        if (duration > 0) {
                            // 拖满一屏宽度 = 跳转视频总长的1/3,但最多不超过2分钟,数值可按需调整
                            val maxSeekRangeMs = (duration / 3).coerceAtMost(120_000L)
                            val deltaMs = (dx / view.width * maxSeekRangeMs).toLong()
                            val target = (swipeSeekStartPositionMs + deltaMs).coerceIn(0, duration)
                            player.seekTo(target)
                        }
                        true // 拖动中消费掉事件,不让它触发单击/双击
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasSeeking = isSwipeSeeking
                    isSwipeSeeking = false
                    wasSeeking // 如果刚才是在拖动快进,消费掉UP事件,避免松手时被误判成一次点击
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
