package com.office.restobook.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.office.restobook.data.local.entities.Bill
import com.office.restobook.data.local.entities.MenuItem
import com.office.restobook.data.local.entities.Order
import com.office.restobook.data.local.entities.OrderItem
import com.office.restobook.repository.RestoRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class OrderViewModel(private val repository: RestoRepository) : ViewModel() {

    private val _currentOrder = MutableLiveData<Order?>()
    val currentOrder: LiveData<Order?> = _currentOrder

    private val _orderItems = MutableLiveData<List<OrderItem>>()
    val orderItems: LiveData<List<OrderItem>> = _orderItems

    val activeMenuItems: LiveData<List<MenuItem>> = repository.activeMenuItems.asLiveData()
    val allMenuItems: LiveData<List<MenuItem>> = repository.allMenuItems.asLiveData()

    fun loadOrder(orderId: Long) {
        viewModelScope.launch {
            val order = repository.getOrderById(orderId)
            _currentOrder.value = order

            repository.getOrderItemsForOrder(orderId).collectLatest {
                _orderItems.value = it
            }
        }
    }

    fun addItemToOrder(menuItem: MenuItem, portion: String = "Regular", quantity: Int = 1) {
        val orderId = _currentOrder.value?.id ?: return
        viewModelScope.launch {
            val price = when (portion) {
                "Half" -> menuItem.priceHalf ?: menuItem.price
                "Full" -> menuItem.priceFull ?: menuItem.price
                else -> menuItem.price
            }
            val orderItem = OrderItem(
                orderId = orderId,
                menuItemId = menuItem.id,
                portion = portion,
                itemName = menuItem.name,
                quantity = quantity,
                priceAtTimeOfOrder = price
            )
            repository.addOrUpdateOrderItem(orderItem)
        }
    }

    fun updateOrderItemQuantity(orderItem: OrderItem, delta: Int) {
        viewModelScope.launch {
            // We pass a dummy OrderItem with the delta quantity to the repository method
            // The repository method looks up correct portion and adds delta to existing quantity
            val itemDelta = orderItem.copy(quantity = delta)
            repository.addOrUpdateOrderItem(itemDelta)
        }
    }
    
    fun setOrderItemQuantity(orderItem: OrderItem, quantity: Int) {
        viewModelScope.launch {
            repository.setOrderItemQuantity(orderItem, quantity)
        }
    }

    fun completePayment(
        paymentMode: String,
        subtotal: Double,
        tax: Double,
        discount: Double,
        total: Double
    ) {
        val order = _currentOrder.value ?: return
        viewModelScope.launch {
            val bill = Bill(
                orderId = order.id,
                subtotal = subtotal,
                tax = tax,
                discount = discount,
                total = total,
                paymentMode = paymentMode
            )
            
            // Use NonCancellable to ensure both actions finish 
            // even if the user navigates away.
            withContext(NonCancellable) {
                try {
                    println("Starting atomic payment completion for order: ${order.id}")
                    // Add a timeout of 10 seconds to prevent indefinite hanging if broker fails
                    kotlinx.coroutines.withTimeout(10000) {
                        repository.completeOrderPayment(order.id, order.firestoreId, bill)
                    }
                    println("Payment completion successful (Atomic)")
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    println("ERROR: Payment confirmation timed out! Broker might be hanging.")
                } catch (e: Exception) {
                    println("ERROR during payment matching: ${e.message}")
                    e.printStackTrace()
                    // If atomic fails, we could potentially retry status only as fallback,
                    // but usually a timeout means all Firestore writes are blocked.
                }
            }
        }
    }

    fun getBillForOrder(orderId: Long, onResult: (Bill?) -> Unit) {
        viewModelScope.launch {
            val bill = repository.getBillForOrder(orderId)
            onResult(bill)
        }
    }
}
