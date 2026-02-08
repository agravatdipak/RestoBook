package com.office.restobook.ui.menu

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.office.restobook.R
import com.office.restobook.RestoApplication
import com.office.restobook.data.local.entities.MenuItem
import com.office.restobook.databinding.DialogNewMenuItemBinding
import com.office.restobook.databinding.FragmentMenuManagementBinding
import com.office.restobook.ui.adapters.MenuAdapter
import com.office.restobook.viewmodel.MenuViewModel
import com.office.restobook.viewmodel.RestoViewModelFactory

class MenuManagementFragment : Fragment() {

    private var _binding: FragmentMenuManagementBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MenuViewModel by viewModels {
        RestoViewModelFactory((requireActivity().application as RestoApplication).repository)
    }

    private lateinit var adapter: MenuAdapter

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { processMenuImage(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMenuManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()
        setupListeners()
    }

    private fun setupRecyclerView() {
        adapter = MenuAdapter(
            onToggleActive = { item, isChecked ->
                viewModel.updateMenuItem(item.copy(isActive = isChecked))
            },
            onEdit = { item ->
                showAddMenuItemDialog(item)
            },
            onDelete = { item ->
                viewModel.deleteMenuItem(item)
                Toast.makeText(context, "${item.name} deleted", Toast.LENGTH_SHORT).show()
            }
        )
        binding.menuRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.menuRecyclerView.adapter = adapter

        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object :
            androidx.recyclerview.widget.ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                // Disable drag for headers
                if (viewHolder is MenuAdapter.HeaderViewHolder) return makeMovementFlags(0, 0)

                val dragFlags =
                    androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN
                return makeMovementFlags(dragFlags, 0)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return adapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // No swipe support
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                viewModel.updateMenuItemsOrder(adapter.getItems())
            }

            override fun isLongPressDragEnabled(): Boolean {
                // Return true to enable drag on long press
                return true
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.menuRecyclerView)

        // Add scroll listener to hide/show FABs with slide animation
        var areFabsVisible = true
        binding.menuRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0 && areFabsVisible) {
                    // Scrolling up - slide FAB down and hide
                    binding.addMenuItemFab.animate()
                        .translationY((binding.addMenuItemFab.height + 16).toFloat())
                        .setDuration(200).start()
                    areFabsVisible = false
                } else if (dy < 0 && !areFabsVisible) {
                    // Scrolling down - slide FAB up and show
                    binding.addMenuItemFab.animate()
                        .translationY(0f)
                        .setDuration(200)
                        .start()
                    areFabsVisible = true
                }
            }
        })
    }

    private fun observeViewModel() {
        viewModel.allMenuItems.observe(viewLifecycleOwner) { items ->
            adapter.setMenuItems(items)
        }
    }

    private fun setupListeners() {
        binding.toolbar.inflateMenu(R.menu.menu_management_toolbar)
        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_import_menu -> {
                    pickImageLauncher.launch("image/*")
                    true
                }
                R.id.action_seed_menu -> {
                    viewModel.seedPragatiMenu()
                    Toast.makeText(context, "Pragati Menu Seeded", Toast.LENGTH_SHORT).show()
                    true
                }

                else -> false
            }
        }

        binding.addMenuItemFab.setOnClickListener {
            showAddMenuItemDialog()
        }
    }

    private fun showAddMenuItemDialog(existingItem: MenuItem? = null) {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogNewMenuItemBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val categories = adapter.allCategories.toMutableList()
        categories.add(0, "Add New Category")

        val categoryAdapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            categories
        )
        dialogBinding.categoryDropdown.setAdapter(categoryAdapter)

        dialogBinding.categoryDropdown.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                dialogBinding.newCategoryLayout.visibility = View.VISIBLE
            } else {
                dialogBinding.newCategoryLayout.visibility = View.GONE
            }
        }

        val isEditing = existingItem != null
        if (isEditing) {
            dialogBinding.titleText.text = "Edit Menu Item"
            dialogBinding.saveButton.text = "Update"
            dialogBinding.nameEditText.setText(existingItem?.name)
            // Prioritize priceFull if it exists, otherwise use price (for old items without portions)
            dialogBinding.priceFullEditText.setText(
                existingItem?.priceFull?.toString() ?: existingItem?.price?.toString()
            )
            dialogBinding.priceHalfEditText.setText(existingItem?.priceHalf?.toString())

            val catPos = categories.indexOf(existingItem?.category)
            if (catPos != -1) {
                dialogBinding.categoryDropdown.setText(existingItem?.category, false)
            }
        }

        dialogBinding.saveButton.setOnClickListener {
            val name = dialogBinding.nameEditText.text.toString()
            val selectedCategory = dialogBinding.categoryDropdown.text.toString()
            val newCategory = dialogBinding.newCategoryEditText.text.toString()

            val finalCategory = if (selectedCategory == "Add New Category") {
                if (newCategory.isBlank()) {
                    Toast.makeText(context, "Please enter new category name", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }
                newCategory
            } else {
                selectedCategory
            }

            val priceFullStr = dialogBinding.priceFullEditText.text.toString()
            val priceHalfStr = dialogBinding.priceHalfEditText.text.toString()

            if (name.isBlank() || finalCategory.isBlank() || priceFullStr.isBlank()) {
                Toast.makeText(
                    context,
                    "Name, Category, and Full Price are required",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val priceFull = priceFullStr.toDoubleOrNull() ?: 0.0
            val priceHalf = priceHalfStr.toDoubleOrNull()
            val hasPortions = priceHalf != null

            if (isEditing) {
                viewModel.updateMenuItem(
                    existingItem!!.copy(
                        name = name,
                        price = if (hasPortions) 0.0 else priceFull, // If has portions, base price is 0, use priceFull/priceHalf
                        category = finalCategory,
                        priceHalf = priceHalf,
                        priceFull = if (hasPortions) priceFull else null, // If has portions, priceFull is the full price
                        hasPortions = hasPortions
                    )
                )
            } else {
                viewModel.addMenuItem(
                    name,
                    if (hasPortions) 0.0 else priceFull,
                    finalCategory,
                    priceHalf,
                    if (hasPortions) priceFull else null
                )
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun processMenuImage(uri: Uri) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromFilePath(requireContext(), uri)
            Toast.makeText(context, "Processing menu...", Toast.LENGTH_SHORT).show()
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    parseAndImportMenu(visionText.text)
                }
                .addOnFailureListener { e ->
                    Log.e("OCR", "Error: ${e.message}")
                    Toast.makeText(context, "Failed to recognize text", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e("OCR", "Error: ${e.message}")
        }
    }

    private fun parseAndImportMenu(visionText: String) {
        val lines = visionText.split("\n")
        var currentCategory = "General"

        // Simple logic to find items and prices
        val priceRegex = Regex("""(\d+)""")

        // We can try to detect portion headers
        var currentCategoryHasPortions = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Detection: If it matches a known category or is all caps
            val isKnownCategory = trimmed.equals("PAV BHAJI", ignoreCase = true) ||
                    trimmed.equals("PAVBHẠJI", ignoreCase = true) ||
                    trimmed.equals("PULAV", ignoreCase = true) ||
                    trimmed.equals("BEVERAGE", ignoreCase = true)

            if (isKnownCategory || (trimmed.toUpperCase() == trimmed && trimmed.length > 3 && !trimmed.contains(
                    "HALF"
                ) && !trimmed.contains("FULL"))
            ) {
                currentCategory = trimmed.capitalize()
                currentCategoryHasPortions = false // Reset for new category
                continue
            }

            // Detect if this line is "HALF FULL" header
            if (trimmed.contains("HALF", ignoreCase = true) && trimmed.contains(
                    "FULL",
                    ignoreCase = true
                )
            ) {
                currentCategoryHasPortions = true
                continue
            }

            val matches = priceRegex.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                val namePart = trimmed.replace(priceRegex, "").trim()
                if (namePart.length > 2) {
                    val prices = matches.map { it.value.toDouble() }
                    if (prices.size >= 2) {
                        viewModel.addMenuItem(namePart, 0.0, currentCategory, prices[0], prices[1])
                    } else if (prices.size == 1) {
                        // If we saw "HALF FULL" recently, maybe we should assume it's one of them?
                        // For now, if only one price, treat as standard
                        viewModel.addMenuItem(namePart, prices[0], currentCategory)
                    }
                }
            }
        }
        Toast.makeText(context, "Menu imported successfully", Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
