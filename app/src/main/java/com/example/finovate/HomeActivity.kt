package com.example.finovate

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class HomeActivity : AppCompatActivity() {

    private lateinit var tvUserName: TextView
    private lateinit var tvCurrentBalance: TextView
    private lateinit var tvIncome: TextView
    private lateinit var tvExpenses: TextView
    private lateinit var tvBudgetAmount: TextView
    private lateinit var tvBudgetUsed: TextView
    private lateinit var tvBudgetRemaining: TextView
    private lateinit var categoriesRecyclerView: RecyclerView
    private lateinit var transactionsRecyclerView: RecyclerView
    private lateinit var btnAddTransaction: ImageView
    private lateinit var btnNotifications: ImageView
    private lateinit var btnProfile: ImageView
    private lateinit var bottomNavigationView: BottomNavigationView

    // Financial data
    private var currentBalance: Double = 0.0
    private var totalIncome: Double = 0.0
    private var totalExpenses: Double = 0.0
    private var monthlyBudget: Double = 120000.00 // Default budget in LKR

    // Lists for RecyclerViews
    private lateinit var categoryList: ArrayList<Category>
    private lateinit var transactionList: ArrayList<Transaction>

    // Custom currency formatter for LKR
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)

    // Date formatter
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    // Notification constants
    private val CHANNEL_ID = "finovate_channel"
    private val NOTIFICATION_ID = 101

    // Activity result handler for adding/editing transactions
    private val addTransactionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Refresh data after add/edit
            loadUserData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Configure currency formatter for LKR
        configureCurrencyFormatter()

        // Initialize views
        initViews()

        // Load user data
        loadUserData()

        // Create notification channel (required for Android 8.0+)
        createNotificationChannel()

        setupToolbarColorChangeOnScroll()

        // Check if budget is exceeded
        checkBudgetStatus()

        // Set up click listeners
        setupClickListeners()

        // Set up navigation
        setupBottomNavigation()

        // Load and display user name
        loadUserName()
    }

    private fun configureCurrencyFormatter() {
        // Customize formatter to use "LKR" as the currency symbol
        currencyFormatter.currency = Currency.getInstance("LKR")
        // Show the currency symbol at the beginning
        (currencyFormatter as java.text.DecimalFormat).positivePrefix = "LKR "
        // Remove the decimal part for LKR as it's typically shown in whole numbers
        currencyFormatter.maximumFractionDigits = 0
        currencyFormatter.minimumFractionDigits = 0
    }

    private fun initViews() {
        tvUserName = findViewById(R.id.tvUserName)
        tvCurrentBalance = findViewById(R.id.tvCurrentBalance)
        tvIncome = findViewById(R.id.tvIncome)
        tvExpenses = findViewById(R.id.tvExpenses)
        tvBudgetAmount = findViewById(R.id.tvBudgetAmount)
        tvBudgetUsed = findViewById(R.id.tvBudgetUsed)
        tvBudgetRemaining = findViewById(R.id.tvBudgetRemaining)
        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView)
        transactionsRecyclerView = findViewById(R.id.transactionsRecyclerView)
        btnAddTransaction = findViewById(R.id.btnAddTransaction)
        btnNotifications = findViewById(R.id.btnNotifications)
        btnProfile = findViewById(R.id.btnProfile)
        bottomNavigationView = findViewById(R.id.bottomNavigationView)
    }

    private fun loadUserName() {
        // Get user email from session
        val sharedPreferences = getSharedPreferences("finovate_session", MODE_PRIVATE)
        val userEmail = sharedPreferences.getString("logged_in_email", "")

        // Load user name from profile data
        val profilePrefs = getSharedPreferences("finovate_profiles", MODE_PRIVATE)
        val userName = profilePrefs.getString("${userEmail}_name", "User")

        // Display user name
        tvUserName.text = userName ?: "Sakith Liyanage"
    }

    private fun loadUserData() {
        // Load transactions from shared preferences
        loadTransactions()

        // Load categories and category data
        loadCategories()

        // Load budget settings
        loadBudget()

        // Calculate balances
        calculateBalances()

        // Update UI with data
        updateUI()
    }

    private fun loadTransactions() {
        try {
            // Load transactions from SharedPreferences
            val sharedPrefs = getSharedPreferences("finovate_transactions", MODE_PRIVATE)
            val transactionsJson = sharedPrefs.getString("transactions_data", null)

            // Initialize the list
            transactionList = ArrayList()

            if (!transactionsJson.isNullOrEmpty()) {
                val listType = object : TypeToken<ArrayList<Transaction>>() {}.type
                val loadedTransactions: ArrayList<Transaction> = Gson().fromJson(transactionsJson, listType)

                // Add all loaded transactions to our list
                transactionList.addAll(loadedTransactions)
            }

            // If no transactions exist yet, display a friendly message
            if (transactionList.isEmpty()) {
                // Create a welcome transaction as a placeholder
                transactionList.add(
                    Transaction(
                        id = "welcome",
                        title = "Welcome, ${tvUserName.text}!",
                        amount = 0.0,
                        type = TransactionType.INCOME,
                        category = "Info",
                        date = Date(),
                        notes = "Add your first transaction to get started"
                    )
                )
            }

            // Sort transactions by date (most recent first)
            transactionList.sortByDescending { it.date }

            // Take only the most recent transactions for the home screen (up to 5)
            val recentTransactions = transactionList.take(5)

            // Set up the transactions recycler view
            val adapter = TransactionAdapter(recentTransactions, currencyFormatter) { transaction ->
                // Handle transaction click - open details/edit
                if (transaction.id != "welcome") {
                    val intent = Intent(this, AddTransactionActivity::class.java)
                    intent.putExtra("TRANSACTION_ID", transaction.id)
                    intent.putExtra("IS_EDIT_MODE", true)
                    addTransactionLauncher.launch(intent)
                } else {
                    // If it's the welcome transaction, go to add transaction screen
                    val intent = Intent(this, AddTransactionActivity::class.java)
                    addTransactionLauncher.launch(intent)
                }
            }

            transactionsRecyclerView.layoutManager = LinearLayoutManager(this)
            transactionsRecyclerView.adapter = adapter
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading transactions", Toast.LENGTH_SHORT).show()
            transactionList = ArrayList()
        }
    }

    private fun loadCategories() {
        try {
            categoryList = ArrayList()

            // Group transactions by category and calculate total for each
            val categoryTotals = HashMap<String, Double>()
            val categoryColors = HashMap<String, Int>()
            val categoryIcons = HashMap<String, Int>()

            // Define default colors and icons for categories
            setupCategoryDefaults(categoryColors, categoryIcons)

            // Process transactions to calculate category totals
            for (transaction in transactionList) {
                if (transaction.type == TransactionType.EXPENSE && transaction.id != "welcome") {
                    val category = transaction.category
                    categoryTotals[category] = (categoryTotals[category] ?: 0.0) + transaction.amount
                }
            }

            // Create category objects from the calculated totals
            categoryTotals.forEach { (category, amount) ->
                categoryList.add(
                    Category(
                        name = category,
                        amount = amount,
                        colorResId = categoryColors[category] ?: R.color.primary,
                        iconResId = categoryIcons[category] ?: R.drawable.ic_category
                    )
                )
            }

            // Sort categories by amount (highest first)
            categoryList.sortByDescending { it.amount }

            // Set up the categories recycler view
            val adapter = CategoryAdapter(categoryList, currencyFormatter) { category ->
                // Handle category click - filter transactions by this category
                val intent = Intent(this, TransactionsActivity::class.java)
                intent.putExtra("FILTER_CATEGORY", category.name)
                startActivity(intent)
            }

            categoriesRecyclerView.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            categoriesRecyclerView.adapter = adapter
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading categories", Toast.LENGTH_SHORT).show()
            categoryList = ArrayList()
        }
    }

    // Helper method to set up default category mappings
    private fun setupCategoryDefaults(
        categoryColors: HashMap<String, Int>,
        categoryIcons: HashMap<String, Int>
    ) {
        // Add mappings for common categories
        categoryColors["Food"] = R.color.category_food
        categoryIcons["Food"] = R.drawable.ic_food

        categoryColors["Bills"] = R.color.category_bills
        categoryIcons["Bills"] = R.drawable.ic_bills

        categoryColors["Transport"] = R.color.category_transport
        categoryIcons["Transport"] = R.drawable.ic_transport

        categoryColors["Shopping"] = R.color.category_shopping
        categoryIcons["Shopping"] = R.drawable.ic_shopping

        categoryColors["Entertainment"] = R.color.category_entertainment
        categoryIcons["Entertainment"] = R.drawable.ic_entertainment

        categoryColors["Health"] = R.color.category_health
        categoryIcons["Health"] = R.drawable.ic_health

        categoryColors["Salary"] = R.color.income_green
        categoryIcons["Salary"] = R.drawable.ic_income

        categoryColors["Freelance"] = R.color.income_green
        categoryIcons["Freelance"] = R.drawable.ic_income

        // Add more category mappings as needed
    }

    private fun loadBudget() {
        // Load budget from SharedPreferences
        val sharedPrefs = getSharedPreferences("finovate_budget", Context.MODE_PRIVATE)

        // Default to 120,000 LKR if not set
        monthlyBudget = sharedPrefs.getFloat("monthly_budget", 120000.00f).toDouble()

        // If this is the first time, save the default budget
        if (!sharedPrefs.contains("monthly_budget")) {
            val editor = sharedPrefs.edit()
            editor.putFloat("monthly_budget", 120000.00f)
            editor.apply()
        }
    }

    private fun calculateBalances() {
        totalIncome = 0.0
        totalExpenses = 0.0

        // Calculate totals from actual transaction list
        for (transaction in transactionList) {
            // Skip the welcome transaction if it exists
            if (transaction.id == "welcome") continue

            if (transaction.type == TransactionType.INCOME) {
                totalIncome += transaction.amount
            } else {
                totalExpenses += transaction.amount
            }
        }

        // Calculate current balance
        currentBalance = totalIncome - totalExpenses
    }

    private fun updateUI() {
        // Update balance texts with LKR currency
        tvCurrentBalance.text = currencyFormatter.format(currentBalance)
        tvIncome.text = currencyFormatter.format(totalIncome)
        tvExpenses.text = currencyFormatter.format(totalExpenses)

        // Update budget information with LKR currency
        tvBudgetAmount.text = currencyFormatter.format(monthlyBudget)
        tvBudgetUsed.text = "${currencyFormatter.format(totalExpenses)} used"
        val remaining = monthlyBudget - totalExpenses
        tvBudgetRemaining.text = "${currencyFormatter.format(remaining)} remaining"

        // Update budget progress bar
        val budgetProgress = findViewById<android.widget.ProgressBar>(R.id.budgetProgressBar)
        val progressPercentage = if (monthlyBudget > 0) (totalExpenses / monthlyBudget * 100).toInt() else 0
        budgetProgress.progress = progressPercentage

        // Set progress bar color based on budget status
        if (progressPercentage > 90) {
            budgetProgress.progressTintList =
                ContextCompat.getColorStateList(this, R.color.expense_red)
        } else if (progressPercentage > 75) {
            budgetProgress.progressTintList =
                ContextCompat.getColorStateList(this, R.color.warning_orange)
        } else {
            budgetProgress.progressTintList =
                ContextCompat.getColorStateList(this, R.color.primary)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val name = getString(R.string.channel_name)
                val descriptionText = getString(R.string.channel_description)
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }

                // Register the channel with the system
                val notificationManager: NotificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun checkBudgetStatus() {
        if (monthlyBudget > 0) {
            val budgetPercentage = (totalExpenses / monthlyBudget) * 100

            // If budget exceeded 90%, show notification
            if (budgetPercentage >= 90) {
                showBudgetWarningNotification()
            }
            // If budget exceeded 75%, show in-app warning
            else if (budgetPercentage >= 75) {
                showBudgetWarningDialog()
            }
        }
    }

    private fun showBudgetWarningNotification() {
        try {
            // Use a default notification icon that's guaranteed to exist
            val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert) // Use system icon instead of custom one
                .setContentTitle("Budget Alert!")
                .setContentText("You've used ${(totalExpenses / monthlyBudget * 100).toInt()}% of your monthly budget.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Use try-catch to handle any notification errors
            try {
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to toast if notification fails
                Toast.makeText(
                    this,
                    "Budget Alert: You've used ${(totalExpenses / monthlyBudget * 100).toInt()}% of your monthly budget.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showBudgetWarningDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Budget Warning")
            .setMessage("You've used ${(totalExpenses / monthlyBudget * 100).toInt()}% of your monthly budget.")
            .setPositiveButton("Adjust Budget") { _, _ ->
                // Open budget settings
                Toast.makeText(this, "Opening budget settings...", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Dismiss") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun setupToolbarColorChangeOnScroll() {
        val nestedScrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.nestedScrollView)
        val appBarLayout = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)

        val initialColor = ContextCompat.getColor(this, R.color.white)
        val scrolledColor = ContextCompat.getColor(this, R.color.primary_variant)

        // Initial color
        appBarLayout.setBackgroundColor(initialColor)

        var isColorChanged = false

        nestedScrollView.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            if (scrollY > 30 && !isColorChanged) {  // User has scrolled down (threshold of 30px)
                // Animate color change to secondary
                val colorAnimation = ValueAnimator.ofObject(
                    ArgbEvaluator(),
                    initialColor,
                    scrolledColor
                )

                colorAnimation.duration = 300 // milliseconds
                colorAnimation.addUpdateListener { animator ->
                    appBarLayout.setBackgroundColor(animator.animatedValue as Int)
                }
                colorAnimation.start()
                isColorChanged = true
            } else if (scrollY <= 30 && isColorChanged) {  // User scrolled back to top
                // Animate color change back to white
                val colorAnimation = ValueAnimator.ofObject(
                    ArgbEvaluator(),
                    scrolledColor,
                    initialColor
                )

                colorAnimation.duration = 300 // milliseconds
                colorAnimation.addUpdateListener { animator ->
                    appBarLayout.setBackgroundColor(animator.animatedValue as Int)
                }
                colorAnimation.start()
                isColorChanged = false
            }
        }
    }

    private fun setupClickListeners() {
        // Add new transaction button in toolbar
        btnAddTransaction.setOnClickListener {
            // Open add transaction activity with animation
            btnAddTransaction.animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(100)
                .withEndAction {
                    btnAddTransaction.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()

                    val intent = Intent(this, AddTransactionActivity::class.java)
                    addTransactionLauncher.launch(intent)
                }
                .start()
        }

        // Profile button
        btnProfile.setOnClickListener {
            Toast.makeText(this, "Opening profile", Toast.LENGTH_SHORT).show()
        }

        // Notifications button
        btnNotifications.setOnClickListener {
            Toast.makeText(this, "Opening notifications", Toast.LENGTH_SHORT).show()
        }

        // View all categories
        val btnViewAllCategories = findViewById<TextView>(R.id.btnViewAllCategories)
        btnViewAllCategories.setOnClickListener {
            // Open categories screen
            Toast.makeText(this, "View all categories", Toast.LENGTH_SHORT).show()
        }

        // View all transactions
        val btnViewAllTransactions = findViewById<TextView>(R.id.btnViewAllTransactions)
        btnViewAllTransactions.setOnClickListener {
            // Open transactions screen
            val intent = Intent(this, TransactionsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    // Already on home, do nothing
                    true
                }
                R.id.nav_transactions -> {
                    val intent = Intent(this, TransactionsActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.nav_budget -> {
                    // Navigate to budget
                    Toast.makeText(this, "Budget", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_reports -> {
                    // Navigate to reports
                    Toast.makeText(this, "Reports", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        // Set Home as selected
        bottomNavigationView.selectedItemId = R.id.nav_home
    }

    // Handle data backup functionality
    private fun backupData() {
        // Code for backing up data to internal storage
        Toast.makeText(this, "Backing up data...", Toast.LENGTH_SHORT).show()
    }

    // Handle data restoration functionality
    private fun restoreData() {
        // Code for restoring data from internal storage
        Toast.makeText(this, "Restoring data...", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        // Reload all data to reflect any changes made in other activities
        loadUserData()
    }
}

class TransactionAdapter(
    private val transactions: List<Transaction>,
    private val currencyFormat: NumberFormat,
    private val onItemClick: (Transaction) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]
        holder.bind(transaction)
    }

    override fun getItemCount() = transactions.size

    inner class TransactionViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTransactionTitle)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvTransactionCategory)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvTransactionAmount)
        private val tvDate: TextView = itemView.findViewById(R.id.tvTransactionDate)

        fun bind(transaction: Transaction) {
            tvTitle.text = transaction.title
            tvCategory.text = transaction.category
            tvDate.text = dateFormat.format(transaction.date)

            // Format amount with LKR currency
            tvAmount.text = currencyFormat.format(transaction.amount)

            if (transaction.type == TransactionType.INCOME) {
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.income_green))
                tvAmount.text = "+ ${tvAmount.text}"
            } else {
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.expense_red))
                tvAmount.text = "- ${tvAmount.text}"
            }

            // Set click listener
            itemView.setOnClickListener {
                onItemClick(transaction)
            }
        }
    }
}

class CategoryAdapter(
    private val categories: List<Category>,
    private val currencyFormat: NumberFormat,
    private val onItemClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_card, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.bind(category)
    }

    override fun getItemCount() = categories.size

    inner class CategoryViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val tvCategoryName: TextView = itemView.findViewById(R.id.tvCategoryName)
        private val tvCategoryAmount: TextView = itemView.findViewById(R.id.tvCategoryAmount)
        private val ivCategoryIcon: android.widget.ImageView = itemView.findViewById(R.id.ivCategoryIcon)
        private val categoryBackground: androidx.cardview.widget.CardView = itemView.findViewById(R.id.categoryCardView)

        fun bind(category: Category) {
            tvCategoryName.text = category.name
            tvCategoryAmount.text = currencyFormat.format(category.amount)
            ivCategoryIcon.setImageResource(category.iconResId)

            // Set background color
            categoryBackground.setCardBackgroundColor(
                ContextCompat.getColor(itemView.context, category.colorResId)
            )

            // Set click listener
            itemView.setOnClickListener {
                onItemClick(category)
            }
        }
    }
}



data class Category(
    val name: String,
    val amount: Double,
    val colorResId: Int,
    val iconResId: Int
)