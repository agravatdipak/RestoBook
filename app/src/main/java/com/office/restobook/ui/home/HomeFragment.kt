package com.office.restobook.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.office.restobook.R
import com.office.restobook.RestoApplication
import com.office.restobook.data.local.entities.Order
import com.office.restobook.databinding.DialogNewOrderBinding
import com.office.restobook.databinding.FragmentHomeBinding
import com.office.restobook.ui.adapters.OrderAdapter
import com.office.restobook.viewmodel.HomeViewModel
import com.office.restobook.viewmodel.RestoViewModelFactory
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels {
        RestoViewModelFactory((requireActivity().application as RestoApplication).repository)
    }

    private lateinit var adapter: OrderAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader()
        setupRecyclerView()
        observeViewModel()
        setupListeners()
    }

    private fun setupHeader() {
        val calendar = java.util.Calendar.getInstance()
        val gujaratiLocale = java.util.Locale("gu", "IN")

        val dateSdf = java.text.SimpleDateFormat("dd MMMM yyyy", gujaratiLocale)
        val daySdf = java.text.SimpleDateFormat("EEEE", gujaratiLocale)

        binding.dateText.text = dateSdf.format(calendar.time)
        binding.dayText.text = daySdf.format(calendar.time)
    }

    private fun setupRecyclerView() {
        adapter = OrderAdapter(
            onClick = { order ->
                val action =
                    HomeFragmentDirections.actionHomeFragmentToOrderDetailsFragment(order.id)
                findNavController().navigate(action)
            },
            onLongClick = { order ->
                showDeleteConfirmation(order)
            }
        )
        binding.ordersRecyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.runningOrders.observe(viewLifecycleOwner) { orders ->
            adapter.submitList(orders)
            binding.emptyStateLayout.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
            binding.ordersRecyclerView.visibility =
                if (orders.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun setupListeners() {
        binding.addOrderFab.setOnClickListener {
            showNewOrderDialog()
        }
    }

    private fun showNewOrderDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogNewOrderBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.startOrderButton.setOnClickListener {
            val name = dialogBinding.customerNameEditText.text.toString()
            if (name.isBlank()) {
                dialogBinding.customerNameLayout.error = "Name is mandatory"
                return@setOnClickListener
            }

            val typeId = dialogBinding.orderTypeChipGroup.checkedChipId
            val type = when (typeId) {
                R.id.chip_parcel -> "Parcel"
                R.id.chip_delivery -> "Zomato"
                else -> "Dine-in"
            }

            lifecycleScope.launch {
                val orderId = viewModel.createOrder(name, type)
                dialog.dismiss()
                val action =
                    HomeFragmentDirections.actionHomeFragmentToOrderDetailsFragment(orderId, true)
                findNavController().navigate(action)
            }
        }

        dialog.show()
    }

    private fun showDeleteConfirmation(order: Order) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Order")
            .setMessage("Are you sure you want to delete the order for ${order.customerName}?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteOrder(order)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
