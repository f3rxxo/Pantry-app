package com.yourname.pillpantry.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A tracked grocery item. Stored locally in the "groceries" table. */
@Entity(tableName = "groceries")
data class Grocery(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: Long = 0,
    val portions: Long = 0,
    val portionsPerUnit: Long = 1,
    val portionsThreshold: Long = 0,
    /** Epoch millis of the last portion update (decay/restock/creation). */
    val lastPortionUpdate: Long? = null,
    val onShoppingList: Boolean = false
) {
    val isLowOnPortions: Boolean get() = portions <= portionsThreshold
}

/** A tracked vitamin/supplement. Stored locally in the "vitamins" table. */
@Entity(tableName = "vitamins")
data class Vitamin(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val currentPills: Long = 0,
    val dailyDosage: Long = 1,
    val refillThreshold: Long = 10,
    /** Epoch millis of the last logged dose, or null if never taken. */
    val lastTaken: Long? = null,
    val onShoppingList: Boolean = false
) {
    val isLow: Boolean get() = currentPills <= refillThreshold

    /** True if [lastTaken] falls on today's date (device-local timezone). */
    val takenToday: Boolean
        get() {
            val last = lastTaken ?: return false
            val lastDate = Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).toLocalDate()
            return lastDate == LocalDate.now()
        }
}

/**
 * A one-off shopping list entry that isn't tracked in the groceries or
 * vitamins tables at all — e.g. "paper towels". Checking one off deletes it
 * outright, since there's no underlying tracked item for it to "return" to.
 */
@Entity(tableName = "custom_shopping_items")
data class CustomShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
