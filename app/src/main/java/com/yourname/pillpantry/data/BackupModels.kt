package com.yourname.pillpantry.data

// `barcode` is only here so backups exported from the older scanner/Firebase
// versions can still be imported. New exports leave it out.

data class GroceryBackup(
    val name: String,
    val barcode: String? = null,
    val quantity: Long,
    val portions: Long,
    val portionsPerUnit: Long,
    val portionsThreshold: Long,
    val onShoppingList: Boolean
)

data class VitaminBackup(
    val name: String,
    val barcode: String? = null,
    val currentPills: Long,
    val dailyDosage: Long,
    val refillThreshold: Long,
    val onShoppingList: Boolean
)

data class BackupPayload(
    val exportedAt: String,
    val groceries: List<GroceryBackup>,
    val vitamins: List<VitaminBackup>
)

data class ImportSummary(
    val groceriesAdded: Int,
    val groceriesSkipped: Int,
    val vitaminsAdded: Int,
    val vitaminsSkipped: Int
) {
    val message: String
        get() = "Imported $groceriesAdded groceries, $vitaminsAdded vitamins " +
            "($groceriesSkipped groceries, $vitaminsSkipped vitamins skipped — already exist)"
}
