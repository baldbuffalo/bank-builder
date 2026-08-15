package com.bankbuilder.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

private data class Transaction(val id: String, val title: String, val amount: Double, val income: Boolean)

private class Store(context: Context) {
    private val prefs = context.getSharedPreferences("bank_builder", Context.MODE_PRIVATE)
    fun load(): List<Transaction> = runCatching {
        val a = JSONArray(prefs.getString("transactions", "[]"))
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Transaction(o.getString("id"), o.getString("title"), o.getDouble("amount"), o.getBoolean("income"))
        }
    }.getOrDefault(emptyList())
    fun save(items: List<Transaction>) {
        val a = JSONArray()
        items.forEach { t -> a.put(JSONObject().apply { put("id", t.id); put("title", t.title); put("amount", t.amount); put("income", t.income) }) }
        prefs.edit().putString("transactions", a.toString()).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BankBuilderApp(Store(this)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankBuilderApp(store: Store) {
    var transactions by remember { mutableStateOf(store.load()) }
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val income = transactions.filter { it.income }.sumOf { it.amount }
    val spent = transactions.filterNot { it.income }.sumOf { it.amount }
    val balance = income - spent
    val money = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2 }

    fun add(t: Transaction) { transactions = listOf(t) + transactions; store.save(transactions); showAdd = false }
    fun remove(t: Transaction) { transactions = transactions.filterNot { it.id == t.id }; store.save(transactions) }

    MaterialTheme {
        Scaffold(
            topBar = { CenterAlignedTopAppBar(title = { Text(if (tab == 0) "Bank Builder" else "Activity") }, actions = {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
            }) },
            bottomBar = { NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Outlined.Home, "Home") }, label = { Text("Home") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Outlined.List, "Activity") }, label = { Text("Activity") })
            } },
            floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Outlined.Add, "Add") } }
        ) { padding ->
            if (tab == 0) HomeScreen(balance, income, spent, transactions.take(5), money, padding)
            else ActivityScreen(transactions, money, ::remove, padding)
        }
        if (showAdd) AddTransactionDialog(::add) { showAdd = false }
        if (showMenu) AlertDialog(onDismissRequest = { showMenu = false }, title = { Text("Bank Builder") }, text = { Text("Your money stays on this device. No bank connection is required.") }, confirmButton = { TextButton(onClick = { showMenu = false }) { Text("OK") } })
    }
}

@Composable private fun HomeScreen(balance: Double, income: Double, spent: Double, recent: List<Transaction>, money: NumberFormat, padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Build your balance", style = MaterialTheme.typography.bodyLarge) }
        item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(24.dp)) { Text("Total balance", style = MaterialTheme.typography.labelLarge); Text(money.format(balance), style = MaterialTheme.typography.displaySmall); Text(if (balance >= 0) "Keep building your balance" else "Your spending is above your income", style = MaterialTheme.typography.bodyMedium) } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { StatCard("Income", money.format(income), Icons.Outlined.ArrowDownward, Modifier.weight(1f)); StatCard("Spent", money.format(spent), Icons.Outlined.ArrowUpward, Modifier.weight(1f)) } }
        item { Text("Recent activity", style = MaterialTheme.typography.titleLarge) }
        if (recent.isEmpty()) item { EmptyState() } else items(recent, key = { it.id }) { TransactionRow(it, money) }
    }
}

@Composable private fun ActivityScreen(items: List<Transaction>, money: NumberFormat, remove: (Transaction) -> Unit, padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (items.isEmpty()) item { EmptyState() }
        items(items, key = { it.id }) { t -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { TransactionRow(t, money, Modifier.weight(1f)); IconButton(onClick = { remove(t) }) { Icon(Icons.Outlined.Delete, "Delete") } } }
    }
}

@Composable private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) { Card(modifier, RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp)) { Icon(icon, null); Spacer(Modifier.height(10.dp)); Text(title); Text(value, style = MaterialTheme.typography.titleLarge) } } }

@Composable private fun TransactionRow(t: Transaction, money: NumberFormat, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier,
        headlineContent = { Text(t.title) },
        supportingContent = { Text(if (t.income) "Income" else "Expense") },
        leadingContent = { Icon(if (t.income) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward, null) },
        trailingContent = { Text((if (t.income) "+" else "-") + money.format(t.amount)) }
    )
}

@Composable private fun EmptyState() { Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp)) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.AccountBalanceWallet, null); Spacer(Modifier.height(8.dp)); Text("No transactions yet", style = MaterialTheme.typography.titleMedium); Text("Tap + to add your first transaction") } } }

@Composable private fun AddTransactionDialog(add: (Transaction) -> Unit, close: () -> Unit) {
    var title by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var income by remember { mutableStateOf(true) }
    AlertDialog(onDismissRequest = close, title = { Text("Add transaction") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(title, { title = it }, label = { Text("Name") }, singleLine = true)
        OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Income"); Switch(income, { income = it }); Text(if (income) "Income" else "Expense") }
    } }, confirmButton = { TextButton(enabled = title.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0, onClick = { add(Transaction(UUID.randomUUID().toString(), title.trim(), amount.toDouble(), income)) }) { Text("Add") } }, dismissButton = { TextButton(close) { Text("Cancel") } })
}
