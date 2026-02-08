package com.office.restobook.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.office.restobook.RestoApplication
import com.office.restobook.databinding.FragmentSalesHistoryBinding
import com.office.restobook.ui.adapters.OrderAdapter
import com.office.restobook.viewmodel.HistoryViewModel
import com.office.restobook.viewmodel.RestoViewModelFactory
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.*
import com.office.restobook.databinding.DialogAddExpenseBinding
import com.office.restobook.ui.adapters.ExpenseAdapter
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope

class SalesHistoryFragment : Fragment() {

    private var _binding: FragmentSalesHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HistoryViewModel by viewModels {
        RestoViewModelFactory((requireActivity().application as RestoApplication).repository)
    }

    private lateinit var orderAdapter: OrderAdapter
    private lateinit var expenseAdapter: ExpenseAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSalesHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabs()
        setupRecyclerViews()
        observeViewModel()
        setupListeners()
        
        // Set initial state for Sales tab (0)
        binding.tabLayout.getTabAt(0)?.select()
        binding.expensesRecyclerView.visibility = View.GONE
        binding.ordersRecyclerView.visibility = View.VISIBLE
        binding.addExpenseFab.hide()
        updateDailyTotal()
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { // Sales
                        binding.expensesRecyclerView.visibility = View.GONE
                        binding.ordersRecyclerView.visibility = View.VISIBLE
                        binding.addExpenseFab.hide()
                        updateEmptyState(viewModel.dailyOrders.value?.isEmpty() ?: true)
                        updateDailyTotal()
                    }
                    1 -> { // Expenses
                        binding.expensesRecyclerView.visibility = View.VISIBLE
                        binding.ordersRecyclerView.visibility = View.GONE
                        binding.addExpenseFab.show()
                        updateEmptyState(viewModel.dailyExpenses.value?.isEmpty() ?: true)
                        updateDailyTotal()
                    }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun setupRecyclerViews() {
        orderAdapter = OrderAdapter(
            onBillClick = { order -> showBillPreview(order) }
        )
        binding.ordersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.ordersRecyclerView.adapter = orderAdapter

        expenseAdapter = ExpenseAdapter(
            onDelete = { expense -> viewModel.deleteExpense(expense) }
        )
        binding.expensesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.expensesRecyclerView.adapter = expenseAdapter
    }

    private fun observeViewModel() {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        
        viewModel.currentSelectedDate.observe(viewLifecycleOwner) { date ->
            binding.selectedDateText.text = sdf.format(Date(date))
        }

        viewModel.dailyOrders.observe(viewLifecycleOwner) { orders ->
            orderAdapter.submitList(orders)
            if (binding.tabLayout.selectedTabPosition == 0) {
                updateEmptyState(orders.isEmpty())
            }
            updateDailyTotal()
        }

        viewModel.dailyExpenses.observe(viewLifecycleOwner) { expenses ->
            expenseAdapter.submitList(expenses)
            if (binding.tabLayout.selectedTabPosition == 1) {
                updateEmptyState(expenses.isEmpty())
            }
            updateDailyTotal()
        }

        viewModel.totalExpenses.observe(viewLifecycleOwner) { updateDailyTotal() }

        viewModel.allTimeProfit.observe(viewLifecycleOwner) { profit ->
            binding.overallProfitText.text = String.format("%s₹%.0f", if (profit >= 0) "" else "-", Math.abs(profit))
            binding.overallProfitText.setTextColor(
                if (profit >= 0) android.graphics.Color.parseColor("#4CAF50") 
                else android.graphics.Color.parseColor("#F44336")
            )
        }
    }

    private fun updateDailyTotal() {
        val selectedTab = binding.tabLayout.selectedTabPosition
        
        when (selectedTab) {
            0 -> { // Sales tab
                val sales = viewModel.dailyOrders.value?.sumOf { it.totalAmount } ?: 0.0
                binding.dailyProfitText.text = String.format("₹%.0f", sales)
                binding.dailyProfitText.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            }
            1 -> { // Expenses tab
                val expenses = viewModel.totalExpenses.value ?: 0.0
                binding.dailyProfitText.text = String.format("₹%.0f", expenses)
                binding.dailyProfitText.setTextColor(android.graphics.Color.parseColor("#F44336"))
            }
        }
    }

    private fun setupListeners() {
        binding.datePickerAction.setOnClickListener {
            showDatePicker()
        }

        binding.addExpenseFab.setOnClickListener {
            showAddExpenseDialog()
        }
    }

    private fun showDatePicker() {
        val builder = MaterialDatePicker.Builder.datePicker()
        builder.setTitleText("Select Date")
        builder.setSelection(viewModel.currentSelectedDate.value ?: MaterialDatePicker.todayInUtcMilliseconds())
        val datePicker = builder.build()

        datePicker.addOnPositiveButtonClickListener { selection: Long? ->
            selection?.let { viewModel.setSelectedDate(it) }
        }

        datePicker.show(parentFragmentManager, "DATE_PICKER")
    }

    private fun showAddExpenseDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogAddExpenseBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.saveButton.setOnClickListener {
            val desc = dialogBinding.descEditText.text.toString()
            val amountStr = dialogBinding.amountEditText.text.toString()
            val amount = amountStr.toDoubleOrNull() ?: 0.0

            if (desc.isBlank() || amount <= 0) {
                Toast.makeText(context, "Valid description and amount required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.addExpense(desc, amount)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showBillPreview(order: com.office.restobook.data.local.entities.Order) {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            val (orderWithItems, bill) = viewModel.getBillData(order.id)
            val menuItems = viewModel.menuItems.value ?: emptyList()
            val menuMap = menuItems.associateBy { it.id }
            
            val billString = com.office.restobook.utils.BillGenerator.generateBillString(
                orderWithItems.order, 
                orderWithItems.items, 
                bill, 
                menuMap
            )

            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Bill Preview")
                .setMessage(billString)
                .setPositiveButton("Close", null)
                .show()
                .apply {
                    // Set monospace font to the message view manually
                    findViewById<android.widget.TextView>(android.R.id.message)?.let {
                        it.typeface = android.graphics.Typeface.MONOSPACE
                        it.textSize = 12f
                    }
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
