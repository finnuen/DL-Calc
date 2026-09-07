package com.example.data

import kotlinx.coroutines.flow.Flow

class CalcStateRepository(private val dao: CalcStateDao) {
    val calcState: Flow<CalcStateEntity?> = dao.getCalcState()

    suspend fun saveState(
        fileSize: String,
        fileSizeUnit: String,
        speed: String,
        speedUnit: String,
        days: String = "",
        hours: String = "",
        minutes: String = "",
        seconds: String = "",
        timeSeconds: String = "",
        calcMode: String = "NONE",
        isDarkMode: Boolean
    ) {
        dao.saveCalcState(
            CalcStateEntity(
                id = 1,
                fileSize = fileSize,
                fileSizeUnit = fileSizeUnit,
                speed = speed,
                speedUnit = speedUnit,
                days = days,
                hours = hours,
                minutes = minutes,
                seconds = seconds,
                timeSeconds = timeSeconds,
                calcMode = calcMode,
                isDarkMode = isDarkMode
            )
        )
    }
}
