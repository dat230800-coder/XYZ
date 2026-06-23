package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val type: String, // "CHI_TIEU" | "THU_NHAP"
    val category: String, // e.g., "Ăn uống", "Giải trí", "Lương", etc.
    val note: String,
    val date: Long = System.currentTimeMillis(),
    val imageUrl: String? = null // Optional generated/parsed image Uri
) : Serializable

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val icon: String, // Icon identifier name
    val color: String, // Color hex string, e.g., "#FF4F81"
    val frequency: String = "Hàng ngày",
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastCompletedDate: Long = 0L // Epoch day or timestamp
) : Serializable

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String, // unique category
    val limitAmount: Double,
    val spentAmount: Double = 0.0
) : Serializable
