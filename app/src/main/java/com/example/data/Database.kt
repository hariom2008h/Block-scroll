package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "whitelisted_channels")
data class WhitelistedChannel(
    @PrimaryKey
    val channelName: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface WhitelistDao {
    @Query("SELECT * FROM whitelisted_channels ORDER BY timestamp ASC")
    fun getAllChannels(): Flow<List<WhitelistedChannel>>

    @Query("SELECT * FROM whitelisted_channels")
    fun getAllChannelsSync(): List<WhitelistedChannel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: WhitelistedChannel)

    @Query("DELETE FROM whitelisted_channels WHERE channelName = :channelName")
    suspend fun deleteChannel(channelName: String)
}

@Database(entities = [WhitelistedChannel::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun whitelistDao(): WhitelistDao
}
