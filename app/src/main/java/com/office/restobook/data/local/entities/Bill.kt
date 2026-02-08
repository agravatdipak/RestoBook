package com.office.restobook.data.local.entities

import androidx.annotation.Keep
import androidx.room.*

@Keep
@Entity(
    tableName = "bills",
    foreignKeys = [
        ForeignKey(
            entity = Order::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("orderId")]
)
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long = 0,
    val subtotal: Double = 0.0,
    val tax: Double = 0.0,
    val discount: Double = 0.0,
    val total: Double = 0.0,
    val paymentMode: String = "Cash", // Cash / UPI / Card
    val createdAt: Long = System.currentTimeMillis()
)
