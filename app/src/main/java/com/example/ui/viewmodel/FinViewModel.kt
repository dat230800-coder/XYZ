package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.model.Budget
import com.example.data.model.Habit
import com.example.data.model.Transaction
import com.example.data.remote.GeminiService
import com.example.data.repository.FinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FinViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "fintrack_db"
    ).fallbackToDestructiveMigration().build()

    private val repository = FinRepository(db.finDao())

    // --- State Observables ---
    val transactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habits: StateFlow<List<Habit>> = repository.allHabits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgets: StateFlow<List<Budget>> = repository.allBudgets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- AI Interactions States ---
    private val _aiAdvice = MutableStateFlow<String?>(null)
    val aiAdvice: StateFlow<String?> = _aiAdvice.asStateFlow()

    private val _isAdviceLoading = MutableStateFlow(false)
    val isAdviceLoading: StateFlow<Boolean> = _isAdviceLoading.asStateFlow()

    private val _generatedImageBase64 = MutableStateFlow<String?>(null)
    val generatedImageBase64: StateFlow<String?> = _generatedImageBase64.asStateFlow()

    private val _isImageLoading = MutableStateFlow(false)
    val isImageLoading: StateFlow<Boolean> = _isImageLoading.asStateFlow()

    private val _imagePromptText = MutableStateFlow("")
    val imagePromptText: StateFlow<String> = _imagePromptText.asStateFlow()

    init {
        // Pre-populate default habits & budgets if database is empty on first launch
        viewModelScope.launch {
            repository.allHabits.stateIn(this).value.let { list ->
                if (list.isEmpty()) {
                    val defaultHabits = listOf(
                        Habit(name = "Ghi chép chi tiêu", icon = "MenuBook", color = "#4CAF50", frequency = "Hàng ngày"),
                        Habit(name = "Uống 2L nước mỗi ngày", icon = "WaterDrop", color = "#2196F3", frequency = "Hàng ngày"),
                        Habit(name = "Tập thể dục 30 phút", icon = "DirectionsRun", color = "#FF9800", frequency = "Hàng ngày"),
                        Habit(name = "Đọc sách 20 phút", icon = "AutoStories", color = "#9C27B0", frequency = "Hàng ngày")
                    )
                    defaultHabits.forEach { repository.insertHabit(it) }
                }
            }

            repository.allBudgets.stateIn(this).value.let { list ->
                if (list.isEmpty()) {
                    val defaultBudgets = listOf(
                        Budget(category = "Ăn uống", limitAmount = 4500000.0, spentAmount = 0.0),
                        Budget(category = "Giải trí", limitAmount = 1500000.0, spentAmount = 0.0),
                        Budget(category = "Mua sắm", limitAmount = 3000000.0, spentAmount = 0.0),
                        Budget(category = "Di chuyển", limitAmount = 800000.0, spentAmount = 0.0)
                    )
                    defaultBudgets.forEach { repository.insertBudget(it) }
                }
            }
        }
    }

    // --- Action Methods ---

    // Transactions
    fun addTransaction(amount: Double, type: String, category: String, note: String) {
        viewModelScope.launch {
            val t = Transaction(
                amount = amount,
                type = type,
                category = category,
                note = note,
                date = System.currentTimeMillis()
            )
            repository.insertTransaction(t)
            recalculateBudgets()
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
            recalculateBudgets()
        }
    }

    // Habits
    fun toggleHabitCompletion(habit: Habit) {
        viewModelScope.launch {
            val today = System.currentTimeMillis()
            val wasCompletedToday = isCompletedToday(habit.lastCompletedDate)

            val updatedHabit = if (wasCompletedToday) {
                // Untoggle habit (decrease/reset streak based on last, or just reduce streak)
                habit.copy(
                    currentStreak = (habit.currentStreak - 1).coerceAtLeast(0),
                    lastCompletedDate = 0L
                )
            } else {
                // Mark complete today
                val newStreak = habit.currentStreak + 1
                habit.copy(
                    currentStreak = newStreak,
                    bestStreak = maxOf(habit.bestStreak, newStreak),
                    lastCompletedDate = today
                )
            }
            repository.updateHabit(updatedHabit)
        }
    }

    fun addNewHabit(name: String, icon: String, color: String) {
        viewModelScope.launch {
            val h = Habit(name = name, icon = icon, color = color, frequency = "Hàng ngày")
            repository.insertHabit(h)
        }
    }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            repository.deleteHabit(habit)
        }
    }

    // Budgets
    fun addBudget(category: String, limit: Double) {
        viewModelScope.launch {
            val b = Budget(category = category, limitAmount = limit)
            repository.insertBudget(b)
            recalculateBudgets()
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch {
            repository.deleteBudget(budget)
        }
    }

    private suspend fun recalculateBudgets() {
        // Retrieve latest values
        val txs = repository.allTransactions.stateIn(viewModelScope).value
        val bgts = repository.allBudgets.stateIn(viewModelScope).value

        for (budget in bgts) {
            val spent = txs.filter { it.type == "CHI_TIEU" && it.category == budget.category }
                .sumOf { it.amount }
            repository.insertBudget(budget.copy(spentAmount = spent))
        }
    }

    // --- AI Operations ---

    fun requestFinancialInsights() {
        viewModelScope.launch {
            _isAdviceLoading.value = true
            val txList = transactions.value
            val response = GeminiService.generateFinancialInsights(txList)
            _aiAdvice.value = response
            _isAdviceLoading.value = false
        }
    }

    fun updateImagePrompt(prompt: String) {
        _imagePromptText.value = prompt
    }

    fun generateImageFromPrompt() {
        val prompt = _imagePromptText.value
        if (prompt.isBlank()) return

        viewModelScope.launch {
            _isImageLoading.value = true
            _generatedImageBase64.value = null
            val base64 = GeminiService.generateImageFromPrompt(prompt)
            _generatedImageBase64.value = base64
            _isImageLoading.value = false
        }
    }

    // --- Helper Utility functions ---
    private fun isCompletedToday(timestamp: Long): Boolean {
        if (timestamp == 0L) return false
        val todayCalendar = java.util.Calendar.getInstance()
        val completedCalendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return todayCalendar.get(java.util.Calendar.YEAR) == completedCalendar.get(java.util.Calendar.YEAR) &&
               todayCalendar.get(java.util.Calendar.DAY_OF_YEAR) == completedCalendar.get(java.util.Calendar.DAY_OF_YEAR)
    }
}
