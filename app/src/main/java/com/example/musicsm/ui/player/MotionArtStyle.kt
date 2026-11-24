package com.example.musicsm.ui.player

/** Where Now Playing puts a release's looping cover video. Stored by [name]. */
enum class MotionArtStyle {
    /** Inside the rounded album card, with the blurred backdrop drifting behind it. */
    CARD,

    /** Apple Music style: a borderless square across the top of the player, fading into it. */
    EDGE,

    /** Filling the whole player behind the controls. */
    FULL_SCREEN;

    companion object {
        fun fromKey(key: String?): MotionArtStyle = entries.firstOrNull { it.name == key } ?: EDGE
    }
}
