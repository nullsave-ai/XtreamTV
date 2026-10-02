@file:OptIn(UnstableApi::class)

package com.xtreamtv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.util.Locale

data class TrackOption(val label: String, val group: Tracks.Group?, val index: Int, val selected: Boolean)

/** Builds audio / subtitle / quality choices from the player's current tracks. */
object TrackOptions {
    fun list(player: Player, type: Int): List<TrackOption> {
        val tracks = player.currentTracks
        val params = player.trackSelectionParameters
        val out = mutableListOf<TrackOption>()
        val disabled = type in params.disabledTrackTypes
        val groups = tracks.groups.filter { it.type == type }
        if (type == C.TRACK_TYPE_TEXT) out += TrackOption("إيقاف الترجمة", null, -1, disabled || groups.none { it.isSelected })
        if (type == C.TRACK_TYPE_VIDEO) out += TrackOption("تلقائي", null, -1, params.overrides.keys.none { it.type == type })
        groups.forEach { g ->
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                val sel = g.isTrackSelected(i) && !(type == C.TRACK_TYPE_TEXT && disabled) &&
                    !(type == C.TRACK_TYPE_VIDEO && params.overrides.keys.none { it.type == type })
                out += TrackOption(label(type, g.getTrackFormat(i), out.size), g, i, sel)
            }
        }
        return out
    }

    fun hasChoices(player: Player, type: Int): Boolean =
        player.currentTracks.groups.filter { it.type == type }.sumOf { g -> (0 until g.length).count { g.isTrackSupported(it) } } > (if (type == C.TRACK_TYPE_AUDIO) 1 else 0)

    private fun label(type: Int, f: Format, n: Int): String {
        val lang = f.language?.takeIf { it != "und" }?.let { code ->
            Locale(code).getDisplayLanguage(Locale("ar")).ifBlank { code }
        }
        return when (type) {
            C.TRACK_TYPE_VIDEO -> if (f.height > 0) "${f.height}p" else "جودة ${n + 1}"
            C.TRACK_TYPE_AUDIO -> {
                val ch = when (f.channelCount) { 1 -> "أحادي"; 2 -> "ستيريو"; 6 -> "5.1"; 8 -> "7.1"; else -> null }
                listOfNotNull(lang ?: f.label ?: "مسار ${n + 1}", ch).joinToString(" • ")
            }
            else -> lang ?: f.label ?: "ترجمة ${n + 1}"
        }
    }

    fun apply(player: Player, type: Int, opt: TrackOption) {
        val b = player.trackSelectionParameters.buildUpon()
        val g = opt.group
        if (g == null) {
            b.clearOverridesOfType(type)
            if (type == C.TRACK_TYPE_TEXT) b.setTrackTypeDisabled(type, true)
        } else {
            b.setTrackTypeDisabled(type, false).setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, opt.index))
        }
        player.trackSelectionParameters = b.build()
    }
}
