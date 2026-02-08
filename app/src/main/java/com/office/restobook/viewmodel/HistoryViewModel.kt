package com.office.restobook.viewmodel

import androidx.lifecycle.*
import com.office.restobook.repository.OrderWithTotal
import com.office.restobook.repository.RestoRepository
import com.office.restobook.data.local.entities.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.*
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import com.office.restobook.repository.OrderWithItems

class HistoryViewModel(private val repository: RestoRepository) : ViewModel() {

    private val _menuItems = repository.allMenuItems.asLiveData()
    val menuItems: LiveData<List<MenuItem>> = _menuItems

    suspend fun getBillData(orderId: Long): Pair<OrderWithItems, Bill?> {
        val orderWithItems = repository.getOrderWithItems(orderId)
        val bill = repository.getBillForOrder(orderId)
        return Pair(orderWithItems, bill)
    }

    private val selectedDate = MutableStateFlow(Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis)

    val currentSelectedDate: LiveData<Long> = selectedDate.asLiveData()

    fun setSelectedDate(timeInMillis: Long) {
        val cal = Calendar.getInstance().apply {
            this.timeInMillis = timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        selectedDate.value = cal.timeInMillis
    }

    val dailyExpenses: LiveData<List<Expense>> = selectedDate.flatMapLatest { date ->
        val endOfDay = date + 24 * 60 * 60 * 1000 - 1
        repository.getExpensesForDate(date, endOfDay)
    }.asLiveData()

    val dailyOrders: LiveData<List<OrderWithTotal>> = combine(selectedDate, repository.completedOrders) { date, orders ->
        val endOfDay = date + 24 * 60 * 60 * 1000 - 1
        orders.filter { it.order.startTime in date..endOfDay }
    }.asLiveData()

    val totalExpenses: LiveData<Double> = selectedDate.flatMapLatest { date ->
        val endOfDay = date + 24 * 60 * 60 * 1000 - 1
        repository.getTotalExpensesForDate(date, endOfDay)
    }.asLiveData().map { it ?: 0.0 }

    val allTimeProfit: LiveData<Double> = combine(
        repository.allBills,
        repository.allExpenses
    ) { bills, expenses ->
        val totalSales = bills.sumOf { it.total }
        val totalExp = expenses.sumOf { it.amount }
        totalSales - totalExp
    }.asLiveData()

    fun addExpense(description: String, amount: Double) {
        viewModelScope.launch {
            repository.insertExpense(Expense(description = description, amount = amount, date = selectedDate.value))
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }
}
