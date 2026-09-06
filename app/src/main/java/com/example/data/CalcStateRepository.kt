package com.example.data

import kotlinx.coroutines.flow.Flow

class CalcStateRepository(private val dao: CalcStateDao) {
    val calcState: Flow<CalcStateEntity?> = dao.getCalcState()

    suspend fun saveState(
        fileSize: String,
        fileSizeUnit: String,
        speed: String,
        speedUnit: String,
        isDarkMode: Boolean
    ) {
        dao.saveCalcState(
            CalcStateEntity(
                id = 1,
                fileSize = fileSize,
                fileSizeUnit = fileSizeUnit,
                speed = speed,
                speedUnit = speedUnit,
                isDarkMode = isDarkMode
            )
        )
    }
}
