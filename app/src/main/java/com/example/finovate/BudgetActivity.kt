package com.example.finovate

import android.content.Intent
import android.os.Bundle
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.NumberFormat
import java.util.*

/**
 * BudgetActivity manages the user's budget settings, including total monthly budget,
 * category-specific budgets, and displays spending progress against these budgets.
 */
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

    // Currency formatter for LKR
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)

    // Budget data
    private var monthlyBudget: Double = 120000.00 // Default 120,000 LKR
    private var totalSpent: Double = 0.0
    private var budgetCategories = ArrayList<BudgetCategory>()
    private var transactionsList = ArrayList<Transaction>()

    // Current month
    private val calendar = Calendar.getInstance()
    private val currentMonth = calendar.get(Calendar.MONTH)
    private val currentYear = calendar.get(Calendar.YEAR)
    private var selectedMonth = currentMonth
    private var selectedYear = currentYear

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget)

        try {
            // Configure currency formatter for LKR
            configureCurrencyFormatter()

            // Initialize views
            initViews()

            // Load data
            loadBudgetData()
            loadTransactions()

            // Calculate spending
            calculateSpending()

            // Set up UI
            setupMonthSelector()
            updateMonthSelectorText()
            setupBudgetCategoriesList()
            updateUI()

            // Set up click listeners
            setupClickListeners()

            // Set up bottom navigation
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
            // Load monthly budget from SharedPreferences
            val sharedPrefs = getSharedPreferences("finovate_budget", MODE_PRIVATE)
            monthlyBudget = sharedPrefs.getFloat("monthly_budget", 120000.00f).toDouble()
            Log.d(TAG, "Loaded monthly budget: $monthlyBudget LKR")

            // Load budget categories
            val budgetCategoriesJson = sharedPrefs.getString("budget_categories", null)

            if (!budgetCategoriesJson.isNullOrEmpty()) {
                val type = object : TypeToken<ArrayList<BudgetCategory>>() {}.type
                budgetCategories = Gson().fromJson(budgetCategoriesJson, type)
                Log.d(TAG, "Loaded ${budgetCategories.size} budget categories")
            } else {
                Log.d(TAG, "No budget categories found, creating defaults")
                // Create default budget categories if none exist
                createDefaultBudgetCategories()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading budget data", e)
            // Create default categories if there's an error
            createDefaultBudgetCategories()
        }
    }

    private fun createDefaultBudgetCategories() {
        budgetCategories = arrayListOf(
            BudgetCategory("Food", 30000.00, R.color.category_food, R.drawable.ic_food),
            BudgetCategory("Bills", 25000.00, R.color.category_bills, R.drawable.ic_bills),
            BudgetCategory("Transport", 15000.00, R.color.category_transport, R.drawable.ic_transport),
            BudgetCategory("Shopping", 20000.00, R.color.category_shopping, R.drawable.ic_shopping),
            BudgetCategory("Entertainment", 15000.00, R.color.category_entertainment, R.drawable.ic_entertainment),
            BudgetCategory("Health", 10000.00, R.color.category_health, R.drawable.ic_health),
            BudgetCategory("Other", 5000.00, R.color.category_other, R.drawable.ic_category)
        )

        // Save default categories
        saveBudgetCategories()
    }

    private fun loadTransactions() {
        try {
            // Load transactions from SharedPreferences
            val sharedPrefs = getSharedPreferences("finovate_transactions", MODE_PRIVATE)
            val transactionsJson = sharedPrefs.getString("transactions_data", null)

            transactionsList.clear()

            if (!transactionsJson.isNullOrEmpty()) {
                val type = object : TypeToken<ArrayList<Transaction>>() {}.type
                val allTransactions: ArrayList<Transaction> = Gson().fromJson(transactionsJson, type)

                // Filter transactions by selected month and year
                transactionsList.addAll(allTransactions.filter {
                    val transactionCalendar = Calendar.getInstance()
                    transactionCalendar.time = it.date
                    transactionCalendar.get(Calendar.MONTH) == selectedMonth &&
                            transactionCalendar.get(Calendar.YEAR) == selectedYear
                })

                Log.d(TAG, "Loaded ${transactionsList.size} transactions for ${selectedMonth+1}/$selectedYear")
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

            // Reset spent amount for all categories
            budgetCategories.forEach { it.spent = 0.0 }

            // Calculate spending for each category
            for (transaction in transactionsList) {
                if (transaction.type == TransactionType.EXPENSE) {
                    totalSpent += transaction.amount

                    // Add to category spending
                    var categoryFound = false
                    for (category in budgetCategories) {
                        if (category.name.equals(transaction.category, ignoreCase = true)) {
                            category.spent += transaction.amount
                            categoryFound = true
                            break
                        }
                    }

                    // If category not found, add to "Other"
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
        val prevMonthBtn = findViewById<ImageView>(R.id.btnPreviousMonth)
        val nextMonthBtn = findViewById<ImageView>(R.id.btnNextMonth)

        prevMonthBtn.setOnClickListener {
            // Go to previous month
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
            // Go to next month (only up to current month)
            if (selectedMonth == currentMonth && selectedYear == currentYear) {
                // Already at current month, do nothing
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
    }

    private fun updateMonthSelectorText() {
        val monthNames = arrayOf("January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December")
        monthSelector.text = "${monthNames[selectedMonth]} $selectedYear"
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
            // Update total budget
            tvTotalBudget.text = currencyFormatter.format(monthlyBudget)

            // Update used and remaining amounts
            tvBudgetUsed.text = "${currencyFormatter.format(totalSpent)} used"
            val remaining = monthlyBudget - totalSpent
            tvBudgetRemaining.text = "${currencyFormatter.format(remaining)} remaining"

            // Update progress bar
            val progressPercentage = if (monthlyBudget > 0) (totalSpent / monthlyBudget * 100).toInt() else 0
            budgetProgressBar.progress = progressPercentage

            // Set progress bar color based on budget status
            if (progressPercentage > 90) {
                budgetProgressBar.progressDrawable = getDrawable(R.drawable.progress_bar_red)
            } else if (progressPercentage > 75) {
                budgetProgressBar.progressDrawable = getDrawable(R.drawable.progress_bar_orange)
            } else {
                budgetProgressBar.progressDrawable = getDrawable(R.drawable.progress_bar_blue)
            }

            // Update RecyclerView
            budgetCategoriesRecyclerView.adapter?.notifyDataSetChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating UI", e)
        }
    }

    private fun setupClickListeners() {
        // Back button
        btnBack.setOnClickListener {
            finish()
        }

        // Add new budget category button
        fabAddBudget.setOnClickListener {
            showAddBudgetDialog()
        }

        // Edit total budget
        val btnEditTotal = findViewById<ImageView>(R.id.btnEditTotalBudget)
        btnEditTotal.setOnClickListener {
            showEditTotalBudgetDialog()
        }
    }

    private fun setupBottomNavigation() {
        // Set Budget as selected
        bottomNavigationView.selectedItemId = R.id.nav_budget

        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, HomeActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.nav_transactions -> {
                    val intent = Intent(this, TransactionsActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.nav_budget -> {
                    // Already on Budget
                    true
                }
                R.id.nav_reports -> {
                    // Navigate to reports (to be implemented)
                    Toast.makeText(this, "Reports coming soon", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    private fun showEditTotalBudgetDialog() {
        try {
            val dialogView = layoutInflater.inflate(R.layout.dialog_edit_total_budget, null)
            val slider = dialogView.findViewById<Slider>(R.id.budgetSlider)
            val amountText = dialogView.findViewById<TextView>(R.id.tvBudgetAmount)

            // Set initial value
            slider.valueTo = 100000f // Max 100,000 LKR
            slider.valueFrom = 0f // Min 0 LKR
            slider.value = monthlyBudget.toFloat()
            amountText.text = currencyFormatter.format(monthlyBudget)

            // Update amount text when slider changes
            slider.addOnChangeListener { _, value, _ ->
                amountText.text = currencyFormatter.format(value.toDouble())
            }

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Edit Total Monthly Budget")
                .setView(dialogView)
                .setPositiveButton("Save") { _, _ ->
                    monthlyBudget = slider.value.toDouble()
                    saveTotalBudget()
                    updateUI()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing edit budget dialog", e)
            Toast.makeText(this, "Error showing dialog", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddBudgetDialog() {
        try {
            val dialogView = layoutInflater.inflate(R.layout.dialog_add_budget_category, null)
            val categoryInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCategoryName)
            val amountInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etBudgetAmount)

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Add Budget Category")
                .setView(dialogView)
                .setPositiveButton("Add") { _, _ ->
                    val categoryName = categoryInput.text.toString()
                    val amountText = amountInput.text.toString()

                    // Validate input
                    if (categoryName.isBlank()) {
                        Toast.makeText(this, "Please enter a category name", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    // Check if category already exists
                    if (budgetCategories.any { it.name.equals(categoryName, ignoreCase = true) }) {
                        Toast.makeText(this, "Category already exists", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    val amount = try {
                        amountText.toDouble()
                    } catch (e: NumberFormatException) {
                        Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    if (amount <= 0) {
                        Toast.makeText(this, "Amount must be greater than 0", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    // Add new category
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
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing add budget dialog", e)
            Toast.makeText(this, "Error showing dialog", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditBudgetDialog(category: BudgetCategory) {
        try {
            val dialogView = layoutInflater.inflate(R.layout.dialog_edit_budget_category, null)
            val slider = dialogView.findViewById<Slider>(R.id.categoryBudgetSlider)
            val amountText = dialogView.findViewById<TextView>(R.id.tvCategoryBudgetAmount)

            // Set initial value
            slider.valueTo = 100000f // Max 100,000 LKR
            slider.valueFrom = 1000f // Min 1,000 LKR (corrected from 100f)
            slider.value = category.budget.toFloat()
            amountText.text = currencyFormatter.format(category.budget)

            // Update amount text when slider changes
            slider.addOnChangeListener { _, value, _ ->
                amountText.text = currencyFormatter.format(value.toDouble())
            }

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Edit ${category.name} Budget")
                .setView(dialogView)
                .setPositiveButton("Save") { _, _ ->
                    category.budget = slider.value.toDouble()
                    saveBudgetCategories()
                    updateUI()
                    Toast.makeText(this, "Budget updated", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Delete") { _, _ ->
                    // Confirm deletion
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Delete ${category.name} Budget?")
                        .setMessage("Are you sure you want to delete this budget category? This cannot be undone.")
                        .setPositiveButton("Delete") { _, _ ->
                            budgetCategories.remove(category)
                            saveBudgetCategories()
                            updateUI()
                            Toast.makeText(this, "Category deleted", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                .show()
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

    // Handle configuration changes
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selectedMonth", selectedMonth)
        outState.putInt("selectedYear", selectedYear)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        selectedMonth = savedInstanceState.getInt("selectedMonth", currentMonth)
        selectedYear = savedInstanceState.getInt("selectedYear", currentYear)

        // Update UI with restored data
        updateMonthSelectorText()
        loadTransactions()
        calculateSpending()
        updateUI()
    }
}