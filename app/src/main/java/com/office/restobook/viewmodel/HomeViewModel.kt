package com.office.restobook.viewmodel

import androidx.lifecycle.*
import com.office.restobook.repository.OrderWithTotal
import com.office.restobook.data.local.entities.Order
import com.office.restobook.repository.RestoRepository
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: RestoRepository) : ViewModel() {

    private val _searchQuery = MutableLiveData("")
    
    val runningOrders: LiveData<List<OrderWithTotal>> = MediatorLiveData<List<OrderWithTotal>>().apply {
        val source = repository.runningOrders.asLiveData()
        addSource(source) { orders ->
            value = filterOrders(orders, _searchQuery.value)
        }
        addSource(_searchQuery) { query ->
            value = filterOrders(source.value, query)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun filterOrders(orders: List<OrderWithTotal>?, query: String?): List<OrderWithTotal> {
        if (orders == null) return emptyList()
        if (query.isNullOrBlank()) return orders
        return orders.filter { it.order.customerName.contains(query, ignoreCase = true) }
    }

    suspend fun createOrder(customerName: String, orderType: String): Long {
        val order = Order(customerName = customerName, orderType = orderType, status = "RUNNING")
        return repository.insertOrder(order)
    }

    fun deleteOrder(order: Order) {
        viewModelScope.launch {
            repository.deleteOrder(order)
        }
    }
}
