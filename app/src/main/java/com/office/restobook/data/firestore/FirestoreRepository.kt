package com.office.restobook.data.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.office.restobook.data.local.entities.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    
    private val db = FirebaseFirestore.getInstance()
    
    // Collections
    private val ordersCollection = FirestoreEnv.orders()
    private val menuItemsCollection = FirestoreEnv.menuItems()
    private val orderItemsCollection = FirestoreEnv.orderItems()
    private val billsCollection = FirestoreEnv.bills()
    private val expensesCollection = FirestoreEnv.expenses()

    // Orders
    suspend fun insertOrder(order: Order): String {
        val docRef = ordersCollection.document()
        val orderWithId = order.copy(
            id = docRef.id.hashCode().toLong(),
            firestoreId = docRef.id
        )
        docRef.set(orderWithId.toMap()).await()
        return docRef.id
    }
    
    suspend fun updateOrder(order: Order) {
        if (order.firestoreId.isNotEmpty()) {
            ordersCollection.document(order.firestoreId).set(order.toMap()).await()
        } else {
            val snapshot = ordersCollection
                .whereEqualTo("id", order.id)
                .limit(1)
                .get()
                .await()
            snapshot.documents.firstOrNull()?.reference?.set(order.toMap())?.await()
        }
    }

    suspend fun updateOrderStatus(orderId: Long, firestoreId: String, status: String) {
        try {
            if (firestoreId.isNotEmpty()) {
                ordersCollection.document(firestoreId).update("status", status).await()
                return
            }
            
            val snapshot = ordersCollection
                .whereEqualTo("id", orderId)
                .limit(1)
                .get()
                .await()
            
            val doc = snapshot.documents.firstOrNull()
            doc?.reference?.update("status", status)?.await()
        } catch (e: Exception) {
            println("ERROR updating order status: ${e.message}")
        }
    }

    suspend fun updateOrderTotal(orderId: Long, firestoreId: String, totalAmount: Double) {
        try {
            if (firestoreId.isNotEmpty()) {
                ordersCollection.document(firestoreId).update("totalAmount", totalAmount).await()
                return
            }

            val snapshot = ordersCollection
                .whereEqualTo("id", orderId)
                .limit(1)
                .get()
                .await()
            val doc = snapshot.documents.firstOrNull()
            doc?.reference?.update("totalAmount", totalAmount)?.await()
        } catch (e: Exception) {
            println("ERROR updating order total: ${e.message}")
        }
    }
    
    suspend fun deleteOrder(order: Order) {
        if (order.firestoreId.isNotEmpty()) {
            ordersCollection.document(order.firestoreId).delete().await()
        } else {
            val snapshot = ordersCollection
                .whereEqualTo("id", order.id)
                .limit(1)
                .get()
                .await()
            snapshot.documents.firstOrNull()?.reference?.delete()?.await()
        }
    }
    
    suspend fun getOrderById(orderId: Long): Order? {
        val snapshot = ordersCollection
            .whereEqualTo("id", orderId)
            .get()
            .await()
        return snapshot.documents.firstOrNull()?.toObject(Order::class.java)
    }
    
    fun getRunningOrders(): Flow<List<Order>> = callbackFlow {
        val listener = ordersCollection
            .whereNotEqualTo("status", "COMPLETED")
            .orderBy("status")
            .orderBy("startTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val orders = snapshot?.documents?.mapNotNull { it.toObject(Order::class.java) } ?: emptyList()
                trySend(orders)
            }
        awaitClose { listener.remove() }
    }
    
    fun getCompletedOrders(): Flow<List<Order>> = callbackFlow {
        val listener = ordersCollection
            .whereEqualTo("status", "COMPLETED")
            .orderBy("startTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val orders = snapshot?.documents?.mapNotNull { it.toObject(Order::class.java) } ?: emptyList()
                trySend(orders)
            }
        awaitClose { listener.remove() }
    }
    
    // Menu Items
    suspend fun insertMenuItem(menuItem: MenuItem): String {
        val docRef = menuItemsCollection.document()
        val itemWithId = menuItem.copy(id = docRef.id.hashCode().toLong())
        docRef.set(itemWithId.toMap()).await()
        return docRef.id
    }
    
    suspend fun updateMenuItem(menuItem: MenuItem) {
        val snapshot = menuItemsCollection
            .whereEqualTo("id", menuItem.id)
            .get()
            .await()
        snapshot.documents.firstOrNull()?.reference?.set(menuItem.toMap())?.await()
    }
    
    suspend fun deleteMenuItem(menuItem: MenuItem) {
        val snapshot = menuItemsCollection
            .whereEqualTo("id", menuItem.id)
            .get()
            .await()
        snapshot.documents.firstOrNull()?.reference?.delete()?.await()
    }

    suspend fun updateMenuItems(items: List<MenuItem>) {
        val batch = db.batch()
        items.forEach { item ->
            val snapshot = menuItemsCollection
                .whereEqualTo("id", item.id)
                .get()
                .await()
            val doc = snapshot.documents.firstOrNull()
            if (doc != null) {
                batch.set(doc.reference, item.toMap())
            }
        }
        batch.commit().await()
    }
    
    fun getAllMenuItems(): Flow<List<MenuItem>> = callbackFlow {
        val listener = menuItemsCollection
            //.orderBy("category") 
            //.orderBy("sortOrder") // Index pending
            //.orderBy("name")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { it.toObject(MenuItem::class.java) } ?: emptyList()
                // Sort client-side to ensure display order without complex index
                val sortedItems = items.sortedWith(compareBy({ it.category }, { it.sortOrder }, { it.name }))
                trySend(sortedItems)
            }
        awaitClose { listener.remove() }
    }
    
    fun getActiveMenuItems(): Flow<List<MenuItem>> = callbackFlow {
        val listener = menuItemsCollection
            .whereEqualTo("isActive", true)
            //.orderBy("category")
            //.orderBy("sortOrder") // Index pending
            //.orderBy("name")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { it.toObject(MenuItem::class.java) } ?: emptyList()
                // Sort client-side
                val sortedItems = items.sortedWith(compareBy({ it.category }, { it.sortOrder }, { it.name }))
                trySend(sortedItems)
            }
        awaitClose { listener.remove() }
    }

    // ... (Order Items section starts at line 138)

    // ... (Extension functions at the bottom)


    // Order Items
    suspend fun insertOrderItem(orderItem: OrderItem) {
        val docRef = orderItemsCollection.document()
        val itemWithId = orderItem.copy(id = docRef.id.hashCode().toLong())
        docRef.set(itemWithId.toMap()).await()
    }
    
    suspend fun updateOrderItem(orderItem: OrderItem) {
        val snapshot = orderItemsCollection
            .whereEqualTo("id", orderItem.id)
            .get()
            .await()
        snapshot.documents.firstOrNull()?.reference?.set(orderItem.toMap())?.await()
    }
    
    suspend fun deleteOrderItem(orderItem: OrderItem) {
        val snapshot = orderItemsCollection
            .whereEqualTo("id", orderItem.id)
            .get()
            .await()
        snapshot.documents.firstOrNull()?.reference?.delete()?.await()
    }
    
    suspend fun getOrderItemsSync(orderId: Long): List<OrderItem> {
        val snapshot = orderItemsCollection
            .whereEqualTo("orderId", orderId)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(OrderItem::class.java) }
    }
    
    fun getOrderItemsForOrder(orderId: Long): Flow<List<OrderItem>> = callbackFlow {
        val listener = orderItemsCollection
            .whereEqualTo("orderId", orderId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { it.toObject(OrderItem::class.java) } ?: emptyList()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }
    
    suspend fun getOrderItem(orderId: Long, menuItemId: Long, portion: String): OrderItem? {
        val snapshot = orderItemsCollection
            .whereEqualTo("orderId", orderId)
            .whereEqualTo("menuItemId", menuItemId)
            .whereEqualTo("portion", portion)
            .get()
            .await()
        return snapshot.documents.firstOrNull()?.toObject(OrderItem::class.java)
    }
    
    // Bills
    suspend fun completeOrderPayment(orderId: Long, firestoreId: String, bill: Bill) {
        try {
            val batch = db.batch()
            
            // 1. Bill insertion
            val billDoc = billsCollection.document()
            val billWithId = bill.copy(id = billDoc.id.hashCode().toLong())
            batch.set(billDoc, billWithId.toMap())
            
            // 2. Order status update
            val orderDoc = if (firestoreId.isNotEmpty()) {
                ordersCollection.document(firestoreId)
            } else {
                val snapshot = ordersCollection.whereEqualTo("id", orderId).limit(1).get().await()
                snapshot.documents.firstOrNull()?.reference
            }
            
            if (orderDoc != null) {
                batch.update(orderDoc, "status", "COMPLETED")
            }
            
            println("Committing payment batch for order: $orderId")
            batch.commit().await()
            println("Payment batch committed successfully")
        } catch (e: Exception) {
            println("ERROR in completeOrderPayment: ${e.message}")
            throw e
        }
    }

    suspend fun insertBill(bill: Bill) {
        try {
            println("Inserting bill for orderId: ${bill.orderId}")
            val docRef = billsCollection.document()
            val billWithId = bill.copy(id = docRef.id.hashCode().toLong())
            docRef.set(billWithId.toMap()).await()
            println("Bill inserted successfully: ${docRef.id}")
        } catch (e: Exception) {
            println("ERROR inserting bill: ${e.message}")
            e.printStackTrace()
            throw e // Rethrow to let the caller handle it
        }
    }
    
    suspend fun getBillForOrder(orderId: Long): Bill? {
        val snapshot = billsCollection
            .whereEqualTo("orderId", orderId)
            .get()
            .await()
        return snapshot.documents.firstOrNull()?.toObject(Bill::class.java)
    }
    
    fun getAllBills(): Flow<List<Bill>> = callbackFlow {
        val listener = billsCollection
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val bills = snapshot?.documents?.mapNotNull { it.toObject(Bill::class.java) } ?: emptyList()
                trySend(bills)
            }
        awaitClose { listener.remove() }
    }
    
    // Expenses
    suspend fun insertExpense(expense: Expense) {
        val docRef = expensesCollection.document()
        val expenseWithId = expense.copy(id = docRef.id.hashCode().toLong())
        docRef.set(expenseWithId.toMap()).await()
    }
    
    suspend fun updateExpense(expense: Expense) {
        val snapshot = expensesCollection
            .whereEqualTo("id", expense.id)
            .get()
            .await()
        snapshot.documents.firstOrNull()?.reference?.set(expense.toMap())?.await()
    }
    
    suspend fun deleteExpense(expense: Expense) {
        val snapshot = expensesCollection
            .whereEqualTo("id", expense.id)
            .get()
            .await()
        snapshot.documents.firstOrNull()?.reference?.delete()?.await()
    }
    
    fun getExpensesForDate(startOfDay: Long, endOfDay: Long): Flow<List<Expense>> = callbackFlow {
        val listener = expensesCollection
            .whereGreaterThanOrEqualTo("date", startOfDay)
            .whereLessThanOrEqualTo("date", endOfDay)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val expenses = snapshot?.documents?.mapNotNull { it.toObject(Expense::class.java) } ?: emptyList()
                trySend(expenses)
            }
        awaitClose { listener.remove() }
    }
    
    fun getAllExpenses(): Flow<List<Expense>> = callbackFlow {
        val listener = expensesCollection
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val expenses = snapshot?.documents?.mapNotNull { it.toObject(Expense::class.java) } ?: emptyList()
                trySend(expenses)
            }
        awaitClose { listener.remove() }
    }
}

// Extension functions to convert entities to Maps for Firestore
private fun Order.toMap() = mapOf(
    "id" to id,
    "firestoreId" to firestoreId,
    "customerName" to customerName,
    "orderType" to orderType,
    "status" to status,
    "startTime" to startTime,
    "totalAmount" to totalAmount
)

private fun MenuItem.toMap() = mapOf(
    "id" to id,
    "name" to name,
    "category" to category,
    "price" to price,
    "hasPortions" to hasPortions,
    "priceHalf" to priceHalf,
    "priceFull" to priceFull,
    "isVeg" to isVeg,
    "isActive" to isActive,
    "sortOrder" to sortOrder
)

private fun OrderItem.toMap() = mapOf(
    "id" to id,
    "orderId" to orderId,
    "menuItemId" to menuItemId,
    "portion" to portion,
    "itemName" to itemName,
    "quantity" to quantity,
    "priceAtTimeOfOrder" to priceAtTimeOfOrder
)

private fun Bill.toMap() = mapOf(
    "id" to id,
    "orderId" to orderId,
    "subtotal" to subtotal,
    "tax" to tax,
    "discount" to discount,
    "total" to total,
    "paymentMode" to paymentMode,
    "createdAt" to createdAt
)

private fun Expense.toMap() = mapOf(
    "id" to id,
    "description" to description,
    "amount" to amount,
    "date" to date
)
