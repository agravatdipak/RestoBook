package com.office.restobook.ui.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.office.restobook.R
import com.office.restobook.RestoApplication
import com.office.restobook.data.local.entities.MenuItem
import com.office.restobook.databinding.DialogBulkQuantityBinding
import com.office.restobook.databinding.DialogPaymentBinding
import com.office.restobook.databinding.DialogSelectPortionBinding
import com.office.restobook.databinding.FragmentMenuManagementBinding
import com.office.restobook.databinding.FragmentOrderDetailsBinding
import com.office.restobook.ui.adapters.MenuAdapter
import com.office.restobook.ui.adapters.OrderItemAdapter
import com.office.restobook.viewmodel.OrderViewModel
import com.office.restobook.viewmodel.RestoViewModelFactory

class OrderDetailsFragment : Fragment() {

    private var _binding: FragmentOrderDetailsBinding? = null
    private val binding get() = _binding!!

    private val args: OrderDetailsFragmentArgs by navArgs()
    private val viewModel: OrderViewModel by viewModels {
        RestoViewModelFactory((requireActivity().application as RestoApplication).repository)
    }

    private lateinit var itemAdapter: OrderItemAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrderDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loadOrder(args.orderId)
        setupRecyclerView()
        observeViewModel()
        setupListeners()

        if (args.autoShowAddItem) {
            showAddItemDialog()
        }
    }

    private fun setupRecyclerView() {
        viewModel.activeMenuItems.observe(viewLifecycleOwner) { menuItems ->
            val menuMap = menuItems.associateBy { it.id }
            itemAdapter = OrderItemAdapter(
                menuItems = menuMap,
                onQuantityChange = { item, delta ->
                    viewModel.updateOrderItemQuantity(item, delta)
                },
                onQuantityClick = { item ->
                    showBulkQuantityDialog(item)
                }
            )
            binding.itemsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            binding.itemsRecyclerView.adapter = itemAdapter

            viewModel.orderItems.value?.let { itemAdapter.submitList(it) }
        }

        // Add scroll listener to hide/show FAB with slide animation
        var isFabVisible = true
        binding.itemsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0 && isFabVisible) {
                    // Scrolling up - slide FAB down and hide
                    binding.addItemButton.animate()
                        .translationY((binding.addItemButton.height + 16).toFloat())
                        .setDuration(200).start()
                    isFabVisible = false
                } else if (dy < 0 && !isFabVisible) {
                    // Scrolling down - slide FAB up and show
//                    binding.addItemButton.show()
                    binding.addItemButton.animate()
                        .translationY(0f)
                        .setDuration(200)
                        .start()
                    isFabVisible = true
                }
            }
        })
    }

    private fun observeViewModel() {
        viewModel.currentOrder.observe(viewLifecycleOwner) { order ->
            order?.let {
                binding.toolbar.title = "Order: ${it.customerName}"
            }
        }

        viewModel.orderItems.observe(viewLifecycleOwner) { items ->
            if (::itemAdapter.isInitialized) {
                itemAdapter.submitList(items)
            }
            calculateTotal(items)
        }
    }

    private fun calculateTotal(items: List<com.office.restobook.data.local.entities.OrderItem>) {
        val total = items.sumOf { it.priceAtTimeOfOrder * it.quantity }
        binding.totalAmountText.text = String.format("₹%.0f", total)
    }

    private fun setupListeners() {
        if (args.isReadOnly) {
            binding.addItemButton.visibility = View.GONE
            binding.payButton.text = "Generate Bill"
            binding.payButton.setOnClickListener {
                generateBill()
            }
        } else {
            binding.addItemButton.setOnClickListener {
                showAddItemDialog()
            }

            binding.payButton.setOnClickListener {
                showPaymentDialog()
            }
        }

        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun generateBill() {
        val order = viewModel.currentOrder.value ?: return
        val items = viewModel.orderItems.value ?: emptyList()
        if (items.isEmpty()) return

        // We need bill details for Pay Mode. Fetch bill.
        viewModel.getBillForOrder(order.id) { bill ->
            showBillDialog(order, items, bill)
        }
    }

    private fun showBillDialog(
        order: com.office.restobook.data.local.entities.Order,
        items: List<com.office.restobook.data.local.entities.OrderItem>,
        bill: com.office.restobook.data.local.entities.Bill?
    ) {
        val menuItems = viewModel.allMenuItems.value ?: emptyList()
        val menuMap = menuItems.associateBy { it.id }

        val billString =
            com.office.restobook.utils.BillGenerator.generateBillString(order, items, bill, menuMap)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
        val textView = android.widget.TextView(requireContext())
        textView.text = billString
        textView.typeface = android.graphics.Typeface.MONOSPACE
        textView.setPadding(32, 32, 32, 32)
        textView.setTextIsSelectable(true)

        dialog.setView(textView)
        dialog.setPositiveButton("Close", null)
        dialog.setNeutralButton("Share") { _, _ ->
            val sendIntent: android.content.Intent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, billString)
                type = "text/plain"
            }
            val shareIntent = android.content.Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }
        dialog.show()
    }

    private fun showAddItemDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = FragmentMenuManagementBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.toolbar.title = "Select Item"
        dialogBinding.addMenuItemFab.visibility = View.GONE

        val menuAdapter = MenuAdapter(
            isSelectionMode = true,
            onItemClick = { menuItem ->
                if (menuItem.hasPortions) {
                    showPortionSelectionDialog(menuItem)
                } else {
                    viewModel.addItemToOrder(menuItem)
                }
                dialog.dismiss()
            }
        )
        dialogBinding.menuRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.menuRecyclerView.adapter = menuAdapter

        viewModel.activeMenuItems.observe(viewLifecycleOwner) {
            menuAdapter.setMenuItems(it)
        }

        dialog.show()
    }

    private fun showPortionSelectionDialog(menuItem: MenuItem) {
        val dialogBinding = DialogSelectPortionBinding.inflate(layoutInflater)
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(
            requireContext(),
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered
        )
            .setView(dialogBinding.root)
            .create()

        dialogBinding.itemNameText.text = menuItem.name
        dialogBinding.priceHalfText.text = String.format("₹%.0f", menuItem.priceHalf ?: 0.0)
        dialogBinding.priceFullText.text = String.format("₹%.0f", menuItem.priceFull ?: 0.0)

        dialogBinding.cardHalf.setOnClickListener {
            viewModel.addItemToOrder(menuItem, "Half")
            dialog.dismiss()
        }

        dialogBinding.cardFull.setOnClickListener {
            viewModel.addItemToOrder(menuItem, "Full")
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showPaymentDialog() {
        val items = viewModel.orderItems.value ?: emptyList()
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), "No items in order", Toast.LENGTH_SHORT).show()
            return
        }

        val total = items.sumOf { it.priceAtTimeOfOrder * it.quantity }
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogPaymentBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.paymentTotalText.text = String.format("₹%.0f", total)

        dialogBinding.confirmPaymentButton.setOnClickListener {
            val modeId = dialogBinding.paymentModeChipGroup.checkedChipId
            val mode = when (modeId) {
                R.id.chip_upi -> "UPI"
                R.id.chip_card -> "Card"
                else -> "Cash"
            }

            viewModel.completePayment(mode, total, 0.0, 0.0, total)
            dialog.dismiss()
            findNavController().navigateUp()
        }

        dialog.show()
    }

    private fun showBulkQuantityDialog(item: com.office.restobook.data.local.entities.OrderItem) {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogBulkQuantityBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val quantities = listOf(2, 3, 4, 5, 6, 7, 8, 9, 10)
        quantities.forEach { q ->
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = q.toString()
                isCheckable = false
                setOnClickListener {
                    viewModel.setOrderItemQuantity(item, q)
                    dialog.dismiss()
                }
            }
            dialogBinding.quantityChipGroup.addView(chip)
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
