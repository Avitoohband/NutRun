package com.avitoohband.nutrun

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avitoohband.nutrun.data.FoodLogEntity
import com.avitoohband.nutrun.data.NutRunDatabase
import com.avitoohband.nutrun.data.UserProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountIsolationDatabaseTest {
    private lateinit var database: NutRunDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NutRunDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun profileAndFoodQueriesNeverReturnAnotherAccountsRows() = runBlocking {
        val dao = database.dao()
        dao.saveProfile(profile("user-a", "a@example.com"))
        dao.saveFood(food("user-a", "food-a"))

        assertEquals("a@example.com", dao.profile("user-a")?.email)
        assertNull(dao.profile("user-b"))
        assertEquals(1, dao.observeFood("user-a", "2026-07-23").first().size)
        assertEquals(0, dao.observeFood("user-b", "2026-07-23").first().size)
    }

    private fun profile(userId: String, email: String) = UserProfileEntity(
        id = "profile:$userId",
        userId = userId,
        email = email,
        birthDate = "1990-01-01",
        biologicalSex = "MALE",
        heightCm = 180.0,
        weightKg = 80.0,
        activityLevel = "MODERATE",
        goal = "MAINTAIN",
        unitSystem = "METRIC",
        calorieTarget = 2_500,
        updatedAtMillis = 1
    )

    private fun food(userId: String, id: String) = FoodLogEntity(
        id = id,
        userId = userId,
        localDate = "2026-07-23",
        mealType = "BREAKFAST",
        catalogId = null,
        name = "Oats",
        brand = null,
        servingGrams = 100.0,
        calories = 379,
        proteinGrams = 13.2,
        carbohydrateGrams = 67.7,
        fatGrams = 6.5,
        updatedAtMillis = 1
    )
}
