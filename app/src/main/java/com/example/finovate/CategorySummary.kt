package com.example.finovate

import androidx.annotation.DrawableRes


data class CategorySummary(
    val name: String,
    val amount: Double,
    @DrawableRes val iconResId: Int,
    val colorResId: Int
)