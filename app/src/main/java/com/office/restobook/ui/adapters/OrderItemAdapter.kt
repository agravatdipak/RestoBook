package com.office.restobook.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.office.restobook.data.local.entities.MenuItem
import com.office.restobook.data.local.entities.OrderItem
import com.office.restobook.databinding.ItemOrderItemBinding

class OrderItemAdapter(
    private val menuItems: Map<Long, MenuItem>,
    private val onQuantityChange: (OrderItem, Int) -> Unit,
    private val onQuantityClick: (OrderItem) -> Unit
) : ListAdapter<OrderItem, OrderItemAdapter.OrderItemViewHolder>(OrderItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderItemViewHolder {
        val binding = ItemOrderItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OrderItemViewHolder(private val binding: ItemOrderItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(orderItem: OrderItem) {
            val menuItem = menuItems[orderItem.menuItemId]
            val itemName = menuItem?.name ?: "Unknown Item"
            binding.itemNameText.text = if (orderItem.portion != "Regular") "$itemName (${orderItem.portion})" else itemName
            binding.itemPriceText.text = String.format("₹%.0f x %d", orderItem.priceAtTimeOfOrder, orderItem.quantity)
            binding.itemTotalText.text = String.format("₹%.0f", orderItem.priceAtTimeOfOrder * orderItem.quantity)
            binding.quantityText.text = orderItem.quantity.toString()

            binding.plusButton.setOnClickListener { onQuantityChange(orderItem, 1) }
            binding.minusButton.setOnClickListener { onQuantityChange(orderItem, -1) }
            binding.quantityText.setOnClickListener { onQuantityClick(orderItem) }
        }
    }

    class OrderItemDiffCallback : DiffUtil.ItemCallback<OrderItem>() {
        override fun areItemsTheSame(oldItem: OrderItem, newItem: OrderItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: OrderItem, newItem: OrderItem): Boolean = oldItem == newItem
    }
}
