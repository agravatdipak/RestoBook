package com.office.restobook.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.office.restobook.data.local.entities.Order
import com.office.restobook.databinding.ItemOrderCardBinding
import com.office.restobook.repository.OrderWithTotal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderAdapter(
    private val onClick: ((Order) -> Unit)? = null,
    private val onLongClick: ((Order) -> Unit)? = null,
    private val onBillClick: ((Order) -> Unit)? = null
) : ListAdapter<OrderWithTotal, OrderAdapter.OrderViewHolder>(OrderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding =
            ItemOrderCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OrderViewHolder(private val binding: ItemOrderCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(orderWithTotal: OrderWithTotal) {
            val order = orderWithTotal.order
            binding.customerNameText.text = order.customerName
            binding.orderTypeChip.text = order.orderType

            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            binding.startTimeText.text = sdf.format(Date(order.startTime))

            if (onClick != null) {
                binding.root.setOnClickListener { onClick.invoke(order) }
            } else {
                binding.root.setOnClickListener(null)
                binding.root.isClickable = false
            }

            if (onLongClick != null) {
                binding.root.setOnLongClickListener {
                    onLongClick.invoke(order)
                    true
                }
            } else {
                binding.root.setOnLongClickListener(null)
                binding.root.isLongClickable = false
            }

            binding.paymentModeText.visibility =
                if (orderWithTotal.paymentMode != null) android.view.View.VISIBLE else android.view.View.GONE
            binding.paymentModeText.text = orderWithTotal.paymentMode ?: ""

            binding.billButton.visibility =
                if (onBillClick != null) android.view.View.VISIBLE else android.view.View.GONE
            binding.billButton.setOnClickListener { onBillClick?.invoke(order) }

            binding.totalAmountText.text = String.format("₹%.0f", orderWithTotal.totalAmount)
        }
    }

    class OrderDiffCallback : DiffUtil.ItemCallback<OrderWithTotal>() {
        override fun areItemsTheSame(oldItem: OrderWithTotal, newItem: OrderWithTotal): Boolean =
            oldItem.order.id == newItem.order.id

        override fun areContentsTheSame(oldItem: OrderWithTotal, newItem: OrderWithTotal): Boolean =
            oldItem == newItem
    }
}
