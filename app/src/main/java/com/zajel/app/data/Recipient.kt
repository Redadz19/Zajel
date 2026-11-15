package com.zajel.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipients")
data class Recipient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phone: String,
    val displayName: String? = null,
    val optedIn: Boolean = false,
    val optedOut: Boolean = false
)
