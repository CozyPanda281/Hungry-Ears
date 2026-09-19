package com.hungryears.music.data.repository

import com.hungryears.music.data.local.db.HungryEarsDatabase
import com.hungryears.music.data.local.db.entity.FavouriteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FavouritesRepository(database: HungryEarsDatabase) {

    private val dao = database.libraryDao()

    fun observeFavouriteIds(): Flow<Set<Long>> =
        dao.observeFavouriteIds().map { it.toHashSet() }

    suspend fun isFavourite(trackId: Long): Boolean = withContext(Dispatchers.IO) {
        dao.isFavourite(trackId)
    }

    suspend fun setFavourite(trackId: Long, favourite: Boolean) = withContext(Dispatchers.IO) {
        if (favourite) {
            dao.addFavourite(FavouriteEntity(trackId, System.currentTimeMillis()))
        } else {
            dao.removeFavourite(trackId)
        }
    }

    suspend fun toggle(trackId: Long) = withContext(Dispatchers.IO) {
        if (dao.isFavourite(trackId)) dao.removeFavourite(trackId) else dao.addFavourite(
            FavouriteEntity(trackId, System.currentTimeMillis()),
        )
    }
}
