package com.office.restobook.data.local.database

import android.content.Context
import androidx.room.*
import com.office.restobook.data.local.dao.RestoDao
import com.office.restobook.data.local.entities.*
import kotlinx.coroutines.launch

@Database(
    entities = [Order::class, MenuItem::class, OrderItem::class, Bill::class, Expense::class],
    version = 3, // Incremented version to include Expense entity
    exportSchema = false
)
abstract class RestoDatabase : RoomDatabase() {

    abstract fun restoDao(): RestoDao

    companion object {
        @Volatile
        private var INSTANCE: RestoDatabase? = null

        fun getDatabase(context: Context): RestoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RestoDatabase::class.java,
                    "resto_database"
                )
                    .fallbackToDestructiveMigration() // Use destructive migration for development
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
