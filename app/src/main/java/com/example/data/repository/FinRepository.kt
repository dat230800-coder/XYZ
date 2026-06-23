package com.example.data.repository

import com.example.data.local.FinDao
import com.example.data.model.Budget
import com.example.data.model.Habit
import com.example.data.model.Transaction
import kotlinx.coroutines.flow.Flow

class FinRepository(private val finDao: FinDao) {

    val allTransactions: Flow<List<Transaction>> = finDao.getAllTransactions()
    val allHabits: Flow<List<Habit>> = finDao.getAllHabits()
    val allBudgets: Flow<List<Budget>> = finDao.getAllBudgets()

    suspend fun insertTransaction(transaction: Transaction) {
        finDao.insertTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        finDao.deleteTransaction(transaction)
    }

    suspend fun deleteTransactionById(id: Int) {
        finDao.deleteTransactionById(id)
    }

    suspend fun insertHabit(habit: Habit) {
        finDao.insertHabit(habit)
    }

    suspend fun updateHabit(habit: Habit) {
        finDao.updateHabit(habit)
    }

    suspend fun deleteHabit(habit: Habit) {
        finDao.deleteHabit(habit)
    }

    suspend fun insertBudget(budget: Budget) {
        finDao.insertBudget(budget)
    }

    suspend fun deleteBudget(budget: Budget) {
        finDao.deleteBudget(budget)
    }
}
