@file:OptIn(UnstableApi::class)

package com.xtreamtv.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.xtreamtv.data.UA

object PlayerFactory {
    /** Live: small buffers for fast zapping. VOD: larger buffers for smooth seeking. */
    fun create(ctx: Context, live: Boolean): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(UA)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(if (live) 6_000 else 20_000, if (live) 30_000 else 60_000, 1_500, 3_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val renderers = DefaultRenderersFactory(ctx).setEnableDecoderFallback(true)
        return ExoPlayer.Builder(ctx, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(DefaultDataSource.Factory(ctx, http)))
            .setLoadControl(load)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
}
