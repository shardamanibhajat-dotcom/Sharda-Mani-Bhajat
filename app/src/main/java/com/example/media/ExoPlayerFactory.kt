package com.example.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

enum class VideoQualityOption(
    val maxHeight: Int?,
    val maxBitrate: Int?,
    val label: String
) {
    AUTO(null, null, "Auto"),
    HD_720(720, 2_500_000, "720p"),
    SD_480(480, 1_200_000, "480p"),
    DATA_SAVER_360(360, 700_000, "360p")
}

data class PlaybackTrackInfo(
    val activeResolutionLabel: String? = null,
    val isAdaptiveStream: Boolean = false
)

/**
 * Ensures only a single video ExoPlayer is actively playing anywhere in the app
 * (Home -> Player, Shorts Feed, or Upload preview).
 */
object ActivePlaybackCoordinator {
    @Volatile
    private var activePlayer: ExoPlayer? = null

    @Synchronized
    fun onPlaybackStarted(player: ExoPlayer) {
        val prev = activePlayer
        if (prev != null && prev !== player) {
            runCatching {
                prev.playWhenReady = false
                if (prev.isPlaying) {
                    prev.pause()
                }
            }
        }
        activePlayer = player
    }

    @Synchronized
    fun onPlayerReleased(player: ExoPlayer) {
        if (activePlayer === player) {
            activePlayer = null
        }
    }
}

@OptIn(UnstableApi::class)
object ExoPlayerFactory {

    private const val MEDIA_CACHE_MAX_BYTES = 100L * 1024L * 1024L // 100 MB LRU disk cache
    private const val PREFETCH_BYTES_PER_SHORT = 1_500_000L // Pre-buffer initial ~1.5 MB

    @Volatile
    private var mediaCache: SimpleCache? = null

    @Volatile
    private var sharedBandwidthMeter: DefaultBandwidthMeter? = null

    private fun getBandwidthMeter(context: Context): DefaultBandwidthMeter {
        sharedBandwidthMeter?.let { return it }
        return synchronized(this) {
            sharedBandwidthMeter ?: DefaultBandwidthMeter.Builder(context.applicationContext)
                .setResetOnNetworkTypeChange(true)
                .build()
                .also { sharedBandwidthMeter = it }
        }
    }

    private fun getOrCreateCache(context: Context): SimpleCache? {
        mediaCache?.let { return it }
        return synchronized(this) {
            mediaCache ?: runCatching {
                val cacheDir = File(context.applicationContext.cacheDir, "cp_media_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(MEDIA_CACHE_MAX_BYTES)
                val dbProvider = StandaloneDatabaseProvider(context.applicationContext)
                SimpleCache(cacheDir, evictor, dbProvider)
            }.getOrNull()?.also { mediaCache = it }
        }
    }

    private fun createHttpDataSourceFactory(bandwidthMeter: DefaultBandwidthMeter): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(12_000)
            .setAllowCrossProtocolRedirects(true)
            .setTransferListener(bandwidthMeter)
    }

    fun create(context: Context, isShortLoop: Boolean = false): ExoPlayer {
        val appContext = context.applicationContext
        val bandwidthMeter = getBandwidthMeter(appContext)

        val trackSelector = DefaultTrackSelector(appContext).apply {
            setParameters(
                buildUponParameters()
                    .setViewportSizeToPhysicalDisplaySize(appContext, true)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
                    .setExceedVideoConstraintsIfNecessary(true)
            )
        }

        // Fast-start buffer thresholds tuned separately for Shorts vs Long videos
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ if (isShortLoop) 6_000 else 12_000,
                /* maxBufferMs = */ if (isShortLoop) 15_000 else 35_000,
                /* bufferForPlaybackMs = */ if (isShortLoop) 600 else 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ if (isShortLoop) 1_500 else 2_000
            )
            .setBackBuffer(if (isShortLoop) 3_000 else 5_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val httpFactory = createHttpDataSourceFactory(bandwidthMeter)
        val localDataSourceFactory = DefaultDataSource.Factory(appContext, httpFactory)
        val cache = getOrCreateCache(appContext)
        val dataSourceFactory = if (cache != null) {
            val cacheFactory = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            SchemeRoutingDataSourceFactory(
                httpCacheFactory = cacheFactory,
                fallbackFactory = localDataSourceFactory
            )
        } else {
            localDataSourceFactory
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(4))

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        return ExoPlayer.Builder(appContext)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    /**
     * Lightweight disk-cache pre-buffering for upcoming Shorts on Dispatchers.IO.
     * Preloads the opening bytes into SimpleCache without allocating extra MediaCodec decoders,
     * and cancels cleanly if the user swipes past quickly.
     */
    suspend fun prefetchToCache(
        context: Context,
        uriString: String,
        maxBytes: Long = PREFETCH_BYTES_PER_SHORT
    ) {
        if (uriString.isBlank()) return
        val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        val scheme = parsed.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return
        // Skip HLS master manifests here; adaptive segments are resolved at playback time
        if (uriString.contains(".m3u8", ignoreCase = true)) return

        val appContext = context.applicationContext
        val cache = getOrCreateCache(appContext) ?: return

        withContext(Dispatchers.IO) {
            coroutineContext.ensureActive()
            val bandwidthMeter = getBandwidthMeter(appContext)
            val httpFactory = createHttpDataSourceFactory(bandwidthMeter)
            val cacheDataSource = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource()

            val dataSpec = DataSpec.Builder()
                .setUri(parsed)
                .setPosition(0L)
                .setLength(maxBytes)
                .build()

            val writer = CacheWriter(
                cacheDataSource,
                dataSpec,
                null,
                null
            )
            val job = coroutineContext[kotlinx.coroutines.Job]
            val handle = job?.invokeOnCompletion { cause ->
                if (cause != null) {
                    writer.cancel()
                }
            }

            try {
                runCatching {
                    coroutineContext.ensureActive()
                    writer.cache()
                }
            } finally {
                handle?.dispose()
            }
        }
    }

    fun buildMediaItem(uriString: String): MediaItem {
        val builder = MediaItem.Builder().setUri(uriString)
        if (uriString.contains(".m3u8", ignoreCase = true)) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        return builder.build()
    }

    fun applyQuality(player: ExoPlayer, quality: VideoQualityOption) {
        val builder = player.trackSelectionParameters.buildUpon()
        if (quality.maxHeight == null) {
            builder.clearVideoSizeConstraints()
                .setMaxVideoBitrate(Int.MAX_VALUE)
        } else {
            builder.setMaxVideoSize(Int.MAX_VALUE, quality.maxHeight)
                .setMaxVideoBitrate(quality.maxBitrate ?: Int.MAX_VALUE)
        }
        player.trackSelectionParameters = builder.build()
    }

    fun inspectTracks(tracks: Tracks): PlaybackTrackInfo {
        var resolution: String? = null
        var adaptive = false
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                if (group.length > 1) {
                    adaptive = true
                }
                for (i in 0 until group.length) {
                    if (group.isTrackSelected(i)) {
                        val format = group.getTrackFormat(i)
                        if (format.height > 0) {
                            resolution = "${format.height}p"
                        }
                    }
                }
            }
        }
        return PlaybackTrackInfo(
            activeResolutionLabel = resolution,
            isAdaptiveStream = adaptive
        )
    }

    /**
     * Routes HTTP/HTTPS streams through Media3 SimpleCache while passing local Photo Picker
     * content:// and android.resource:// URIs directly to DefaultDataSource.
     */
    private class SchemeRoutingDataSourceFactory(
        private val httpCacheFactory: DataSource.Factory,
        private val fallbackFactory: DataSource.Factory
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return SchemeRoutingDataSource(
                httpCacheDataSource = httpCacheFactory.createDataSource(),
                fallbackDataSource = fallbackFactory.createDataSource()
            )
        }
    }

    private class SchemeRoutingDataSource(
        private val httpCacheDataSource: DataSource,
        private val fallbackDataSource: DataSource
    ) : DataSource {
        private var activeDataSource: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            httpCacheDataSource.addTransferListener(transferListener)
            fallbackDataSource.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val scheme = dataSpec.uri.scheme?.lowercase()
            val target = if (scheme == "http" || scheme == "https") {
                httpCacheDataSource
            } else {
                fallbackDataSource
            }
            activeDataSource = target
            return target.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return activeDataSource?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
        }

        override fun getUri(): Uri? = activeDataSource?.uri

        override fun getResponseHeaders(): MutableMap<String, MutableList<String>> {
            return activeDataSource?.responseHeaders ?: mutableMapOf()
        }

        override fun close() {
            activeDataSource?.close()
            activeDataSource = null
        }
    }
}
