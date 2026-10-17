package com.yourname.pillpantry.data

import android.content.Context
import androidx.room.withTransaction
import com.yourname.pillpantry.notifications.NotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** The "portions used per day" cutoff. Central US time, matching the user's household. */
private val PORTION_TIMEZONE: ZoneId = ZoneId.of("America/Chicago")
private const val PORTION_CUTOVER_HOUR = 3 // 3:00 AM Central

/**
 * On-device data access layer (Room / SQLite). Everything lives on the
 * phone — no account, no network, no sync.
 */
class PillPantryRepository(private val db: AppDatabase) {

    private val dao = db.dao()

    companion object {
        fun from(context: Context): PillPantryRepository =
            PillPantryRepository(AppDatabase.get(context))
    }

    fun observeGroceries(): Flow<List<Grocery>> = dao.observeGroceries()

    fun observeVitamins(): Flow<List<Vitamin>> = dao.observeVitamins()

    suspend fun setGroceryOnShoppingList(itemId: Long, onList: Boolean) {
        dao.setGroceryOnList(itemId, onList)
    }

    suspend fun setVitaminOnShoppingList(itemId: Long, onList: Boolean) {
        dao.setVitaminOnList(itemId, onList)
    }

    /** Adds a free-text shopping list entry not tied to any tracked grocery/vitamin. */
    suspend fun addCustomShoppingItem(name: String) {
        dao.insertCustomItem(CustomShoppingItem(name = name))
    }

    /** Removes a custom shopping item outright — there's no underlying tracked item to keep. */
    suspend fun deleteCustomShoppingItem(itemId: Long) {
        dao.deleteCustomItem(itemId)
    }

    /** Groceries, vitamins, AND custom items currently on the shopping list, merged into one flow. */
    fun observeShoppingList(): Flow<ShoppingListSnapshot> =
        combine(
            dao.observeGroceriesOnList(),
            dao.observeVitaminsOnList(),
            dao.observeCustomItems()
        ) { groceries, vitamins, custom ->
            ShoppingListSnapshot(groceries, vitamins, custom)
        }

    /**
     * @param quantity number of units/packages on hand (e.g. 1 carton of eggs)
     * @param portions remaining individual portions (e.g. 3 eggs left)
     * @param portionsThreshold once [portions] drops to/below this, the item
     *   is auto-added to the shopping list
     */
    suspend fun addGrocery(
        name: String,
        quantity: Long,
        portions: Long,
        portionsThreshold: Long
    ) {
        // How many portions one unit represents, e.g. 1 carton = 3 portions.
        // Used later when restocking (see restockGrocery).
        val portionsPerUnit =
            if (quantity > 0) (portions / quantity).coerceAtLeast(1) else portions.coerceAtLeast(1)

        dao.insertGrocery(
            Grocery(
                name = name,
                quantity = quantity,
                portions = portions,
                portionsPerUnit = portionsPerUnit,
                portionsThreshold = portionsThreshold,
                lastPortionUpdate = System.currentTimeMillis(),
                onShoppingList = portions <= portionsThreshold
            )
        )
    }

    /**
     * Restocks a grocery item by [units]. Bumps quantity by [units] and
     * portions by `portionsPerUnit * units`, clearing the shopping-list flag
     * if that brings it back above the threshold. Returns the new portions.
     */
    suspend fun restockGrocery(item: Grocery, units: Long = 1): Long =
        db.withTransaction {
            val current = dao.getGrocery(item.id) ?: return@withTransaction item.portions
            val newPortions = current.portions + (current.portionsPerUnit * units)
            dao.updateGrocery(
                current.copy(
                    quantity = current.quantity + units,
                    portions = newPortions,
                    lastPortionUpdate = System.currentTimeMillis(),
                    onShoppingList = newPortions <= current.portionsThreshold
                )
            )
            newPortions
        }

    suspend fun deleteGrocery(itemId: Long) {
        dao.deleteGrocery(itemId)
    }

    suspend fun deleteVitamin(itemId: Long) {
        dao.deleteVitamin(itemId)
    }

    /** Manually adds [pillsToAdd] pills without going through "Take Dose". */
    suspend fun restockVitamin(item: Vitamin, pillsToAdd: Long): Long =
        db.withTransaction {
            val current = dao.getVitamin(item.id) ?: return@withTransaction item.currentPills
            val newCount = current.currentPills + pillsToAdd
            dao.updateVitamin(
                current.copy(
                    currentPills = newCount,
                    onShoppingList = newCount <= current.refillThreshold
                )
            )
            newCount
        }

    suspend fun addVitamin(
        name: String,
        dailyDosage: Long,
        refillThreshold: Long,
        pillsPerBottle: Long
    ) {
        dao.insertVitamin(
            Vitamin(
                name = name,
                currentPills = pillsPerBottle,
                dailyDosage = dailyDosage,
                refillThreshold = refillThreshold,
                lastTaken = null,
                onShoppingList = pillsPerBottle <= refillThreshold
            )
        )
    }

    /** Returns the new pill count after taking a dose. Auto-flags the shopping list when low. */
    suspend fun takeDose(vitamin: Vitamin): Long =
        db.withTransaction {
            val current = dao.getVitamin(vitamin.id) ?: return@withTransaction vitamin.currentPills
            val newCount = (current.currentPills - current.dailyDosage).coerceAtLeast(0)
            dao.updateVitamin(
                current.copy(
                    currentPills = newCount,
                    lastTaken = System.currentTimeMillis(),
                    onShoppingList = newCount <= current.refillThreshold
                )
            )
            newCount
        }

    /**
     * Edit everything about a vitamin by hand, including the current pill
     * count. The shopping-list flag is re-evaluated against the threshold.
     */
    suspend fun updateVitamin(
        vitaminId: Long,
        name: String,
        currentPills: Long,
        dailyDosage: Long,
        refillThreshold: Long
    ) {
        val current = dao.getVitamin(vitaminId) ?: return
        dao.updateVitamin(
            current.copy(
                name = name,
                currentPills = currentPills,
                dailyDosage = dailyDosage,
                refillThreshold = refillThreshold,
                onShoppingList = currentPills <= refillThreshold
            )
        )
    }

    /** Vitamins that haven't been logged today — used by the evening reminder. */
    suspend fun getVitaminsNotTakenToday(): List<Vitamin> =
        dao.getVitamins().filter { !it.takenToday }

    /**
     * Edit everything about a grocery item by hand, including the current
     * portions. [Grocery.lastPortionUpdate] is reset to now so the daily
     * decay doesn't immediately subtract from the value just typed in, and
     * the shopping-list flag is re-evaluated against the threshold.
     */
    suspend fun updateGrocery(
        itemId: Long,
        name: String,
        quantity: Long,
        portions: Long,
        portionsPerUnit: Long,
        portionsThreshold: Long
    ) {
        val current = dao.getGrocery(itemId) ?: return
        dao.updateGrocery(
            current.copy(
                name = name,
                quantity = quantity,
                portions = portions,
                portionsPerUnit = portionsPerUnit,
                portionsThreshold = portionsThreshold,
                lastPortionUpdate = System.currentTimeMillis(),
                onShoppingList = portions <= portionsThreshold
            )
        )
    }

    /**
     * Catch-up logic for the daily portion decrement. Rather than relying on
     * a background job firing at an exact time (Android won't reliably do
     * that for a personal app — see README), this counts how many 3am
     * Central-time boundaries have passed since each grocery item's
     * [Grocery.lastPortionUpdate] and decrements portions by 1 per boundary
     * crossed. Call this on app launch (and from the best-effort WorkManager
     * job) so the count is always correct whenever the user opens the app,
     * even after several days away.
     *
     * If [context] is provided, also fires a local notification for any item
     * newly pushed onto the shopping list by this decrement.
     */
    suspend fun applyMissedPortionDecrements(
        context: Context? = null,
        now: Instant = Instant.now()
    ): PortionDecayResult {
        val newlyFlagged = mutableListOf<Pair<String, Long>>() // name to portions left
        val updatedNames = mutableListOf<String>()

        db.withTransaction {
            for (item in dao.getGroceries()) {
                val last = item.lastPortionUpdate ?: continue
                if (item.portions <= 0) continue

                val elapsedDays = elapsedCutoverBoundaries(Instant.ofEpochMilli(last), now)
                if (elapsedDays <= 0) continue

                val newPortions = (item.portions - elapsedDays).coerceAtLeast(0)
                val nowOnShoppingList = item.onShoppingList || newPortions <= item.portionsThreshold
                dao.updateGrocery(
                    item.copy(
                        portions = newPortions,
                        lastPortionUpdate = now.toEpochMilli(),
                        onShoppingList = nowOnShoppingList
                    )
                )
                updatedNames.add(item.name)

                if (nowOnShoppingList && !item.onShoppingList) {
                    newlyFlagged.add(item.name to newPortions)
                }
            }
        }

        if (context != null) {
            for ((name, portionsLeft) in newlyFlagged) {
                NotificationHelper.sendShoppingListAlert(context, name, portionsLeft)
            }
        }

        return PortionDecayResult(updatedNames)
    }

    /** Reads everything for a one-shot JSON export — see [ImportSummary]/[BackupPayload]. */
    suspend fun exportBackup(): BackupPayload {
        val groceries = dao.getGroceries().map {
            GroceryBackup(
                name = it.name,
                quantity = it.quantity,
                portions = it.portions,
                portionsPerUnit = it.portionsPerUnit,
                portionsThreshold = it.portionsThreshold,
                onShoppingList = it.onShoppingList
            )
        }
        val vitamins = dao.getVitamins().map {
            VitaminBackup(
                name = it.name,
                currentPills = it.currentPills,
                dailyDosage = it.dailyDosage,
                refillThreshold = it.refillThreshold,
                onShoppingList = it.onShoppingList
            )
        }

        return BackupPayload(
            exportedAt = Instant.now().toString(),
            groceries = groceries,
            vitamins = vitamins
        )
    }

    /**
     * Restores from a JSON export (including ones made by the older
     * scanner/Firebase versions). Matches by name, ignoring case, to avoid
     * duplicates — an item already tracked is skipped rather than
     * overwritten, so importing is safe to run repeatedly.
     */
    suspend fun importBackup(payload: BackupPayload): ImportSummary =
        db.withTransaction {
            var groceriesAdded = 0
            var groceriesSkipped = 0
            var vitaminsAdded = 0
            var vitaminsSkipped = 0
            val nowMillis = System.currentTimeMillis()

            for (g in payload.groceries) {
                if (dao.findGroceryByName(g.name) != null) {
                    groceriesSkipped++
                    continue
                }
                dao.insertGrocery(
                    Grocery(
                        name = g.name,
                        quantity = g.quantity,
                        portions = g.portions,
                        portionsPerUnit = g.portionsPerUnit.coerceAtLeast(1),
                        portionsThreshold = g.portionsThreshold,
                        lastPortionUpdate = nowMillis,
                        onShoppingList = g.onShoppingList
                    )
                )
                groceriesAdded++
            }

            for (v in payload.vitamins) {
                if (dao.findVitaminByName(v.name) != null) {
                    vitaminsSkipped++
                    continue
                }
                dao.insertVitamin(
                    Vitamin(
                        name = v.name,
                        currentPills = v.currentPills,
                        dailyDosage = v.dailyDosage,
                        refillThreshold = v.refillThreshold,
                        lastTaken = null,
                        onShoppingList = v.onShoppingList
                    )
                )
                vitaminsAdded++
            }

            ImportSummary(groceriesAdded, groceriesSkipped, vitaminsAdded, vitaminsSkipped)
        }

    /** Number of 3am Central-time boundaries crossed between [from] and [to]. */
    private fun elapsedCutoverBoundaries(from: Instant, to: Instant): Long {
        fun effectiveDay(instant: Instant): LocalDate {
            val zoned: ZonedDateTime = instant.atZone(PORTION_TIMEZONE)
            return if (zoned.hour < PORTION_CUTOVER_HOUR) {
                zoned.toLocalDate().minusDays(1)
            } else {
                zoned.toLocalDate()
            }
        }
        val days = ChronoUnit.DAYS.between(effectiveDay(from), effectiveDay(to))
        return days.coerceAtLeast(0)
    }
}

data class PortionDecayResult(val updatedItemNames: List<String>) {
    val message: String
        get() = if (updatedItemNames.isEmpty()) {
            "Already up to date — no decay needed right now."
        } else {
            "Applied decay to: ${updatedItemNames.joinToString(", ")}"
        }
}

data class ShoppingListSnapshot(
    val groceries: List<Grocery> = emptyList(),
    val vitamins: List<Vitamin> = emptyList(),
    val customItems: List<CustomShoppingItem> = emptyList()
) {
    val isEmpty: Boolean get() = groceries.isEmpty() && vitamins.isEmpty() && customItems.isEmpty()
}
