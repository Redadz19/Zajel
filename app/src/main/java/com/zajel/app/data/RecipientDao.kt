package com.zajel.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipientDao {
    @Query("SELECT * FROM recipients ORDER BY id DESC")
    fun observeAll(): Flow<List<Recipient>>

    @Query("SELECT * FROM recipients WHERE phone = :phone LIMIT 1")
    suspend fun findByPhone(phone: String): Recipient?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(recipient: Recipient): Long

    @Query("UPDATE recipients SET optedOut = 1 WHERE phone = :phone")
    suspend fun optOut(phone: String)

    @Delete
    suspend fun delete(recipient: Recipient)
}
