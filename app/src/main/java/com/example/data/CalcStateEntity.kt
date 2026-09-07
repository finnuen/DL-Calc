package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calc_state")
data class CalcStateEntity(
    @PrimaryKey val id: Int = 1,
    val fileSize: String,
    val fileSizeUnit: String,
    val speed: String,
    val speedUnit: String,
    val days: String = "",
    val hours: String = "",
    val minutes: String = "",
    val seconds: String = "",
    val timeSeconds: String = "",
    val calcMode: String = "NONE",
    val isDarkMode: Boolean
)
