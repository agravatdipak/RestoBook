package com.office.restobook.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.office.restobook.R
import com.office.restobook.data.local.entities.MenuItem
import com.office.restobook.databinding.ItemMenuItemBinding
import com.office.restobook.databinding.ItemMenuCategoryHeaderBinding

sealed class MenuListItem {
    data class Header(val category: String, var isExpanded: Boolean = true) : MenuListItem()
    data class Item(val menuItem: MenuItem) : MenuListItem()
}

class MenuAdapter(
    private val isSelectionMode: Boolean = false,
    private val onToggleActive: ((MenuItem, Boolean) -> Unit)? = null,
    private val onEdit: ((MenuItem) -> Unit)? = null,
    private val onDelete: ((MenuItem) -> Unit)? = null,
    private val onItemClick: ((MenuItem) -> Unit)? = null
) : ListAdapter<MenuListItem, RecyclerView.ViewHolder>(MenuDiffCallback()) {

    private var originalItems: MutableList<MenuItem> = mutableListOf()
    private val expandedCategories = mutableSetOf<String>()
    private val displayList = mutableListOf<MenuListItem>()

    val allCategories: List<String>
        get() = originalItems.map { it.category }.distinct().sorted()

    fun setMenuItems(items: List<MenuItem>) {
        originalItems = items.toMutableList()
        // Initialize expanded categories if empty
        if (expandedCategories.isEmpty()) {
            expandedCategories.addAll(items.map { it.category }.distinct())
        }
        updateDisplayList()
    }

    private fun updateDisplayList() {
        displayList.clear()
        val grouped = originalItems.groupBy { it.category }
        
        // We should respect the order of categories in originalItems if possible, 
        // or sort categories alphabetically? 
        // The original code sorted categories alphabetically in `allCategories`.
        // Let's stick to alphabetical categories for now, but items within are sorted by list order.
        
        val sortedCategories = grouped.keys.sorted() 
        
        sortedCategories.forEach { category ->
            val items = grouped[category] ?: emptyList()
            // Items are already in the order of originalItems (if originalItems was sorted by sortOrder)
            // But groupBy preserves order of values.
            
            val isExpanded = expandedCategories.contains(category)
            displayList.add(MenuListItem.Header(category, isExpanded))
            if (isExpanded) {
                items.forEach { displayList.add(MenuListItem.Item(it)) }
            }
        }
        submitList(ArrayList(displayList))
    }
    
    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition < 0 || fromPosition >= displayList.size || toPosition < 0 || toPosition >= displayList.size) return false
        
        val fromItem = displayList[fromPosition]
        val toItem = displayList[toPosition]

        // Only allow moving items
        if (fromItem !is MenuListItem.Item || toItem !is MenuListItem.Item) return false
        
        // Only allow moving within same category
        if (fromItem.menuItem.category != toItem.menuItem.category) return false

        // Swap in display list
        java.util.Collections.swap(displayList, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)

        // Update originalItems
        val fromIndex = originalItems.indexOfFirst { it.id == fromItem.menuItem.id }
        val toIndex = originalItems.indexOfFirst { it.id == toItem.menuItem.id }
        
        if (fromIndex != -1 && toIndex != -1) {
            java.util.Collections.swap(originalItems, fromIndex, toIndex)
            
            // Re-assign sort orders based on new index
            // We only need to update the sortOrder of the affected items?
            // Or maybe just keeping the list order is enough, and we assign sortOrder when saving.
        }
        
        return true
    }

    fun getItems(): List<MenuItem> {
        // Return items with updated sortOrder based on their index in the list
        return originalItems.mapIndexed { index, item -> item.copy(sortOrder = index) }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is MenuListItem.Header -> TYPE_HEADER
            is MenuListItem.Item -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = ItemMenuCategoryHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemMenuItemBinding.inflate(inflater, parent, false)
                ItemViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is HeaderViewHolder -> holder.bind(item as MenuListItem.Header)
            is ItemViewHolder -> holder.bind(item as MenuListItem.Item)
        }
    }

    inner class HeaderViewHolder(private val binding: ItemMenuCategoryHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: MenuListItem.Header) {
            binding.categoryTitleText.text = header.category
            binding.expandIcon.setImageResource(if (header.isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
            
            binding.root.setOnClickListener {
                if (expandedCategories.contains(header.category)) {
                    expandedCategories.remove(header.category)
                } else {
                    expandedCategories.add(header.category)
                }
                updateDisplayList()
            }
        }
    }

    inner class ItemViewHolder(private val binding: ItemMenuItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: MenuListItem.Item) {
            val menuItem = entry.menuItem
            binding.itemNameText.text = menuItem.name
            
            if (menuItem.hasPortions) {
                val full = menuItem.priceFull?.let { String.format("F: ₹%.0f", it) } ?: ""
                val half = menuItem.priceHalf?.let { String.format("H: ₹%.0f", it) } ?: ""
                binding.itemPriceText.text = if (full.isNotEmpty() && half.isNotEmpty()) "$full | $half" else "$full$half"
            } else {
                binding.itemPriceText.text = String.format("₹%.0f", menuItem.price)
            }
            
            // Toggle visibility based on mode
            if (isSelectionMode) {
                binding.itemSwitch.visibility = android.view.View.GONE
                binding.editButton.visibility = android.view.View.GONE
                binding.deleteButton.visibility = android.view.View.GONE
            } else {
                binding.itemSwitch.visibility = android.view.View.VISIBLE
                binding.editButton.visibility = android.view.View.VISIBLE
                binding.deleteButton.visibility = android.view.View.VISIBLE
            }

            binding.itemSwitch.isChecked = menuItem.isActive
            binding.itemSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggleActive?.invoke(menuItem, isChecked)
            }

            binding.editButton.setOnClickListener { onEdit?.invoke(menuItem) }
            binding.deleteButton.setOnClickListener { onDelete?.invoke(menuItem) }
            binding.root.setOnClickListener { onItemClick?.invoke(menuItem) }
        }
    }

    class MenuDiffCallback : DiffUtil.ItemCallback<MenuListItem>() {
        override fun areItemsTheSame(oldItem: MenuListItem, newItem: MenuListItem): Boolean {
            return if (oldItem is MenuListItem.Header && newItem is MenuListItem.Header) {
                oldItem.category == newItem.category
            } else if (oldItem is MenuListItem.Item && newItem is MenuListItem.Item) {
                oldItem.menuItem.id == newItem.menuItem.id
            } else false
        }

        override fun areContentsTheSame(oldItem: MenuListItem, newItem: MenuListItem): Boolean = oldItem == newItem
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }
}
