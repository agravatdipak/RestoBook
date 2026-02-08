package com.office.restobook.utils

import com.office.restobook.data.local.entities.Bill
import com.office.restobook.data.local.entities.Order
import com.office.restobook.data.local.entities.OrderItem
import com.office.restobook.data.local.entities.MenuItem
import java.text.SimpleDateFormat
import java.util.*

object BillGenerator {
    fun generateBillString(
        order: Order,
        items: List<OrderItem>,
        bill: Bill?,
        menuMap: Map<Long, MenuItem>
    ): String {
        val sb = StringBuilder()
        
        val sdfDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateStr = sdfDate.format(Date(order.startTime))
        val timeStr = sdfTime.format(Date(order.startTime))
        
        sb.append("        Pragati Pavbhaji & Pulav\n")
        sb.append("   04, Kashtabhanjan food corner,\n")
        sb.append("       pasodara patiya, kamrej.\n")
        sb.append("       Contact: 9601949996\n")
        val separator = "-----------------------------------\n"
        sb.append(separator)
        sb.append(" Date: $dateStr   Time: $timeStr\n")
        sb.append(" Bill No: ${order.id}\n")
        sb.append(" Customer: ${order.customerName}\n")
        sb.append(" Pay Mode: ${bill?.paymentMode ?: "Cash"}\n")
        sb.append(separator)
        sb.append(" Item               Qty   Rate  Amt\n")
        sb.append(separator)
        
        var totalQty = 0
        items.forEach { item ->
            val itemName = item.itemName.ifEmpty { (menuMap[item.menuItemId]?.name ?: "Item-${item.id % 1000}") }
            val portionSuffix = if (item.portion != "Regular") " (${item.portion})" else ""
            val fullDisplayName = itemName + portionSuffix
            
            val qty = item.quantity.toString().padEnd(5)
            val rate = String.format("%.0f", item.priceAtTimeOfOrder).padEnd(6)
            val amt = String.format("%.0f", item.priceAtTimeOfOrder * item.quantity)
            
            if (fullDisplayName.length <= 18) {
                sb.append(" ${fullDisplayName.padEnd(19)}$qty$rate$amt\n")
            } else {
                // Line 1: Part of name + columns
                sb.append(" ${fullDisplayName.take(18).padEnd(19)}$qty$rate$amt\n")
                // Subsequent lines: Just the remaining name
                var remaining = fullDisplayName.substring(18)
                while (remaining.isNotEmpty()) {
                    val part = if (remaining.length > 18) remaining.take(18) else remaining
                    sb.append(" $part\n")
                    remaining = if (remaining.length > 18) remaining.substring(18) else ""
                }
            }
            totalQty += item.quantity
        }
        
        val total = items.sumOf { it.priceAtTimeOfOrder * it.quantity }
        
        sb.append(separator)
        sb.append(" Total Qty: $totalQty\n")
        sb.append(" Sub Total:                ${String.format("%.2f", total)}\n")
        sb.append(separator)
        sb.append(" GRAND TOTAL:        Rs. ${String.format("%.2f", total)}\n")
        sb.append(separator)
        sb.append("     Thank You! Visit Again 😊\n")

        return sb.toString()
    }
}
