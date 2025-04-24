package com.example.finovate

import java.util.*

data class Transaction(
    val id: String,
    val title: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val date: Date,
    val notes: String? = null
)


enum class TransactionType {
    INCOME,
    EXPENSE
}