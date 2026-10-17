package com.yourname.pillpantry.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {

    // --- Groceries ---

    @Query("SELECT * FROM groceries ORDER BY name COLLATE NOCASE ASC")
    fun observeGroceries(): Flow<List<Grocery>>

    @Query("SELECT * FROM groceries WHERE onShoppingList = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeGroceriesOnList(): Flow<List<Grocery>>

    @Query("SELECT * FROM groceries")
    suspend fun getGroceries(): List<Grocery>

    @Query("SELECT * FROM groceries WHERE id = :id")
    suspend fun getGrocery(id: Long): Grocery?

    @Query("SELECT * FROM groceries WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findGroceryByName(name: String): Grocery?

    @Insert
    suspend fun insertGrocery(item: Grocery): Long

    @Update
    suspend fun updateGrocery(item: Grocery)

    @Query("UPDATE groceries SET onShoppingList = :onList WHERE id = :id")
    suspend fun setGroceryOnList(id: Long, onList: Boolean)

    @Query("DELETE FROM groceries WHERE id = :id")
    suspend fun deleteGrocery(id: Long)

    // --- Vitamins ---

    @Query("SELECT * FROM vitamins ORDER BY name COLLATE NOCASE ASC")
    fun observeVitamins(): Flow<List<Vitamin>>

    @Query("SELECT * FROM vitamins WHERE onShoppingList = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeVitaminsOnList(): Flow<List<Vitamin>>

    @Query("SELECT * FROM vitamins")
    suspend fun getVitamins(): List<Vitamin>

    @Query("SELECT * FROM vitamins WHERE id = :id")
    suspend fun getVitamin(id: Long): Vitamin?

    @Query("SELECT * FROM vitamins WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findVitaminByName(name: String): Vitamin?

    @Insert
    suspend fun insertVitamin(item: Vitamin): Long

    @Update
    suspend fun updateVitamin(item: Vitamin)

    @Query("UPDATE vitamins SET onShoppingList = :onList WHERE id = :id")
    suspend fun setVitaminOnList(id: Long, onList: Boolean)

    @Query("DELETE FROM vitamins WHERE id = :id")
    suspend fun deleteVitamin(id: Long)

    // --- Custom shopping list items ---

    @Query("SELECT * FROM custom_shopping_items ORDER BY createdAt DESC")
    fun observeCustomItems(): Flow<List<CustomShoppingItem>>

    @Insert
    suspend fun insertCustomItem(item: CustomShoppingItem): Long

    @Query("DELETE FROM custom_shopping_items WHERE id = :id")
    suspend fun deleteCustomItem(id: Long)
}

@Database(
    entities = [Grocery::class, Vitamin::class, CustomShoppingItem::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dao(): PantryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** One database per process, shared by the UI and the background workers. */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pillpantry.db"
                ).build().also { instance = it }
            }
    }
}
