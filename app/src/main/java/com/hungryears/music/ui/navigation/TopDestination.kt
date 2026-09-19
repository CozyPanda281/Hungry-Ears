package com.hungryears.music.ui.navigation

import androidx.annotation.StringRes
import com.hungryears.music.R

enum class TopDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
) {
    Home("home", R.string.tab_home),
    Library("library", R.string.tab_library),
    Search("search", R.string.tab_search),
    Playlists("playlists", R.string.tab_playlists),
}