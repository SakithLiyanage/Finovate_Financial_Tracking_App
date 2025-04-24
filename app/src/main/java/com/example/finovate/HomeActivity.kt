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
import android.util.Log
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

    private val TAG = "HomeActivity"

    private lateinit var tvUserName: TextView
    private lateinit var tvCurrentBalance: TextView
    private lateinit var tvIncome: TextView
    private lateinit var tvExpenses: TextView
    private lateinit var categoriesRecyclerView: RecyclerView
    private lateinit var transactionsRecyclerView: RecyclerView
    private lateinit var btnAddTransaction: ImageView
    private lateinit var btnNotifications: ImageView
    private lateinit var btnProfile: ImageView
    private lateinit var bottomNavigationView: BottomNavigationView

    private lateinit var tvTotalBudget: TextView
    private lateinit var tvBudgetUsed: TextView
    private lateinit var tvBudgetRemaining: TextView
    private lateinit var budgetProgressBar: android.widget.ProgressBar
    private lateinit var viewBudgetDetails: TextView

    private var currentBalance: Double = 0.0
    private var totalIncome: Double = 0.0
    private var totalExpenses: Double = 0.0
    private var monthlyBudget: Double = 0.00 // Default budget in LKR
    private var totalSpent: Double = 0.0          // Current month expenses

    private lateinit var categoryList: ArrayList<Category>
    private lateinit var transactionList: ArrayList<Transaction>

    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    private val CHANNEL_ID = "finovate_channel"
    private val NOTIFICATION_ID = 101

    private val addTransactionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadUserData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        configureCurrencyFormatter()

        initViews()

        loadUserData()

        createNotificationChannel()

        setupToolbarColorChangeOnScroll()

        checkBudgetStatus()

        setupClickListeners()

        setupBottomNavigation()

        loadUserName()
    }

    private fun configureCurrencyFormatter() {
        currencyFormatter.currency = Currency.getInstance("LKR")
        (currencyFormatter as java.text.DecimalFormat).positivePrefix = "LKR "
        currencyFormatter.maximumFractionDigits = 0
        currencyFormatter.minimumFractionDigits = 0
    }

    private fun initViews() {
        try {
            tvUserName = findViewById(R.id.tvUserName)
            tvCurrentBalance = findViewById(R.id.tvCurrentBalance)
            tvIncome = findViewById(R.id.tvIncome)
            tvExpenses = findViewById(R.id.tvExpenses)
            transactionsRecyclerView = findViewById(R.id.transactionsRecyclerView)
            btnAddTransaction = findViewById(R.id.btnAddTransaction)
            btnProfile = findViewById(R.id.btnProfile)
            bottomNavigationView = findViewById(R.id.bottomNavigationView)

            tvTotalBudget = findViewById(R.id.tvTotalBudget)
            tvBudgetUsed = findViewById(R.id.tvBudgetUsed)
            tvBudgetRemaining = findViewById(R.id.tvBudgetRemaining)
            budgetProgressBar = findViewById(R.id.budgetProgressBar)
            viewBudgetDetails = findViewById(R.id.viewBudgetDetails)

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing views", e)
        }
    }

    private fun loadUserName() {
        val sharedPreferences = getSharedPreferences("finovate_session", MODE_PRIVATE)
        val userEmail = sharedPreferences.getString("logged_in_email", "")

        val profilePrefs = getSharedPreferences("finovate_profiles", MODE_PRIVATE)
        val userName = profilePrefs.getString("${userEmail}_name", "User")

        tvUserName.text = userName ?: "Sakith Liyanage"
    }

    private fun loadUserData() {
        loadTransactions()


        loadBudget()

        calculateBalances()

        calculateCurrentMonthSpending()

        updateUI()
    }

    private fun loadTransactions() {
        try {
            val sharedPrefs = getSharedPreferences("finovate_transactions", MODE_PRIVATE)
            val transactionsJson = sharedPrefs.getString("transactions_data", null)

            transactionList = ArrayList()

            if (!transactionsJson.isNullOrEmpty()) {
                val listType = object : TypeToken<ArrayList<Transaction>>() {}.type
                val loadedTransactions: ArrayList<Transaction> = Gson().fromJson(transactionsJson, listType)

                transactionList.addAll(loadedTransactions)
                Log.d(TAG, "Loaded ${transactionList.size} transactions")
            } else {
                Log.d(TAG, "No transactions found")
            }

            if (transactionList.isEmpty()) {
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

            transactionList.sortByDescending { it.date }

            val recentTransactions = transactionList.take(5)

            val adapter = TransactionAdapter(recentTransactions, currencyFormatter) { transaction ->
                if (transaction.id != "welcome") {
                    val intent = Intent(this, AddTransactionActivity::class.java)
                    intent.putExtra("TRANSACTION_ID", transaction.id)
                    intent.putExtra("IS_EDIT_MODE", true)
                    addTransactionLauncher.launch(intent)
                } else {
                    val intent = Intent(this, AddTransactionActivity::class.java)
                    addTransactionLauncher.launch(intent)
                }
            }

            transactionsRecyclerView.layoutManager = LinearLayoutManager(this)
            transactionsRecyclerView.adapter = adapter
        } catch (e: Exception) {
            Log.e(TAG, "Error loading transactions", e)
            Toast.makeText(this, "Error loading transactions", Toast.LENGTH_SHORT).show()
            transactionList = ArrayList()
        }
    }



    private fun loadBudget() {
        try {
            val sharedPrefs = getSharedPreferences("finovate_budget", Context.MODE_PRIVATE)

            monthlyBudget = sharedPrefs.getFloat("monthly_budget", 0.00f).toDouble()

            Log.d(TAG, "Loaded monthly budget: $monthlyBudget LKR")

            if (!sharedPrefs.contains("monthly_budget")) {
                val editor = sharedPrefs.edit()
                editor.putFloat("monthly_budget", 0.00f)
                editor.apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading budget data", e)
            monthlyBudget = 0.00
        }
    }

    private fun calculateBalances() {
        totalIncome = 0.0
        totalExpenses = 0.0

        for (transaction in transactionList) {
            if (transaction.id == "welcome") continue

            if (transaction.type == TransactionType.INCOME) {
                totalIncome += transaction.amount
            } else {
                totalExpenses += transaction.amount
            }
        }

        currentBalance = totalIncome - totalExpenses
    }

    private fun calculateCurrentMonthSpending() {
        try {
            totalSpent = 0.0

            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH)
            val currentYear = calendar.get(Calendar.YEAR)

            for (transaction in transactionList) {
                if (transaction.id == "welcome") continue

                val transactionCalendar = Calendar.getInstance()
                transactionCalendar.time = transaction.date

                if (transactionCalendar.get(Calendar.MONTH) == currentMonth &&
                    transactionCalendar.get(Calendar.YEAR) == currentYear &&
                    transaction.type == TransactionType.EXPENSE) {

                    totalSpent += transaction.amount
                }
            }

            Log.d(TAG, "Current month spending: $totalSpent LKR out of $monthlyBudget LKR budget")
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating current month spending", e)
        }
    }

    private fun updateUI() {
        try {
            tvCurrentBalance.text = currencyFormatter.format(currentBalance)
            tvIncome.text = currencyFormatter.format(totalIncome)
            tvExpenses.text = currencyFormatter.format(totalExpenses)

            tvTotalBudget.text = currencyFormatter.format(monthlyBudget)
            tvBudgetUsed.text = "${currencyFormatter.format(totalSpent)} used"

            val remaining = monthlyBudget - totalSpent
            tvBudgetRemaining.text = "${currencyFormatter.format(remaining)} remaining"

            val progressPercentage = if (monthlyBudget > 0) (totalSpent / monthlyBudget * 100).toInt() else 0
            budgetProgressBar.progress = progressPercentage

            if (progressPercentage > 90) {
                budgetProgressBar.progressDrawable = getDrawable(R.drawable.progress_bar_red)
            } else if (progressPercentage > 75) {
                budgetProgressBar.progressDrawable = getDrawable(R.drawable.progress_bar_orange)
            } else {
                budgetProgressBar.progressDrawable = getDrawable(R.drawable.progress_bar_blue)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating UI", e)
        }
    }

    private fun setupCategoryDefaults(
        categoryColors: HashMap<String, Int>,
        categoryIcons: HashMap<String, Int>
    ) {
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
            val budgetPercentage = (totalSpent / monthlyBudget) * 100

            if (budgetPercentage >= 90) {
                showBudgetWarningNotification()
            }
            else if (budgetPercentage >= 75) {
                showBudgetWarningDialog()
            }
        }
    }

    private fun showBudgetWarningNotification() {
        try {
            val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert) // Use system icon instead of custom one
                .setContentTitle("Budget Alert!")
                .setContentText("You've used ${(totalSpent / monthlyBudget * 100).toInt()}% of your monthly budget.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            try {
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this,
                    "Budget Alert: You've used ${(totalSpent / monthlyBudget * 100).toInt()}% of your monthly budget.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showBudgetWarningDialog() {
        try {
            MaterialAlertDialogBuilder(this)
                .setTitle("Budget Warning")
                .setMessage("You've used ${(totalSpent / monthlyBudget * 100).toInt()}% of your monthly budget.")
                .setPositiveButton("Adjust Budget") { _, _ ->
                    val intent = Intent(this, BudgetActivity::class.java)
                    startActivity(intent)
                }
                .setNegativeButton("Dismiss") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing budget warning dialog", e)
        }
    }

    private fun setupToolbarColorChangeOnScroll() {
        try {
            val nestedScrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.nestedScrollView)
            val appBarLayout = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)

            val initialColor = ContextCompat.getColor(this, R.color.white)
            val scrolledColor = ContextCompat.getColor(this, R.color.primary_variant)

            appBarLayout.setBackgroundColor(initialColor)

            var isColorChanged = false

            nestedScrollView.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                if (scrollY > 30 && !isColorChanged) {
                    // Animate color change to secondary
                    val colorAnimation = ValueAnimator.ofObject(
                        ArgbEvaluator(),
                        initialColor,
                        scrolledColor
                    )

                    colorAnimation.duration = 300
                    colorAnimation.addUpdateListener { animator ->
                        appBarLayout.setBackgroundColor(animator.animatedValue as Int)
                    }
                    colorAnimation.start()
                    isColorChanged = true
                } else if (scrollY <= 30 && isColorChanged) {

                    val colorAnimation = ValueAnimator.ofObject(
                        ArgbEvaluator(),
                        scrolledColor,
                        initialColor
                    )

                    colorAnimation.duration = 300
                    colorAnimation.addUpdateListener { animator ->
                        appBarLayout.setBackgroundColor(animator.animatedValue as Int)
                    }
                    colorAnimation.start()
                    isColorChanged = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up toolbar color change", e)
        }
    }

    private fun setupClickListeners() {
        try {
            btnAddTransaction.setOnClickListener {
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

            btnProfile.setOnClickListener {
                val intent = Intent(this, ProfileActivity::class.java)
                startActivity(intent)
            }




            val btnViewAllTransactions = findViewById<TextView>(R.id.btnViewAllTransactions)
            btnViewAllTransactions.setOnClickListener {
                val intent = Intent(this, TransactionsActivity::class.java)
                startActivity(intent)
            }

            viewBudgetDetails.setOnClickListener {
                val intent = Intent(this, BudgetActivity::class.java)
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up click listeners", e)
        }
    }

    private fun setupBottomNavigation() {
        try {
            bottomNavigationView.selectedItemId = R.id.nav_home

            bottomNavigationView.setOnItemSelectedListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.nav_home -> {
                        true
                    }
                    R.id.nav_transactions -> {
                        val intent = Intent(this, TransactionsActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    R.id.nav_budget -> {
                        val intent = Intent(this, BudgetActivity::class.java)
                        startActivity(intent)
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

            bottomNavigationView.selectedItemId = R.id.nav_home
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up bottom navigation", e)
        }
    }

    override fun onResume() {
        super.onResume()
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

            tvAmount.text = currencyFormat.format(transaction.amount)

            if (transaction.type == TransactionType.INCOME) {
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.income_green))
                tvAmount.text = "+ ${tvAmount.text}"
            } else {
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.expense_red))
                tvAmount.text = "- ${tvAmount.text}"
            }

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

            categoryBackground.setCardBackgroundColor(
                ContextCompat.getColor(itemView.context, category.colorResId)
            )

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