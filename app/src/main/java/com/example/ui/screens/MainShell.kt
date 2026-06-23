package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Budget
import com.example.data.model.Habit
import com.example.data.model.Transaction
import com.example.ui.theme.*
import com.example.ui.viewmodel.FinViewModel
import java.text.SimpleDateFormat
import java.util.*

// --- Navigation Tabs ---
enum class AppTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Tổng Quan", Icons.Default.SpaceDashboard),
    TRANSACTIONS("Sổ Chi Tiêu", Icons.Default.ReceiptLong),
    HABITS("Thói Quen", Icons.Default.AutoStories),
    AI_INSIGHTS("Sân Chơi AI", Icons.Default.AutoAwesome)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(viewModel: FinViewModel) {
    var currentTab by remember { mutableStateOf(AppTab.DASHBOARD) }
    
    // Track overlays
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var showAddHabitDialog by remember { mutableStateOf(false) }

    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                AppTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        ),
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentTab == AppTab.TRANSACTIONS) {
                FloatingActionButton(
                    onClick = { showAddTransactionDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("floating_add_transaction")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Thêm Giao Dịch")
                }
            } else if (currentTab == AppTab.HABITS) {
                FloatingActionButton(
                    onClick = { showAddHabitDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("floating_add_habit")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Thêm Thói Quen")
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "tab_transition"
            ) { tab ->
                when (tab) {
                    AppTab.DASHBOARD -> DashboardTab(
                        transactions = transactions,
                        habits = habits,
                        budgets = budgets,
                        onHabitToggle = { viewModel.toggleHabitCompletion(it) },
                        onNavigateToTransactions = { currentTab = AppTab.TRANSACTIONS },
                        onNavigateToHabits = { currentTab = AppTab.HABITS }
                    )
                    AppTab.TRANSACTIONS -> TransactionsTab(
                        transactions = transactions,
                        onDeleteTransaction = { viewModel.deleteTransaction(it) }
                    )
                    AppTab.HABITS -> HabitsTab(
                        habits = habits,
                        onHabitToggle = { viewModel.toggleHabitCompletion(it) },
                        onDeleteHabit = { viewModel.deleteHabit(it) }
                    )
                    AppTab.AI_INSIGHTS -> AiInsightsTab(
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    // Modal Dialogs
    if (showAddTransactionDialog) {
        AddTransactionModal(
            onDismiss = { showAddTransactionDialog = false },
            onSave = { amount, type, category, note ->
                viewModel.addTransaction(amount, type, category, note)
                showAddTransactionDialog = false
            }
        )
    }

    if (showAddHabitDialog) {
        AddHabitModal(
            onDismiss = { showAddHabitDialog = false },
            onSave = { name, icon, color ->
                viewModel.addNewHabit(name, icon, color)
                showAddHabitDialog = false
            }
        )
    }
}

// --- CURRENCY FORMAT HElPER ---
fun formatVnd(amount: Double): String {
    return String.format(Locale("vi", "VN"), "%,.0f đ", amount)
}

// ================= TAB 1: DASHBOARD =================
@Composable
fun DashboardTab(
    transactions: List<Transaction>,
    habits: List<Habit>,
    budgets: List<Budget>,
    onHabitToggle: (Habit) -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToHabits: () -> Unit
) {
    val totalIncome = transactions.filter { it.type == "THU_NHAP" }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == "CHI_TIEU" }.sumOf { it.amount }
    val balance = totalIncome - totalExpense

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FinTrack Pro 🛡️",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Quản lý dòng tiền thông minh tích cực",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = "Wallet",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Net Balance Premium card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("balance_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(SurfaceDark, BaseDark)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "TỔNG SỐ DƯ HIỆN TẠI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = formatVnd(balance),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (balance >= 0) AccentTeal else AccentRed
                        )
                        
                        Divider(color = BorderDark, thickness = 1.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.ArrowCircleUp,
                                        contentDescription = "Income",
                                        tint = AccentGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tổng Thu", fontSize = 11.sp, color = Color.Gray)
                                }
                                Text(
                                    text = formatVnd(totalIncome),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.ArrowCircleDown,
                                        contentDescription = "Expense",
                                        tint = AccentRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tổng Chi", fontSize = 11.sp, color = Color.Gray)
                                }
                                Text(
                                    text = formatVnd(totalExpense),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Budget limits warning / stats
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Theo Dõi Ngân Sách",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (budgets.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Không có định mức ngân sách nào được cấu hình.",
                            modifier = Modifier.padding(16.dp),
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    budgets.forEach { budget ->
                        val percent = if (budget.limitAmount > 0) (budget.spentAmount / budget.limitAmount).toFloat() else 0f
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(budget.category, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    text = "Đã chi ${formatVnd(budget.spentAmount)} / ${formatVnd(budget.limitAmount)}",
                                    fontSize = 12.sp,
                                    color = if (percent > 0.9f) AccentRed else if (percent > 0.7f) AccentOrange else Color.Gray
                                )
                            }
                            LinearProgressIndicator(
                                progress = percent.coerceAtMost(1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = if (percent > 0.9f) AccentRed else if (percent > 0.7f) AccentOrange else AccentTeal,
                                trackColor = BorderDark
                            )
                        }
                    }
                }
            }
        }

        // Quick Daily habit checklist widget
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Thói Quen Mỗi Ngày",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Xem tất cả",
                        fontSize = 12.sp,
                        color = AccentTeal,
                        modifier = Modifier.clickable { onNavigateToHabits() }
                    )
                }

                if (habits.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Bạn chưa thêm thói quen nào.",
                            modifier = Modifier.padding(16.dp),
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        habits.take(3).forEach { habit ->
                            val isCompletedToday = habit.lastCompletedDate > 0L
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                Color(android.graphics.Color.parseColor(habit.color)).copy(alpha = 0.2f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = mapIconNameToVector(habit.icon),
                                            contentDescription = null,
                                            tint = Color(android.graphics.Color.parseColor(habit.color)),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = habit.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Whatshot,
                                                contentDescription = "Streak",
                                                tint = AccentOrange,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Chuỗi: ${habit.currentStreak} ngày", fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { onHabitToggle(habit) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCompletedToday) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Complete Today",
                                        tint = if (isCompletedToday) AccentGreen else Color.Gray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Transactions Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Giao Dịch Gần Đây",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Xem Sổ",
                        fontSize = 12.sp,
                        color = AccentTeal,
                        modifier = Modifier.clickable { onNavigateToTransactions() }
                    )
                }

                if (transactions.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Chưa có cuộc giao dịch nào. Hãy nhấn nút thêm phía dưới!",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        transactions.take(5).forEach { transaction ->
                            TransactionRow(
                                transaction = transaction,
                                onDelete = {} // Read-only dashboard display
                            )
                        }
                    }
                }
            }
        }
    }
}

// ================= TAB 2: TRANSACTIONS SỔ SÁCH =================
@Composable
fun TransactionsTab(
    transactions: List<Transaction>,
    onDeleteTransaction: (Transaction) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("Tất cả") }

    val filteredTransactions = remember(transactions, searchQuery, selectedCategoryFilter) {
        transactions.filter {
            val matchesSearch = it.note.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategoryFilter == "Tất cả" || it.category == selectedCategoryFilter
            matchesSearch && matchesCategory
        }
    }

    val categories = remember(transactions) {
        listOf("Tất cả") + transactions.map { it.category }.distinct()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Sổ Chi Tiêu Giao Dịch",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Search Bar Custom
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Tìm giao dịch, ghi chú...", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_transactions"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = BorderDark
            )
        )

        // Category Filter Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                val isSelected = cat == selectedCategoryFilter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { selectedCategoryFilter = cat }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        cat,
                        color = if (isSelected) Color.White else Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Divider(color = BorderDark, thickness = 1.dp)

        // Transactions List
        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Không tìm thấy giao dịch nào phù hợp.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTransactions, key = { it.id }) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        onDelete = { onDeleteTransaction(transaction) }
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionRow(
    transaction: Transaction,
    onDelete: () -> Unit
) {
    val isExpense = transaction.type == "CHI_TIEU"
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { showDeleteConfirm = !showDeleteConfirm }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isExpense) AccentRed.copy(alpha = 0.15f) else AccentGreen.copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = mapCategoryNameToVector(transaction.category),
                    contentDescription = null,
                    tint = if (isExpense) AccentRed else AccentGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (transaction.note.isNotBlank()) transaction.note else transaction.category,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatDate(transaction.date) + " • " + transaction.category,
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = (if (isExpense) "-" else "+") + formatVnd(transaction.amount),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isExpense) AccentRed else AccentGreen
            )

            AnimatedVisibility(visible = showDeleteConfirm) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Xóa Giao Dịch", tint = AccentRed)
                }
            }
        }
    }
}

// ================= TAB 3: HABITS =================
@Composable
fun HabitsTab(
    habits: List<Habit>,
    onHabitToggle: (Habit) -> Unit,
    onDeleteHabit: (Habit) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Phong Cách Sống & Thói Quen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tích hợp kỷ luật thói quen hàng ngày nâng tầm tài chính lành mạnh",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        if (habits.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("Không có thói quen nào. Nhấp nút '+' để thêm thói quen đầu tiên dấn thân thôi!")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(habits) { habit ->
                    HabitGridCard(
                        habit = habit,
                        onToggle = { onHabitToggle(habit) },
                        onDelete = { onDeleteHabit(habit) }
                    )
                }
            }
        }
    }
}

@Composable
fun HabitGridCard(
    habit: Habit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val isCompletedToday = habit.lastCompletedDate > 0L
    val colorPrimary = Color(android.graphics.Color.parseColor(habit.color))
    var showingActions by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable { showingActions = !showingActions },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(colorPrimary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = mapIconNameToVector(habit.icon),
                        contentDescription = null,
                        tint = colorPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Whatshot,
                        contentDescription = "streak",
                        tint = AccentOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "${habit.currentStreak}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentOrange
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    habit.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Kỷ lục: ${habit.bestStreak} ngày",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (showingActions) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Xóa Thói Quen", tint = AccentRed, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCompletedToday) AccentGreen else colorPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text(
                        text = if (isCompletedToday) "Đã Xong" else "Hoàn Thành",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ================= TAB 4: AI CHAT & IMAGE GENERATOR PLAYGROUND =================
@Composable
fun AiInsightsTab(
    viewModel: FinViewModel
) {
    val aiAdvice by viewModel.aiAdvice.collectAsStateWithLifecycle()
    val isAdviceLoading by viewModel.isAdviceLoading.collectAsStateWithLifecycle()
    
    val imagePromptText by viewModel.imagePromptText.collectAsStateWithLifecycle()
    val generatedImageBase64 by viewModel.generatedImageBase64.collectAsStateWithLifecycle()
    val isImageLoading by viewModel.isImageLoading.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Trí Tuệ Nhân Tạo AI Playground 🧠",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Trải nghiệm phân tích tài chính sâu từ Gemini 3.5 & công cụ tạo ảnh giấc mơ sống động với model Gemini 3.1 Flash Image Preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }

        // --- SUB SECTION 1: FINANCIAL INSIGHTS ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Trợ Lý Nhận Xét Tài Chính AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = AccentTeal
                        )
                        Icon(
                            imageVector = Icons.Default.Recommend,
                            contentDescription = null,
                            tint = AccentTeal,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = "AI sẽ quét toàn bộ số dư và giao dịch thực từ cơ sở dữ liệu để đưa ra phân tích thói quen và giải pháp thích nghi ngắn gọn, tối ưu.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.requestFinancialInsights()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_insight_button"),
                        enabled = !isAdviceLoading
                    ) {
                        if (isAdviceLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Đang tổng thuật hồ sơ...", fontSize = 13.sp)
                        } else {
                            Text("Quét & Phân Tích Bằng AI", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (aiAdvice != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BaseDark, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = aiAdvice!!,
                                color = Color.White,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.testTag("ai_insight_text")
                            )
                        }
                    }
                }
            }
        }

        // --- SUB SECTION 2: PROMPT TO IMAGE GENERATION ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Vẽ Tranh Giấc Mơ Tài Sản TIết Kiệm (AI Image)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AccentOrange
                    )

                    Text(
                        text = "Viết một văn bản mô tả tài sản hoặc mục tiêu tiết kiệm ước mơ của bạn (ví dụ: một chiếc ô tô thể thao, một ngôi nhà ấm cúng phủ tuyết). Hệ thống liên kết API gemini-3.1-flash-image-preview để phác họa ước mơ tiếp thêm động lực tiết kiệm!",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )

                    OutlinedTextField(
                        value = imagePromptText,
                        onValueChange = { viewModel.updateImagePrompt(it) },
                        placeholder = { Text("Mô tả giấc mơ (ví dụ: A modern cozy wood cabin in Switzerland, hyperrealistic)", fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prompt_input_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = BorderDark
                        )
                    )

                    // Presets
                    Text("💡 Gợi ý nhanh ước mơ:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val presets = listOf(
                            "A gorgeous luxury red sport car driving on coastline sunset",
                            "A cute piggy bank filled with gold coins, cartoon icon",
                            "A luxury modern workspace with dual monitor setups looking at mountain view"
                        )
                        items(presets) { preset ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BorderDark)
                                    .clickable { viewModel.updateImagePrompt(preset) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(preset, fontSize = 10.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.generateImageFromPrompt()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_image_button"),
                        enabled = !isImageLoading && imagePromptText.isNotBlank()
                    ) {
                        if (isImageLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Đang hội họa tác phẩm...", fontSize = 13.sp)
                        } else {
                            Text("Hội Họa Tranh Vẽ", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (generatedImageBase64 != null) {
                        val bitmap = remember(generatedImageBase64) {
                            try {
                                val bytes = Base64.decode(generatedImageBase64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        if (bitmap != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BaseDark, RoundedCornerShape(12.dp))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "AI Generated Dream",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .testTag("ai_generated_img")
                                )
                                Text(
                                    "Tác phẩm giấc mơ tiết kiệm của riêng bạn hoàn thành! Đẹp ngỡ ngàng, nỗ lực kiên trì đạt được nhé! 🎉",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        } else {
                            Text("Không thể chuyển đổi tác phẩm nghệ thuật. Hãy thử nhập lại prompt ngắn và dứt khoát.", color = AccentRed, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ================= DIALOGS / MODALS =================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionModal(
    onDismiss: () -> Unit,
    onSave: (amount: Double, type: String, category: String, note: String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("CHI_TIEU") } // "CHI_TIEU" | "THU_NHAP"
    
    val expenseCategories = listOf("Ăn uống", "Giải trí", "Mua sắm", "Di chuyển", "Học tập", "Khác")
    val incomeCategories = listOf("Lương", "Freelance", "Đầu tư", "Quà tặng", "Khác")
    
    var selectedCategory by remember(type) {
        mutableStateOf(if (type == "CHI_TIEU") expenseCategories.first() else incomeCategories.first())
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("add_transaction_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Ghi hồ sơ Giao dịch mới",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Tab Switcher for CHI TIEU / THU NHAP
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BorderDark)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (type == "CHI_TIEU") AccentRed else Color.Transparent)
                            .clickable { type = "CHI_TIEU" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Chi Tiêu", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (type == "THU_NHAP") AccentGreen else Color.Transparent)
                            .clickable { type = "THU_NHAP" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Thu Nhập", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Amount Text Field Input
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) amountText = newValue
                    },
                    label = { Text("Số tiền (VNĐ)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("amount_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = BorderDark
                    )
                )

                // Category Selection Horizontal scroll list
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Chọn Danh Mục:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val currentList = if (type == "CHI_TIEU") expenseCategories else incomeCategories
                        items(currentList) { cat ->
                            val isSelected = cat == selectedCategory
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else BorderDark)
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    cat,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Note Field
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Ghi chú, lịch trình...") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = BorderDark
                    )
                )

                // Save or Cancel Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Bỏ qua", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            if (amount > 0) {
                                onSave(amount, type, selectedCategory, note)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_transaction_button"),
                        enabled = amountText.isNotBlank()
                    ) {
                        Text("Lưu Sổ", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHabitModal(
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, color: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    
    val presetIcons = listOf("DirectionsRun", "AutoStories", "WaterDrop", "MenuBook", "TaskAlt", "LocalCafe")
    var selectedIcon by remember { mutableStateOf(presetIcons.first()) }

    val presetColors = listOf("#4CAF50", "#2196F3", "#9C27B0", "#FF9800", "#FF4F81", "#E91E63")
    var selectedColor by remember { mutableStateOf(presetColors.first()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("add_habit_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Xây Thói quen Kỷ luật Mới",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên thói quen (ví dụ: Thiền 10 phút, Chạy bộ)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("habit_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = BorderDark
                    )
                )

                // Select Icon Preset
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Biểu Tượng:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        presetIcons.forEach { iconName ->
                            val isSelected = iconName == selectedIcon
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else BorderDark)
                                    .clickable { selectedIcon = iconName },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = mapIconNameToVector(iconName),
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Select Color
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tông Màu:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        presetColors.forEach { hex ->
                            val isSelected = hex == selectedColor
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .clickable { selectedColor = hex }
                                    .padding(2.dp)
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.4f))
                                    )
                                }
                            }
                        }
                    }
                }

                // Save / Cancel Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hủy", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(name, selectedIcon, selectedColor)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_habit_button"),
                        enabled = name.isNotBlank()
                    ) {
                        Text("Khởi Tạo", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ================= STATS HELPERS & VECTOR MAPS =================
fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return formatter.format(date)
}

fun mapCategoryNameToVector(categoryName: String): ImageVector {
    return when (categoryName) {
        "Ăn uống" -> Icons.Default.LocalPizza
        "Giải trí" -> Icons.Default.SportsEsports
        "Mua sắm" -> Icons.Default.ShoppingCart
        "Di chuyển" -> Icons.Default.DirectionsCar
        "Học tập" -> Icons.Default.MenuBook
        "Lương" -> Icons.Default.MonetizationOn
        "Freelance" -> Icons.Default.LaptopMac
        "Đầu tư" -> Icons.Default.ShowChart
        "Quà tặng" -> Icons.Default.CardGiftcard
        "Quà" -> Icons.Default.CardGiftcard
        else -> Icons.Default.Payment
    }
}

fun mapIconNameToVector(iconName: String): ImageVector {
    return when (iconName) {
        "DirectionsRun" -> Icons.Default.DirectionsRun
        "AutoStories" -> Icons.Default.AutoStories
        "WaterDrop" -> Icons.Default.WaterDrop
        "MenuBook" -> Icons.Default.MenuBook
        "TaskAlt" -> Icons.Default.TaskAlt
        "LocalCafe" -> Icons.Default.LocalCafe
        else -> Icons.Default.Favorite
    }
}
