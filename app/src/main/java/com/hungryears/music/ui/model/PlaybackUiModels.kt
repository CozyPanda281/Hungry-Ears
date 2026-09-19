package com.hungryears.music.ui.model

data class TrackUi(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
)

data class ArtistUi(
    val id: String,
    val name: String,
)