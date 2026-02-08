package com.office.restobook.data.local.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "orders")
data class Order(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firestoreId: String = "",
    val customerName: String = "",
    val orderType: String = "Dine-in", // Dine-in / Parcel / Zomato
    val startTime: Long = System.currentTimeMillis(),
    val status: String = "RUNNING", // RUNNING / PAYMENT PENDING / COMPLETED
    val totalAmount: Double = 0.0
)
