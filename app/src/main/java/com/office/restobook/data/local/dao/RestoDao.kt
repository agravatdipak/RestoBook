package com.office.restobook.data.local.dao

import androidx.room.*
import com.office.restobook.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RestoDao {

    // Orders
    @Insert
    suspend fun insertOrder(order: Order): Long

    @Update
    suspend fun updateOrder(order: Order)

    @Delete
    suspend fun deleteOrder(order: Order)

    @Query("""
        SELECT o.*, COALESCE(SUM(oi.quantity * oi.priceAtTimeOfOrder), 0.0) as calculatedTotal 
        FROM orders o 
        LEFT JOIN order_items oi ON o.id = oi.orderId 
        WHERE o.status != 'COMPLETED' 
        GROUP BY o.id 
        ORDER BY o.startTime DESC
    """)
    fun getRunningOrdersWithTotal(): Flow<List<OrderWithTotal>>

    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getOrderById(orderId: Long): Order?

    @Query("""
        SELECT o.*, COALESCE(SUM(oi.quantity * oi.priceAtTimeOfOrder), 0.0) as calculatedTotal 
        FROM orders o 
        LEFT JOIN order_items oi ON o.id = oi.orderId 
        WHERE o.status = 'COMPLETED' 
        GROUP BY o.id 
        ORDER BY o.startTime DESC
    """)
    fun getCompletedOrdersWithTotal(): Flow<List<OrderWithTotal>>

    // Menu Items
    @Insert
    suspend fun insertMenuItem(menuItem: MenuItem): Long

    @Update
    suspend fun updateMenuItem(menuItem: MenuItem)

    @Delete
    suspend fun deleteMenuItem(menuItem: MenuItem)

    @Query("SELECT * FROM menu_items ORDER BY category ASC")
    fun getAllMenuItems(): Flow<List<MenuItem>>

    @Query("SELECT * FROM menu_items WHERE isActive = 1 ORDER BY category ASC")
    fun getActiveMenuItems(): Flow<List<MenuItem>>

    // Order Items
    @Insert
    suspend fun insertOrderItem(orderItem: OrderItem)

    @Update
    suspend fun updateOrderItem(orderItem: OrderItem)

    @Delete
    suspend fun deleteOrderItem(orderItem: OrderItem)

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    fun getOrderItemsForOrder(orderId: Long): Flow<List<OrderItem>>
    
    @Query("SELECT * FROM order_items WHERE orderId = :orderId AND menuItemId = :menuItemId AND portion = :portion")
    suspend fun getOrderItem(orderId: Long, menuItemId: Long, portion: String): OrderItem?

    // Bills
    @Insert
    suspend fun insertBill(bill: Bill)

    @Query("SELECT * FROM bills WHERE orderId = :orderId")
    suspend fun getBillForOrder(orderId: Long): Bill?

    @Query("SELECT * FROM bills ORDER BY createdAt DESC")
    fun getAllBills(): Flow<List<Bill>>
    
    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getOrderWithItems(orderId: Long): OrderWithItems

    // Expenses
    @Insert
    suspend fun insertExpense(expense: Expense)

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("SELECT * FROM expenses WHERE date >= :startOfDay AND date <= :endOfDay ORDER BY date DESC")
    fun getExpensesForDate(startOfDay: Long, endOfDay: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT SUM(amount) FROM expenses WHERE date >= :startOfDay AND date <= :endOfDay")
    fun getTotalExpensesForDate(startOfDay: Long, endOfDay: Long): Flow<Double?>
}

data class OrderWithTotal(
    @Embedded val order: Order,
    val calculatedTotal: Double
)

data class OrderWithItems(
    @Embedded val order: Order,
    @Relation(
        parentColumn = "id",
        entityColumn = "orderId"
    )
    val items: List<OrderItem>
)
