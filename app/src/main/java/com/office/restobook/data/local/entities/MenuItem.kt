package com.office.restobook.data.local.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

import com.google.firebase.firestore.PropertyName

@Keep
@Entity(tableName = "menu_items")
data class MenuItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val category: String = "",
    val price: Double = 0.0,
    val priceHalf: Double? = null,
    val priceFull: Double? = null,
    val hasPortions: Boolean = false,
    @get:PropertyName("isVeg") val isVeg: Boolean = true,
    @get:PropertyName("isActive") val isActive: Boolean = true,
    val sortOrder: Int = 0
)
