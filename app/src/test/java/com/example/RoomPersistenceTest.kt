package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CalcStateRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: CalcStateRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CalcStateRepository(db.calcStateDao())
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testSaveAndRetrieveState() = runBlocking {
        repository.saveState(
            fileSize = "25.5",
            fileSizeUnit = "GB",
            speed = "150",
            speedUnit = "Mbps",
            isDarkMode = true
        )

        val saved = repository.calcState.firstOrNull()
        assertNotNull(saved)
        assertEquals("25.5", saved?.fileSize)
        assertEquals("GB", saved?.fileSizeUnit)
        assertEquals("150", saved?.speed)
        assertEquals("Mbps", saved?.speedUnit)
        assertTrue(saved?.isDarkMode == true)
    }
}
