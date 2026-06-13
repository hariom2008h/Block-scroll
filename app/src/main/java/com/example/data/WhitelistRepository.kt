package com.example.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow

class WhitelistRepository(private val dao: WhitelistDao) {
    val allChannels: Flow<List<WhitelistedChannel>> = dao.getAllChannels()

    fun getAllChannelsSync(): List<WhitelistedChannel> = dao.getAllChannelsSync()

    suspend fun insert(channel: WhitelistedChannel) = dao.insertChannel(channel)

    suspend fun delete(channelName: String) = dao.deleteChannel(channelName)
}

object DatabaseProvider {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "app_database"
            ).build()
            INSTANCE = instance
            instance
        }
    }
}
