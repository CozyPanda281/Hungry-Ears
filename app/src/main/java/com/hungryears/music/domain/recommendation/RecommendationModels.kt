package com.hungryears.music.domain.recommendation

import com.hungryears.music.domain.model.Artist
import com.hungryears.music.domain.model.Track

/**
 * Pure, explainable recommendation types (RECOMMENDATION_ENGINE.md). Kotlin-only — no Room, no
 * Android — so affinity math, decay, diversity and explanation-consistency are unit-testable
 * without a database. Autoplay (PHASE 8) deliberately lives in `domain/autoplay`, not here.
 */

/** Raw behavioural signal the engine consumes; see RECOMMENDATION_ENGINE §1. */
data class PlaybackSignal(
    val trackId: Long,
    val type: PlaybackEventType,
    val positionMs: Long,
    val durationMs: Long,
    val occurredAt: Long,
)

/** The four playback event kinds the recorder emits (see domain/history/PlaybackEventType). */
enum class PlaybackEventType { PLAY, COMPLETE, SKIP, REPLAY }

/** Provable reason a recommendation is shown; `signal` always names the top contributor. */
data class Explanation(
    val signal: ExplanationSignal,
    val label: String,
) {
    /** Rendered, human-readable reason the UI may show (UI may also hide it). */
    val text: String get() = signal.label(label)
}

enum class ExplanationSignal {
    PLAYED_FREQUENTLY {
        override fun label(name: String) = "Because you played $name often"
    },
    FINISHED_COMPLETELY {
        override fun label(name: String) = "Because you finished $name"
    },
    SAME_ARTIST {
        override fun label(name: String) = "Same artist as $name you like"
    },
    SAME_GENRE {
        override fun label(name: String) = "Similar genre to $name you finished"
    },
    WEEKDAY_HABIT {
        override fun label(name: String) = "Because you play $name on weekends"
    },
    RECENT_BUT_RARE {
        override fun label(name: String) = "A deep cut you added recently"
    },
    SOFT_MOOD {
        override fun label(name: String) = "Calm-sounding genre you lean on"
    },
    ;

    abstract fun label(name: String): String
}

/** Scored affinity for one track with its explanation. */
data class TrackScore(
    val track: Track,
    val affinity: Double,
    val explanation: Explanation,
)

/** Scored affinity for one artist with its explanation. */
data class ArtistScore(
    val artist: Artist,
    val affinity: Double,
    val explanation: Explanation,
)

/** A recommendable item plus the section it belongs to. */
data class Recommendation(
    val trackId: Long,
    val score: Double,
    val explanation: Explanation,
)

/** A pre-baked smart-mix definition (deterministic rule-mix; see §4). */
data class SmartMix(
    val title: String,
    val subtitle: String,
    val reason: Explanation,
)

/** A named section rendered on Home (only when non-empty; never all at once — see §order). */
data class HomeSection(
    val id: String,
    val title: String,
    val tracks: List<TrackScore> = emptyList(),
    val artists: List<ArtistScore> = emptyList(),
    val mixes: List<SmartMix> = emptyList(),
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && artists.isEmpty() && mixes.isEmpty()
}
