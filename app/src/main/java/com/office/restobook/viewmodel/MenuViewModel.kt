package com.office.restobook.viewmodel

import androidx.lifecycle.*
import com.office.restobook.data.local.entities.MenuItem
import com.office.restobook.repository.RestoRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MenuViewModel(private val repository: RestoRepository) : ViewModel() {

    val allMenuItems: LiveData<List<MenuItem>> = repository.allMenuItems.asLiveData()

    fun addMenuItem(name: String, price: Double, category: String, priceHalf: Double? = null, priceFull: Double? = null, isVeg: Boolean = true) {
        viewModelScope.launch {
            val hasPortions = priceHalf != null || priceFull != null
            val menuItem = MenuItem(
                name = name, 
                price = price, 
                category = category,
                priceHalf = priceHalf,
                priceFull = priceFull,
                hasPortions = hasPortions,
                isVeg = isVeg
            )
            repository.insertMenuItem(menuItem)
        }
    }

    fun updateMenuItem(menuItem: MenuItem) {
        viewModelScope.launch {
            repository.updateMenuItem(menuItem)
        }
    }

    fun deleteMenuItem(menuItem: MenuItem) {
        viewModelScope.launch {
            repository.deleteMenuItem(menuItem)
        }
    }

    fun updateMenuItemsOrder(items: List<MenuItem>) {
        viewModelScope.launch {
            repository.updateMenuItems(items)
        }
    }

    fun seedPragatiMenu() {
        viewModelScope.launch {
            // Get current items to prevent duplicates
            val currentItems = try {
                 repository.allMenuItems.first()
            } catch (e: Exception) {
                emptyList()
            }
            
            val seedItems = listOf(
                MenuItem(name = "Regular Pavbhaji", category = "PAV BHAJI", priceHalf = 60.0, priceFull = 90.0, hasPortions = true, isVeg = true),
                MenuItem(name = "Butter Pavbhaji", category = "PAV BHAJI", priceHalf = 80.0, priceFull = 110.0, hasPortions = true, isVeg = true),
                MenuItem(name = "Cheese Butter Pavbhaji", category = "PAV BHAJI", priceHalf = 90.0, priceFull = 130.0, hasPortions = true, isVeg = true),
                MenuItem(name = "Green Pavbhaji", category = "PAV BHAJI", priceHalf = 90.0, priceFull = 120.0, hasPortions = true, isVeg = true),
                MenuItem(name = "Cheese Green Pavbhaji", category = "PAV BHAJI", priceHalf = 100.0, priceFull = 140.0, hasPortions = true, isVeg = true),
                MenuItem(name = "Jain Pavbhaji", category = "PAV BHAJI", priceHalf = 70.0, priceFull = 90.0, hasPortions = true, isVeg = true),
                MenuItem(name = "Cheese Kataka Pav", category = "PAV BHAJI", priceHalf = 60.0, priceFull = 90.0, hasPortions = true, isVeg = true),
                
                MenuItem(name = "Regular Pulav", category = "PULAV", priceHalf = 60.0, priceFull = 90.0, hasPortions = true, isVeg = true),
                MenuItem(name = "Butter Pulav", category = "PULAV", priceHalf = 70.0, priceFull = 120.0, hasPortions = true, isVeg = true),
                MenuItem(name = "Cheese Pulav", category = "PULAV", priceHalf = 90.0, priceFull = 130.0, hasPortions = true, isVeg = true),
                MenuItem(name = "Chinese Pulav", category = "PULAV", priceHalf = 70.0, priceFull = 110.0, hasPortions = true, isVeg = true),
                
                MenuItem(name = "Dahi Thepala", category = "BEVERAGE", price = 50.0, isVeg = true),
                MenuItem(name = "Chaash", category = "BEVERAGE", price = 20.0, isVeg = true),
                MenuItem(name = "Masala Papad", category = "BEVERAGE", price = 20.0, isVeg = true),
                MenuItem(name = "Cold Drink", category = "BEVERAGE", price = 20.0, isVeg = true)
            )

            val newItems = seedItems.filter { seedItem ->
                currentItems.none { current ->
                    current.name.equals(seedItem.name, ignoreCase = true) &&
                    current.category.equals(seedItem.category, ignoreCase = true)
                }
            }

            newItems.forEach { repository.insertMenuItem(it) }
        }
    }
}
