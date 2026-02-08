package com.office.restobook.repository

import com.office.restobook.data.firestore.FirestoreRepository
import com.office.restobook.data.local.entities.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch

class RestoRepository(private val firestoreRepo: FirestoreRepository) {

    // Orders
    val runningOrders: Flow<List<OrderWithTotal>> = firestoreRepo.getRunningOrders().map { orders ->
        orders.map { order ->
            OrderWithTotal(order, order.totalAmount)
        }
    }
    
    val completedOrders: Flow<List<OrderWithTotal>> = combine(
        firestoreRepo.getCompletedOrders(),
        firestoreRepo.getAllBills()
    ) { orders, bills ->
        orders.map { order ->
            val bill = bills.find { it.orderId == order.id }
            OrderWithTotal(order, order.totalAmount, bill?.paymentMode)
        }
    }

    suspend fun insertOrder(order: Order): Long {
        val docId = firestoreRepo.insertOrder(order)
        return docId.hashCode().toLong()
    }
    
    suspend fun updateOrder(order: Order) = firestoreRepo.updateOrder(order)
    suspend fun updateOrderStatus(orderId: Long, firestoreId: String, status: String) = 
        firestoreRepo.updateOrderStatus(orderId, firestoreId, status)
    suspend fun updateOrderTotal(orderId: Long, firestoreId: String, totalAmount: Double) = 
        firestoreRepo.updateOrderTotal(orderId, firestoreId, totalAmount)
    suspend fun deleteOrder(order: Order) = firestoreRepo.deleteOrder(order)
    suspend fun getOrderById(orderId: Long): Order? = firestoreRepo.getOrderById(orderId)

    // Menu Items
    val allMenuItems: Flow<List<MenuItem>> = firestoreRepo.getAllMenuItems()
        
    val activeMenuItems: Flow<List<MenuItem>> = firestoreRepo.getActiveMenuItems()

    suspend fun insertMenuItem(menuItem: MenuItem) = firestoreRepo.insertMenuItem(menuItem)
    suspend fun updateMenuItem(menuItem: MenuItem) = firestoreRepo.updateMenuItem(menuItem)
    suspend fun updateMenuItems(items: List<MenuItem>) = firestoreRepo.updateMenuItems(items)
    suspend fun deleteMenuItem(menuItem: MenuItem) = firestoreRepo.deleteMenuItem(menuItem)

    // Order Items
    fun getOrderItemsForOrder(orderId: Long): Flow<List<OrderItem>> = 
        firestoreRepo.getOrderItemsForOrder(orderId)
    
    private suspend fun getOrderItemsSync(orderId: Long): List<OrderItem> {
        return firestoreRepo.getOrderItemsSync(orderId)
    }
    
    suspend fun addOrUpdateOrderItem(orderItem: OrderItem) {
        val existing = firestoreRepo.getOrderItem(orderItem.orderId, orderItem.menuItemId, orderItem.portion)
        if (existing != null) {
            val updated = existing.copy(quantity = existing.quantity + orderItem.quantity)
            if (updated.quantity <= 0) {
                firestoreRepo.deleteOrderItem(existing)
            } else {
                firestoreRepo.updateOrderItem(updated)
            }
        } else if (orderItem.quantity > 0) {
            firestoreRepo.insertOrderItem(orderItem)
        }
        updateTotalForOrder(orderItem.orderId)
    }

    suspend fun setOrderItemQuantity(orderItem: OrderItem, quantity: Int) {
        val existing = firestoreRepo.getOrderItem(orderItem.orderId, orderItem.menuItemId, orderItem.portion)
        if (existing != null) {
            val newQuantity = existing.quantity + quantity
            if (newQuantity <= 0) {
                firestoreRepo.deleteOrderItem(existing)
            } else {
                val updated = existing.copy(quantity = newQuantity)
                firestoreRepo.updateOrderItem(updated)
            }
            updateTotalForOrder(orderItem.orderId)
        }
    }

    private suspend fun updateTotalForOrder(orderId: Long) {
        val order = firestoreRepo.getOrderById(orderId) ?: return
        
        // Fetch all items for this order to calculate new total
        val items = firestoreRepo.getOrderItemsSync(orderId)
        val newTotal = items.sumOf { it.quantity * it.priceAtTimeOfOrder }
        
        // Use partial update with firestoreId for reliability
        firestoreRepo.updateOrderTotal(order.id, order.firestoreId, newTotal)
    }

    suspend fun removeOrderItem(orderItem: OrderItem) {
        firestoreRepo.deleteOrderItem(orderItem)
        updateTotalForOrder(orderItem.orderId)
    }

    // Bills
    suspend fun completeOrderPayment(orderId: Long, firestoreId: String, bill: Bill) = 
        firestoreRepo.completeOrderPayment(orderId, firestoreId, bill)

    suspend fun insertBill(bill: Bill) = firestoreRepo.insertBill(bill)
    suspend fun getBillForOrder(orderId: Long) = firestoreRepo.getBillForOrder(orderId)
    val allBills: Flow<List<Bill>> = firestoreRepo.getAllBills()
    
    suspend fun getOrderWithItems(orderId: Long): OrderWithItems {
        val order = firestoreRepo.getOrderById(orderId) ?: throw IllegalArgumentException("Order not found")
        val items = getOrderItemsSync(orderId)
        return OrderWithItems(order, items)
    }

    // Expenses
    suspend fun insertExpense(expense: Expense) = firestoreRepo.insertExpense(expense)
    suspend fun updateExpense(expense: Expense) = firestoreRepo.updateExpense(expense)
    suspend fun deleteExpense(expense: Expense) = firestoreRepo.deleteExpense(expense)
    
    fun getExpensesForDate(startOfDay: Long, endOfDay: Long): Flow<List<Expense>> = 
        firestoreRepo.getExpensesForDate(startOfDay, endOfDay)
    
    fun getTotalExpensesForDate(startOfDay: Long, endOfDay: Long): Flow<Double?> = 
        firestoreRepo.getExpensesForDate(startOfDay, endOfDay).map { expenses ->
            expenses.sumOf { it.amount }
        }
    
    val allExpenses: Flow<List<Expense>> = firestoreRepo.getAllExpenses()
}

// Data classes for combined queries
data class OrderWithTotal(
    val order: Order,
    val totalAmount: Double,
    val paymentMode: String? = null
)

data class OrderWithItems(
    val order: Order,
    val items: List<OrderItem>
)
