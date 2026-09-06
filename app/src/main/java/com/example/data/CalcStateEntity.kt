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
    val isDarkMode: Boolean
)
