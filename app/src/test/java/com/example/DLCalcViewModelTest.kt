package com.example

import com.example.data.CalcStateDao
import com.example.data.CalcStateEntity
import com.example.data.CalcStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DLCalcViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeDao : CalcStateDao {
        var savedState: CalcStateEntity? = null
        override fun getCalcState(): Flow<CalcStateEntity?> = flowOf(savedState)
        override suspend fun saveCalcState(state: CalcStateEntity) {
            savedState = state
        }
    }

    private lateinit var viewModel: DLCalcViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val repository = CalcStateRepository(FakeDao())
        viewModel = DLCalcViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testDaysHoursMinutesSecondsUpdateTotalSeconds() = runTest {
        viewModel.onDaysChange("1")
        assertEquals("1", viewModel.uiState.value.days)
        assertEquals("86400", viewModel.uiState.value.timeSeconds)

        viewModel.onHoursChange("2")
        assertEquals("2", viewModel.uiState.value.hours)
        assertEquals("93600", viewModel.uiState.value.timeSeconds)

        viewModel.onMinutesChange("30")
        assertEquals("30", viewModel.uiState.value.minutes)
        assertEquals("95400", viewModel.uiState.value.timeSeconds)

        viewModel.onSecondsChange("15")
        assertEquals("15", viewModel.uiState.value.seconds)
        assertEquals("95415", viewModel.uiState.value.timeSeconds)
    }

    @Test
    fun testTimeSecondsSyncsBreakdown() = runTest {
        // 3665 seconds = 1 hour, 1 minute, 5 seconds
        viewModel.onTimeSecondsChange("3665")
        assertEquals("3665", viewModel.uiState.value.timeSeconds)
        assertEquals("", viewModel.uiState.value.days)
        assertEquals("1", viewModel.uiState.value.hours)
        assertEquals("1", viewModel.uiState.value.minutes)
        assertEquals("5", viewModel.uiState.value.seconds)

        // 70 seconds = 0 days, 0 hours, 1 minute, 10 seconds -> 0s are left blank
        viewModel.onTimeSecondsChange("70")
        assertEquals("", viewModel.uiState.value.days)
        assertEquals("", viewModel.uiState.value.hours)
        assertEquals("1", viewModel.uiState.value.minutes)
        assertEquals("10", viewModel.uiState.value.seconds)

        // 86400 seconds = 1 day, 0 hours, 0 minutes, 0 seconds -> 0s are left blank
        viewModel.onTimeSecondsChange("86400")
        assertEquals("1", viewModel.uiState.value.days)
        assertEquals("", viewModel.uiState.value.hours)
        assertEquals("", viewModel.uiState.value.minutes)
        assertEquals("", viewModel.uiState.value.seconds)
    }

    @Test
    fun testTriangleCalculationModeWithDaysAndTime() = runTest {
        // Input days = 1, then input fileSize = 100 -> Mode becomes SPEED
        viewModel.onDaysChange("1")
        viewModel.onFileSizeChange("100")
        assertEquals(CalcMode.SPEED, viewModel.uiState.value.calcMode)

        // Clear all resets mode to NONE and clears all fields
        viewModel.clearAll()
        assertEquals(CalcMode.NONE, viewModel.uiState.value.calcMode)
        assertEquals("", viewModel.uiState.value.days)
        assertEquals("", viewModel.uiState.value.hours)
        assertEquals("", viewModel.uiState.value.minutes)
        assertEquals("", viewModel.uiState.value.seconds)
        assertEquals("", viewModel.uiState.value.timeSeconds)
        assertEquals("", viewModel.uiState.value.fileSize)
        assertEquals("", viewModel.uiState.value.speed)
    }

    @Test
    fun testSpeedAndHoursCalculationMode() = runTest {
        // Input speed = 10, then input hours = 2 -> Mode becomes SIZE
        viewModel.onSpeedChange("10")
        viewModel.onHoursChange("2")
        assertEquals(CalcMode.SIZE, viewModel.uiState.value.calcMode)
        assertEquals("7200", viewModel.uiState.value.timeSeconds)
    }

    @Test
    fun testNumbersOverThousandWithCommas() = runTest {
        // Entering or pasting formatted numbers strips commas so raw values are stored
        viewModel.onFileSizeChange("1,000")
        assertEquals("1000", viewModel.uiState.value.fileSize)

        viewModel.onSpeedChange("10,000")
        assertEquals("10000", viewModel.uiState.value.speed)

        viewModel.onTimeSecondsChange("1,000")
        assertEquals("1000", viewModel.uiState.value.timeSeconds)

        viewModel.onDaysChange("1,000")
        assertEquals("1000", viewModel.uiState.value.days)
    }

    @Test
    fun testFileSizeAdditionCalculation() = runTest {
        viewModel.onFileSizeChange("50+40")
        assertEquals("50+40", viewModel.uiState.value.fileSize)

        // Moving to another field keeps 50+40 as-is while calculating with the sum
        viewModel.onSpeedChange("100")
        assertEquals("50+40", viewModel.uiState.value.fileSize)
        assertEquals(CalcMode.TIME, viewModel.uiState.value.calcMode)

        // Deleting first number ("+40") or middle number ("40++50") preserves '+' and '++' so user can replace the number
        viewModel.onFileSizeChange("+40")
        assertEquals("+40", viewModel.uiState.value.fileSize)
        viewModel.onFileSizeChange("40+10+50")
        viewModel.onFileSizeChange("40++50")
        assertEquals("40++50", viewModel.uiState.value.fileSize)
        viewModel.onFileSizeChange("")
        assertEquals("", viewModel.uiState.value.fileSize)
    }
}
