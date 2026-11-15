package com.zajel.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Recipient::class], version = 1, exportSchema = false)
abstract class ZajelDatabase : RoomDatabase() {
    abstract fun recipientDao(): RecipientDao
}
