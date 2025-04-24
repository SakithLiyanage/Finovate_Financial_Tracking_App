package com.example.finovate

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.NumberFormat
import java.util.*


class BudgetActivity : AppCompatActivity() {

    private val TAG = "BudgetActivity"

    private lateinit var tvTotalBudget: TextView
    private lateinit var tvBudgetUsed: TextView
    private lateinit var tvBudgetRemaining: TextView
    private lateinit var budgetProgressBar: android.widget.ProgressBar
    private lateinit var btnBack: ImageView
    private lateinit var budgetCategoriesRecyclerView: RecyclerView
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var fabAddBudget: FloatingActionButton
    private lateinit var monthSelector: TextView

    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)

    private var monthlyBudget: Double = 0.00
    private var totalSpent: Double = 0.0
    private var budgetCategories = ArrayList<BudgetCategory>()
    private var transactionsList = ArrayList<Transaction>()

    private val calendar = Calendar.getInstance()
    private val currentMonth = calendar.get(Calendar.MONTH)
    private val currentYear = calendar.get(Calendar.YEAR)
    private var selectedMonth = currentMonth
    private var selectedYear = currentYear

    private var isUpdatingFromSlider = false
    private var isUpdatingFromText = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget)

        try {
            configureCurrencyFormatter()

            initViews()

            loadBudgetData()
            loadTransactions()

            calculateSpending()

            setupMonthSelector()
            updateMonthSelectorText()
            setupBudgetCategoriesList()
            updateUI()

            setupClickListeners()

            setupBottomNavigation()

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing BudgetActivity", e)
            Toast.makeText(this, "Error loading budget data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun configureCurrencyFormatter() {
        try {
            currencyFormatter.currency = Currency.getInstance("LKR")
            (currencyFormatter as java.text.DecimalFormat).positivePrefix = "LKR "
            currencyFormatter.maximumFractionDigits = 0
            currencyFormatter.minimumFractionDigits = 0
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring currency formatter", e)
        }
    }

    private fun initViews() {
        tvTotalBudget = findViewById(R.id.tvTotalBudget)
        tvBudgetUsed = findViewById(R.id.tvBudgetUsed)
        tvBudgetRemaining = findViewById(R.id.tvBudgetRemaining)
        budgetProgressBar = findViewById(R.id.budgetProgressBar)
        btnBack = findViewById(R.id.btnBack)
        budgetCategoriesRecyclerView = findViewById(R.id.budgetCategoriesRecyclerView)
        bottomNavigationView = findViewById(R.id.bottomNavigationView)
        fabAddBudget = findViewById(R.id.fabAddBudget)
        monthSelector = findViewById(R.id.monthSelector)
    }

    private fun loadBudgetData() {
        try {
            val sharedPrefs = getSharedPreferences("finovate_budget", MODE_PRIVATE)
            monthlyBudget = sharedPrefs.getFloat("monthly_budget", 00.00f).toDouble()
            Log.d(TAG, "Loaded monthly budget: $monthlyBudget LKR")

            val budgetCategoriesJson = sharedPrefs.getString("budget_categories", null)

            if (!budgetCategoriesJson.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<ArrayList<BudgetCategory>>() {}.type
                    budgetCategories = Gson().fromJson(budgetCategoriesJson, type)
                    Log.d(TAG, "Loaded ${budgetCategories.size} budget categories")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing budget categories JSON", e)
                    createDefaultBudgetCategories()
                }
            } else {
                Log.d(TAG, "No budget categories found, creating defaults")
                createDefaultBudgetCategories()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading budget data", e)
            createDefaultBudgetCategories()
        }
    }

    private fun createDefaultBudgetCategories() {
        try {
            budgetCategories = arrayListOf(
                BudgetCategory("Food", 0.00, R.color.category_food, R.drawable.ic_food),
                BudgetCategory("Bills", 0.00, R.color.category_bills, R.drawable.ic_bills),
                BudgetCategory("Transport", 0.00, R.color.category_transport, R.drawable.ic_transport),
                BudgetCategory("Shopping", 0.00, R.color.category_shopping, R.drawable.ic_shopping),
                BudgetCategory("Entertainment", 0.00, R.color.category_entertainment, R.drawable.ic_entertainment),
                BudgetCategory("Health", 0.00, R.color.category_health, R.drawable.ic_health),
                BudgetCategory("Other", 0.00, R.color.category_other, R.drawable.ic_category)
            )

            saveBudgetCategories()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating default categories", e)
            budgetCategories = ArrayList()
        }
    }

    private fun loadTransactions() {
        try {
            val sharedPrefs = getSharedPreferences("finovate_transactions", MODE_PRIVATE)
            val transactionsJson = sharedPrefs.getString("transactions_data", null)

            transactionsList.clear()

            if (!transactionsJson.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<ArrayList<Transaction>>() {}.type
                    val allTransactions: ArrayList<Transaction> = Gson().fromJson(transactionsJson, type)

                    transactionsList.addAll(allTransactions.filter {
                        try {
                            val transactionCalendar = Calendar.getInstance()
                            transactionCalendar.time = it.date
                            transactionCalendar.get(Calendar.MONTH) == selectedMonth &&
                                    transactionCalendar.get(Calendar.YEAR) == selectedYear
                        } catch (e: Exception) {
                            Log.e(TAG, "Error with transaction date", e)
                            false
                        }
                    })

                    Log.d(TAG, "Loaded ${transactionsList.size} transactions for ${selectedMonth+1}/$selectedYear")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing transactions JSON", e)
                }
            } else {
                Log.d(TAG, "No transactions found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading transactions", e)
            Toast.makeText(this, "Error loading transactions", Toast.LENGTH_SHORT).show()
        }
    }

    private fun calculateSpending() {
        try {
            totalSpent = 0.0

            budgetCategories.forEach { it.spent = 0.0 }

            for (transaction in transactionsList) {
                if (transaction.type == TransactionType.EXPENSE) {
                    totalSpent += transaction.amount

                    var categoryFound = false
                    for (category in budgetCategories) {
                        if (category.name.equals(transaction.category, ignoreCase = true)) {
                            category.spent += transaction.amount
                            categoryFound = true
                            break
                        }
                    }

                    if (!categoryFound) {
                        val otherCategory = budgetCategories.find { it.name.equals("Other", ignoreCase = true) }
                        if (otherCategory != null) {
                            otherCategory.spent += transaction.amount
                        } else {
                            // Create "Other" category if it doesn't exist
                            val newOtherCategory = BudgetCategory(
                                "Other",
                                5000.00,
                                R.color.category_other,
                                R.drawable.ic_category,
                                transaction.amount
                            )
                            budgetCategories.add(newOtherCategory)
                            saveBudgetCategories()
                        }
                    }
                }
            }

            Log.d(TAG, "Total spent: $totalSpent LKR out of $monthlyBudget LKR budget")
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating spending", e)
        }
    }

    private fun setupMonthSelector() {
        try {
            val prevMonthBtn = findViewById<ImageView>(R.id.btnPreviousMonth)
            val nextMonthBtn = findViewById<ImageView>(R.id.btnNextMonth)

            prevMonthBtn.setOnClickListener {
                if (selectedMonth == 0) {
                    selectedMonth = 11
                    selectedYear--
                } else {
                    selectedMonth--
                }
                updateMonthSelectorText()
                loadTransactions()
                calculateSpending()
                updateUI()
            }

            nextMonthBtn.setOnClickListener {
                if (selectedMonth == currentMonth && selectedYear == currentYear) {
                    Toast.makeText(this, "Cannot view future months", Toast.LENGTH_SHORT).show()
                } else {
                    if (selectedMonth == 11) {
                        selectedMonth = 0
                        selectedYear++
                    } else {
                        selectedMonth++
                    }
                    updateMonthSelectorText()
                    loadTransactions()
                    calculateSpending()
                    updateUI()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up month selector", e)
        }
    }

    private fun updateMonthSelectorText() {
        try {
            val monthNames = arrayOf("January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December")
            monthSelector.text = "${monthNames[selectedMonth]} $selectedYear"
        } catch (e: Exception) {
            Log.e(TAG, "Error updating month selector text", e)
            // Fallback text
            monthSelector.text = "Current Month"
        }
    }

    private fun setupBudgetCategoriesList() {
        try {
            val adapter = BudgetCategoryAdapter(
                budgetCategories,
                currencyFormatter,
                onEditClick = { category ->
                    showEditBudgetDialog(category)
                }
            )

            budgetCategoriesRecyclerView.layoutManager = LinearLayoutManager(this)
            budgetCategoriesRecyclerView.adapter = adapter
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up budget categories list", e)
        }
    }

    private fun updateUI() {
        try {
            tvTotalBudget.text = currencyFormatter.format(monthlyBudget)

            tvBudgetUsed.text = "${currencyFormatter.format(totalSpent)} used"
            val remaining = monthlyBudget - totalSpent
            tvBudgetRemaining.text = "${currencyFormatter.format(remaining)} remaining"

            val progressPercentage = if (monthlyBudget > 0) (totalSpent / monthlyBudget * 100).toInt() else 0
            val safeProgressPercentage = progressPercentage.coerceIn(0, 100)
            budgetProgressBar.progress = safeProgressPercentage

            try {
                if (progressPercentage > 90) {
                    budgetProgressBar.progressDrawable = getDrawable(R.drawable.progress_bar_red)
                } else if (progressPercentage > 75) {
                    budgetProgressBar.progressDrawable = getDrawable(R.drawable.progress_bar_orange)
                } else {
                    budgetProgressBar.progressDrawable = getDrawable(R.drawable.progress_bar_blue)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting progress bar color", e)
            }

            try {
                budgetCategoriesRecyclerView.adapter?.notifyDataSetChanged()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating RecyclerView", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating UI", e)
        }
    }

    private fun setupClickListeners() {
        try {
            btnBack.setOnClickListener {
                finish()
            }

            fabAddBudget.setOnClickListener {
                showAddBudgetDialog()
            }

            val btnEditTotal = findViewById<ImageView>(R.id.btnEditTotalBudget)
            btnEditTotal.setOnClickListener {
                showEditTotalBudgetDialog()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up click listeners", e)
        }
    }

    private fun setupBottomNavigation() {
        try {
            bottomNavigationView.selectedItemId = R.id.nav_budget

            bottomNavigationView.setOnItemSelectedListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.nav_home -> {
                        val intent = Intent(this, HomeActivity::class.java)
                        startActivity(intent)
                        finish()
                        true
                    }
                    R.id.nav_transactions -> {
                        val intent = Intent(this, TransactionsActivity::class.java)
                        startActivity(intent)
                        finish()
                        true
                    }
                    R.id.nav_budget -> {
                        // Already on Budget
                        true
                    }
                    R.id.nav_reports -> {
                        val intent = Intent(this, ReportsActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    else -> false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up bottom navigation", e)
        }
    }

    private fun showEditTotalBudgetDialog() {
        try {
            Log.d(TAG, "Opening edit total budget dialog")
            val dialogView = layoutInflater.inflate(R.layout.dialog_edit_total_budget, null)

            val slider = dialogView.findViewById<Slider>(R.id.budgetSlider)
            val etAmount = dialogView.findViewById<TextInputEditText>(R.id.etTotalBudgetAmount)

            if (slider == null || etAmount == null) {
                Log.e(TAG, "Failed to find views in dialog_edit_total_budget.xml")
                Toast.makeText(this, "Error loading dialog", Toast.LENGTH_SHORT).show()
                return
            }

            val maxBudgetValue = Math.max(1000000f, (monthlyBudget * 2).toFloat())
            slider.valueTo = maxBudgetValue
            slider.valueFrom = 0f // Allow 0 budget if needed
            slider.value = monthlyBudget.toFloat().coerceIn(0f, maxBudgetValue)

            etAmount.setText(monthlyBudget.toInt().toString())

            slider.addOnChangeListener { _, value, fromUser ->
                if (fromUser && !isUpdatingFromText) {
                    try {
                        isUpdatingFromSlider = true
                        etAmount.setText(value.toInt().toString())
                        isUpdatingFromSlider = false
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating text field from slider", e)
                    }
                }
            }

            etAmount.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (!isUpdatingFromSlider) {
                        try {
                            val text = s.toString()
                            if (text.isNotEmpty()) {
                                val value = text.toFloatOrNull() ?: 0f
                                isUpdatingFromText = true

                                // If value exceeds slider range, update the range
                                if (value > slider.valueTo) {
                                    slider.valueTo = value * 1.5f // Increase range by 50%
                                }

                                // Set the value
                                slider.value = value
                                isUpdatingFromText = false
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing budget amount", e)
                        }
                    }
                }
            })

            val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Edit Total Monthly Budget")
                .setView(dialogView)
                .setPositiveButton("Save", null) // Will replace this below
                .setNegativeButton("Cancel", null)
                .create()

            dialog.show()

            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val budgetText = etAmount.text.toString()
                    if (budgetText.isEmpty()) {
                        Toast.makeText(this, "Please enter a budget amount", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val newBudget = try {
                        budgetText.toDouble()
                    } catch (e: NumberFormatException) {
                        Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    if (newBudget < 0) {
                        Toast.makeText(this, "Budget cannot be negative", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    monthlyBudget = newBudget
                    saveTotalBudget()
                    updateUI()

                    Toast.makeText(this, "Budget updated", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving budget", e)
                    Toast.makeText(this, "Error saving budget", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing edit total budget dialog", e)
            Toast.makeText(this, "Error showing dialog", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddBudgetDialog() {
        try {
            val dialogView = layoutInflater.inflate(R.layout.dialog_add_budget_category, null)
            val categoryInput = dialogView.findViewById<TextInputEditText>(R.id.etCategoryName)
            val amountInput = dialogView.findViewById<TextInputEditText>(R.id.etBudgetAmount)

            val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Add Budget Category")
                .setView(dialogView)
                .setPositiveButton("Add", null) // Will replace this below
                .setNegativeButton("Cancel", null)
                .create()

            dialog.show()

            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val categoryName = categoryInput.text.toString()
                    val amountText = amountInput.text.toString()

                    if (categoryName.isBlank()) {
                        Toast.makeText(this, "Please enter a category name", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    if (budgetCategories.any { it.name.equals(categoryName, ignoreCase = true) }) {
                        Toast.makeText(this, "Category already exists", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val amount = try {
                        amountText.toDouble()
                    } catch (e: NumberFormatException) {
                        Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    if (amount <= 0) {
                        Toast.makeText(this, "Amount must be greater than 0", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    budgetCategories.add(
                        BudgetCategory(
                            categoryName,
                            amount,
                            R.color.category_other,  // Default color
                            R.drawable.ic_category   // Default icon
                        )
                    )

                    saveBudgetCategories()
                    calculateSpending() // Recalculate to include the new category
                    updateUI()

                    Toast.makeText(this, "Category added", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding budget category", e)
                    Toast.makeText(this, "Error adding category", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing add budget dialog", e)
            Toast.makeText(this, "Error showing dialog", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditBudgetDialog(category: BudgetCategory) {
        try {
            Log.d(TAG, "Opening edit budget dialog for category: ${category.name}")
            val dialogView = layoutInflater.inflate(R.layout.dialog_edit_budget_category, null)

            val slider = dialogView.findViewById<Slider>(R.id.categoryBudgetSlider)
            val etAmount = dialogView.findViewById<TextInputEditText>(R.id.etCategoryBudgetAmount)

            if (slider == null || etAmount == null) {
                Log.e(TAG, "Failed to find views in dialog_edit_budget_category.xml")
                Toast.makeText(this, "Error loading dialog", Toast.LENGTH_SHORT).show()
                return
            }

            val maxValue = Math.max(1000000f, (Math.max(category.budget, category.spent) * 2).toFloat())
            slider.valueTo = maxValue
            slider.valueFrom = 1000f  // Min 1,000 LKR

            val initialValue = category.budget.toFloat().coerceIn(slider.valueFrom, slider.valueTo)
            slider.value = initialValue

            etAmount.setText(category.budget.toInt().toString())

            slider.addOnChangeListener { _, value, fromUser ->
                if (fromUser && !isUpdatingFromText) {
                    try {
                        isUpdatingFromSlider = true
                        etAmount.setText(value.toInt().toString())
                        isUpdatingFromSlider = false
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating text field from slider", e)
                    }
                }
            }

            etAmount.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (!isUpdatingFromSlider) {
                        try {
                            val text = s.toString()
                            if (text.isNotEmpty()) {
                                val value = text.toFloatOrNull() ?: slider.valueFrom
                                isUpdatingFromText = true

                                if (value > slider.valueTo) {
                                    slider.valueTo = value * 1.5f // Increase range by 50%
                                }

                                slider.value = value.coerceAtLeast(slider.valueFrom)
                                isUpdatingFromText = false
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing category budget amount", e)
                        }
                    }
                }
            })

            val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Edit ${category.name} Budget")
                .setView(dialogView)
                .setPositiveButton("Save", null) // Will replace this below
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Delete", null) // Will replace this below
                .create()

            dialog.show()

            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val budgetText = etAmount.text.toString()
                    if (budgetText.isEmpty()) {
                        Toast.makeText(this, "Please enter a budget amount", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val newBudget = try {
                        budgetText.toDouble()
                    } catch (e: NumberFormatException) {
                        Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    if (newBudget <= 0) {
                        Toast.makeText(this, "Budget must be greater than 0", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    if (newBudget < category.spent) {
                        Toast.makeText(this, "Warning: Budget is less than spent amount", Toast.LENGTH_LONG).show()
                    }

                    category.budget = newBudget
                    saveBudgetCategories()
                    updateUI()
                    Toast.makeText(this, "Budget updated", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving category budget", e)
                    Toast.makeText(this, "Error saving budget", Toast.LENGTH_SHORT).show()
                }
            }

            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                try {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Delete ${category.name} Budget?")
                        .setMessage("Are you sure you want to delete this budget category? This cannot be undone.")
                        .setPositiveButton("Delete") { _, _ ->
                            try {
                                budgetCategories.remove(category)
                                saveBudgetCategories()
                                updateUI()
                                Toast.makeText(this, "Category deleted", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error deleting category", e)
                                Toast.makeText(this, "Error deleting category", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing delete confirmation", e)
                    Toast.makeText(this, "Error showing confirmation", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing edit category dialog", e)
            Toast.makeText(this, "Error showing dialog", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveTotalBudget() {
        try {
            val sharedPrefs = getSharedPreferences("finovate_budget", MODE_PRIVATE)
            val editor = sharedPrefs.edit()
            editor.putFloat("monthly_budget", monthlyBudget.toFloat())
            editor.apply()
            Log.d(TAG, "Saved total budget: $monthlyBudget LKR")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving total budget", e)
            Toast.makeText(this, "Error saving budget", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBudgetCategories() {
        try {
            val sharedPrefs = getSharedPreferences("finovate_budget", MODE_PRIVATE)
            val editor = sharedPrefs.edit()
            val budgetCategoriesJson = Gson().toJson(budgetCategories)
            editor.putString("budget_categories", budgetCategoriesJson)
            editor.apply()
            Log.d(TAG, "Saved ${budgetCategories.size} budget categories")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving budget categories", e)
            Toast.makeText(this, "Error saving categories", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selectedMonth", selectedMonth)
        outState.putInt("selectedYear", selectedYear)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        selectedMonth = savedInstanceState.getInt("selectedMonth", currentMonth)
        selectedYear = savedInstanceState.getInt("selectedYear", currentYear)

        updateMonthSelectorText()
        loadTransactions()
        calculateSpending()
        updateUI()
    }
}