package com.lizongying.mytv0

import android.media.MediaCodec
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import com.lizongying.mytv0.databinding.PlayerBinding
import com.lizongying.mytv0.models.TVModel


class PlayerFragment : Fragment() {
    private var _binding: PlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null

    private var tvModel: TVModel? = null
    private val aspectRatio = 16f / 9f

    private lateinit var mainActivity: MainActivity

    private var metadata = Metadata()

    private var desiredPlayWhenReady = false
    private var recreateCount = 0
    private var lastRecreateTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var playGeneration = 0

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        mainActivity = activity as MainActivity
        super.onActivityCreated(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PlayerBinding.inflate(inflater, container, false)
        val playerView = _binding!!.playerView

        playerView.viewTreeObserver?.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            @OptIn(UnstableApi::class)
            override fun onGlobalLayout() {
                playerView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                if (player == null) {
                    createPlayer()
                }

                (activity as MainActivity).ready(TAG)
                Log.i(TAG, "player ready")
            }
        })

        return _binding!!.root
    }

    @OptIn(UnstableApi::class)
    private fun createPlayer() {
        val renderersFactory = DefaultRenderersFactory(requireContext())
        renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        renderersFactory.setEnableDecoderFallback(true)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()

        player = ExoPlayer.Builder(requireContext())
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .build()
        binding.playerView.player = player
        player?.repeatMode = REPEAT_MODE_ALL
        player?.playWhenReady = desiredPlayWhenReady
        player?.addAnalyticsListener(metadataListener)
        player?.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val playerView = binding.playerView
                val ratio = playerView.measuredWidth.div(playerView.measuredHeight)
                val layoutParams = playerView.layoutParams
                if (ratio < aspectRatio) {
                    layoutParams?.height =
                        (playerView.measuredWidth.div(aspectRatio)).toInt()
                    playerView.layoutParams = layoutParams
                } else if (ratio > aspectRatio) {
                    layoutParams?.width =
                        (playerView.measuredHeight.times(aspectRatio)).toInt()
                    playerView.layoutParams = layoutParams
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                if (isPlaying) {
                    hidePlayerLoading()
                    tvModel?.confirmSourceType()
                    tvModel?.setErrInfo("")
                    tvModel?.retryTimes = 0
                } else {
                    Log.i(TAG, "${tvModel?.tv?.title} 播放停止")
//                                tvModel?.setErrInfo("播放停止")
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateString = when (playbackState) {
                    Player.STATE_IDLE -> "idle"
                    Player.STATE_BUFFERING -> "buffering"
                    Player.STATE_READY -> "ready"
                    Player.STATE_ENDED -> "end"
                    else -> "unknown"
                }
                Log.d(TAG, "playbackState $stateString")
                if (playbackState == Player.STATE_BUFFERING) {
                    showPlayerLoading()
                }
                super.onPlaybackStateChanged(playbackState)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    mainActivity.onPlayEnd()
                }
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                handlePlayerError(error)
            }
        })
    }

    @OptIn(UnstableApi::class)
    fun play(tvModel: TVModel) {
        this.tvModel = tvModel
        val currentGeneration = ++playGeneration
        if (player == null) {
            createPlayer()
        }
        player?.run {
            IgnoreSSLCertificate.ignore()
            val httpDataSource = DefaultHttpDataSource.Factory()
            httpDataSource.setKeepPostFor302Redirects(true)
            httpDataSource.setAllowCrossProtocolRedirects(true)
            httpDataSource.setTransferListener(object : TransferListener {
                override fun onTransferInitializing(
                    source: DataSource,
                    dataSpec: DataSpec,
                    isNetwork: Boolean
                ) {
//                    TODO("Not yet implemented")
                }

                override fun onTransferStart(
                    source: DataSource,
                    dataSpec: DataSpec,
                    isNetwork: Boolean
                ) {
                    Log.d(TAG, "onTransferStart uri ${source.uri}")
//                    TODO("Not yet implemented")
                }

                override fun onBytesTransferred(
                    source: DataSource,
                    dataSpec: DataSpec,
                    isNetwork: Boolean,
                    bytesTransferred: Int
                ) {
//                    TODO("Not yet implemented")
                }

                override fun onTransferEnd(
                    source: DataSource,
                    dataSpec: DataSpec,
                    isNetwork: Boolean
                ) {
//                    TODO("Not yet implemented")
                }
            })

            val dataSource = tvModel.getSource()
            if (dataSource != null) {
                setMediaSource(dataSource)
            } else {
                setMediaItem(tvModel.getMediaItem())
            }

            prepare()
            playWhenReady = true
            desiredPlayWhenReady = true
            showPlayerLoading()
            handler.postDelayed({
                handleStartupTimeout(currentGeneration)
            }, STARTUP_TIMEOUT_MS)
        }
    }

    private fun handlePlayerError(error: PlaybackException) {
        hidePlayerLoading()
        Log.i(
            TAG,
            "播放错误 ${error.errorCode}||| ${error.errorCodeName}||| ${error.message}||| $error"
        )

        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            tvModel?.setReady()
            return
        }

        showPlaybackError(error)

        if (isFatalCodecError(error)) {
            recreatePlayerAndPlayNext()
            return
        }

        handleRecoverablePlayerError()
    }

    private fun handleRecoverablePlayerError() {
        mainActivity.playNextAfterPlaybackError(tvModel)
    }

    private fun showPlaybackError(error: PlaybackException) {
        showShortToast("错误码[${error.errorCode}] ${error.errorCodeName}")
    }

    private fun showShortToast(message: String) {
        val toast = Toast.makeText(requireContext().applicationContext, message, Toast.LENGTH_SHORT)
        toast.show()
        handler.postDelayed({ toast.cancel() }, ERROR_TOAST_MS)
    }

    private fun failStartup() {
        showShortToast("播放启动超时")
        releasePlayer()
        mainActivity.playNextAfterPlaybackError(tvModel)
    }

    private fun showPlayerLoading() {
        _binding?.playerLoading?.visibility = View.VISIBLE
    }

    private fun hidePlayerLoading() {
        _binding?.playerLoading?.visibility = View.GONE
    }

    private fun handleStartupTimeout(generation: Int) {
        val currentPlayer = player ?: return
        if (generation != playGeneration || currentPlayer.isPlaying) {
            return
        }
        if (currentPlayer.playbackState == Player.STATE_BUFFERING ||
            currentPlayer.playbackState == Player.STATE_IDLE
        ) {
            Log.w(TAG, "播放启动超时")
            failStartup()
        }
    }

    @OptIn(UnstableApi::class)
    private fun isFatalCodecError(error: PlaybackException): Boolean {
        var cause = error.cause
        while (cause != null) {
            if (cause is MediaCodecRenderer.DecoderInitializationException) {
                return true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                cause is MediaCodec.CodecException
            ) {
                return true
            }
            cause = cause.cause
        }

        return error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED &&
                error.cause == null
    }

    private fun canRecreate(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastRecreateTime > RECREATE_WINDOW_MS) {
            recreateCount = 0
        }
        if (recreateCount >= MAX_RECREATE_COUNT) {
            return false
        }
        recreateCount++
        lastRecreateTime = now
        return true
    }

    private fun recreatePlayerAndPlayNext() {
        releasePlayer()
        if (canRecreate()) {
            createPlayer()
        }
        mainActivity.playNextAfterPlaybackError(tvModel)
    }

    private fun releasePlayer() {
        playGeneration++
        hidePlayerLoading()
        player?.clearVideoSurface()
        player?.release()
        _binding?.playerView?.player = null
        player = null
    }

    override fun onStart() {
        Log.i(TAG, "onStart")
        super.onStart()
    }

    override fun onResume() {
        Log.i(TAG, "play-onResume")
        super.onResume()
        player?.playWhenReady = desiredPlayWhenReady
    }

    override fun onPause() {
        super.onPause()
        desiredPlayWhenReady = player?.playWhenReady ?: desiredPlayWhenReady
        player?.playWhenReady = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releasePlayer()
        _binding = null
    }

    protected fun triggerMetadata(metadata: Metadata) {
        //onMetadataListeners.forEach { it(metadata) }
        Log.d(TAG, "metadata: $metadata")
    }

    private val metadataListener = @UnstableApi object : AnalyticsListener {
        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(
                videoMimeType = format.sampleMimeType ?: "",
                videoWidth = format.width,
                videoHeight = format.height,
                videoColor = format.colorInfo?.toLogString() ?: "",
                // TODO 帧率、比特率目前是从tag中获取，有的返回空，后续需要实时计算
                videoFrameRate = format.frameRate,
                videoBitrate = format.bitrate,
            )
            triggerMetadata(metadata)
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            metadata = metadata.copy(videoDecoder = decoderName)
            triggerMetadata(metadata)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(
                audioMimeType = format.sampleMimeType ?: "",
                audioChannels = format.channelCount,
                audioSampleRate = format.sampleRate,
            )
            triggerMetadata(metadata)
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            metadata = metadata.copy(audioDecoder = decoderName)
            triggerMetadata(metadata)
        }
    }

    /** 元数据 */
    data class Metadata(
        /** 视频编码 */
        val videoMimeType: String = "",
        /** 视频宽度 */
        val videoWidth: Int = 0,
        /** 视频高度 */
        val videoHeight: Int = 0,
        /** 视频颜色 */
        val videoColor: String = "",
        /** 视频帧率 */
        val videoFrameRate: Float = 0f,
        /** 视频比特率 */
        val videoBitrate: Int = 0,
        /** 视频解码器 */
        val videoDecoder: String = "",

        /** 音频编码 */
        val audioMimeType: String = "",
        /** 音频通道 */
        val audioChannels: Int = 0,
        /** 音频采样率 */
        val audioSampleRate: Int = 0,
        /** 音频解码器 */
        val audioDecoder: String = "",
    )

    companion object {
        private const val TAG = "PlayerFragment"
        private const val MAX_RECREATE_COUNT = 3
        private const val RECREATE_WINDOW_MS = 10_000L
        private const val MIN_BUFFER_MS = 5_000
        private const val MAX_BUFFER_MS = 15_000
        private const val BUFFER_FOR_PLAYBACK_MS = 1_000
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 2_500
        private const val STARTUP_TIMEOUT_MS = 18_000L
        private const val ERROR_TOAST_MS = 1_000L
    }
}
