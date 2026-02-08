package com.office.restobook.data.local.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Keep
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String = "",
    val amount: Double = 0.0,
    val date: Long = System.currentTimeMillis()
)
