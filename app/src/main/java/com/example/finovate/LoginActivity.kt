package com.example.finovate

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var passwordInputLayout: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLoginSubmit: MaterialButton
    private lateinit var tvSignUp: TextView
    private lateinit var btnBack: ImageView
    private lateinit var tvForgotPassword: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Make status bar transparent with light icons
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)

        // Initialize views
        emailInputLayout = findViewById(R.id.emailInputLayout)
        passwordInputLayout = findViewById(R.id.passwordInputLayout)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLoginSubmit = findViewById(R.id.btnLoginSubmit)
        tvSignUp = findViewById(R.id.tvSignUp)
        btnBack = findViewById(R.id.btnBack)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)

        // Set up input validation watchers
        setupInputValidation()

        // Set click listeners
        btnBack.setOnClickListener {
            onBackPressed()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
        }

        btnLoginSubmit.setOnClickListener {
            validateAndLogin()
        }

        tvForgotPassword.setOnClickListener {
            // Implement forgot password feature
            Snackbar.make(
                findViewById(android.R.id.content),
                "Password reset link will be sent to your email",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun setupInputValidation() {
        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString().isNotEmpty()) {
                    emailInputLayout.error = null
                }
            }
        })

        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString().isNotEmpty()) {
                    passwordInputLayout.error = null
                }
            }
        })
    }

    private fun validateAndLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        var isValid = true

        // Validate email
        if (email.isEmpty()) {
            emailInputLayout.error = "Email is required"
            isValid = false
        } else if (!isValidEmail(email)) {
            emailInputLayout.error = "Enter a valid email address"
            isValid = false
        }

        // Validate password
        if (password.isEmpty()) {
            passwordInputLayout.error = "Password is required"
            isValid = false
        }

        if (isValid) {
            // Check if user exists in SharedPreferences
            val sharedPreferences = getSharedPreferences("finovate_users", MODE_PRIVATE)
            val savedPassword = sharedPreferences.getString(email, "")

            if (savedPassword != "" && savedPassword == password) {
                // Login successful

                // Save login session
                val sessionPref = getSharedPreferences("finovate_session", MODE_PRIVATE)
                val editor = sessionPref.edit()
                editor.putBoolean("is_logged_in", true)
                editor.putString("logged_in_email", email)
                editor.apply()

                // Show animation and feedback
                btnLoginSubmit.startAnimation()

                // Navigate to Home Activity
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            } else {
                // Login failed
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "Invalid email or password",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
        return email.matches(emailPattern.toRegex())
    }

    // Extension function for button loading animation
    private fun MaterialButton.startAnimation() {
        this.isEnabled = false
        this.text = "Signing in..."
        this.icon = ContextCompat.getDrawable(context, R.drawable.ic_loading)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}