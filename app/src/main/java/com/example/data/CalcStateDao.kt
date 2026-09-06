package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalcStateDao {
    @Query("SELECT * FROM calc_state WHERE id = 1")
    fun getCalcState(): Flow<CalcStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCalcState(state: CalcStateEntity)
}
