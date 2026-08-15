package com.bankbuilder.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

private data class Transaction(val id: String, val title: String, val amount: Double, val income: Boolean)

private data class Bank(val id: String, val name: String, val shortName: String)

private val supportedBanks = listOf(
    Bank("sbi", "State Bank of India", "SBI"),
    Bank("hdfc", "HDFC Bank", "HDFC"),
    Bank("icici", "ICICI Bank", "ICICI"),
    Bank("axis", "Axis Bank", "AXIS"),
    Bank("kotak", "Kotak Mahindra Bank", "KOTAK"),
    Bank("indusind", "IndusInd Bank", "INDUSIND"),
    Bank("yes", "YES BANK", "YES"),
    Bank("idfc", "IDFC FIRST Bank", "IDFC"),
    Bank("federal", "Federal Bank", "FEDERAL"),
    Bank("au", "AU Small Finance Bank", "AU")
)

/**
 * The production provider will supply this URL after Bank Builder is registered
 * with an open-banking/Account Aggregator integration. Never put bank passwords,
 * PINs, or OTPs into Bank Builder. Authentication happens at the provider in
 * the Chrome Custom Tab, then the provider redirects to bankbuilder://connect/callback.
 */
private const val AUTHORIZATION_ENDPOINT = "https://connect.bankbuilder.app/authorize"

private class Store(context: Context) {
    private val prefs = context.getSharedPreferences("bank_builder", Context.MODE_PRIVATE)

    fun loadTransactions(): List<Transaction> = runCatching {
        val a = JSONArray(prefs.getString("transactions", "[]"))
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Transaction(o.getString("id"), o.getString("title"), o.getDouble("amount"), o.getBoolean("income"))
        }
    }.getOrDefault(emptyList())

    fun saveTransactions(items: List<Transaction>) {
        val a = JSONArray()
        items.forEach { t ->
            a.put(JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("amount", t.amount)
                put("income", t.income)
            })
        }
        prefs.edit().putString("transactions", a.toString()).apply()
    }

    fun connectedBank(): String? = prefs.getString("connected_bank", null)

    fun saveConnectedBank(bank: Bank) {
        prefs.edit()
            .putString("connected_bank", bank.id)
            .putString("connected_bank_name", bank.name)
            .apply()
    }

    fun connectedBankName(): String? = prefs.getString("connected_bank_name", null)
}

class MainActivity : ComponentActivity() {
    private lateinit var store: Store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        handleConnectionCallback(intent)
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleConnectionCallback(intent)
        render()
    }

    private fun handleConnectionCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "bankbuilder" || uri.host != "connect" || uri.path != "/callback") return

        val bankId = uri.getQueryParameter("bank") ?: return
        val success = uri.getQueryParameter("status") == "success" || uri.getQueryParameter("code") != null
        if (success) {
            supportedBanks.firstOrNull { it.id == bankId }?.let(store::saveConnectedBank)
        }
        intent.replaceExtras(Bundle())
        intent.data = null
    }

    private fun render() {
        setContent { BankBuilderApp(store, ::openBankAuthorization) }
    }

    private fun openBankAuthorization(bank: Bank) {
        val authorizationUri = Uri.parse(AUTHORIZATION_ENDPOINT).buildUpon()
            .appendQueryParameter("bank", bank.id)
            .appendQueryParameter("redirect_uri", "bankbuilder://connect/callback")
            .appendQueryParameter("state", UUID.randomUUID().toString())
            .build()

        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(this, authorizationUri)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankBuilderApp(store: Store, openBankAuthorization: (Bank) -> Unit) {
    var connectedBankName by remember { mutableStateOf(store.connectedBankName()) }
    var transactions by remember { mutableStateOf(store.loadTransactions()) }
    var setupComplete by remember { mutableStateOf(connectedBankName != null) }
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    if (!setupComplete) {
        BankSetupScreen(openBankAuthorization = openBankAuthorization)
        return
    }

    val income = transactions.filter { it.income }.sumOf { it.amount }
    val spent = transactions.filterNot { it.income }.sumOf { it.amount }
    val balance = income - spent
    val money = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2 }

    fun add(t: Transaction) {
        transactions = listOf(t) + transactions
        store.saveTransactions(transactions)
        showAdd = false
    }

    fun remove(t: Transaction) {
        transactions = transactions.filterNot { it.id == t.id }
        store.saveTransactions(transactions)
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(if (tab == 0) "Bank Builder" else "Activity") },
                    actions = { IconButton(onClick = { showMenu = true }) { Icon(Icons.Outlined.MoreVert, "More") } }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Outlined.Home, "Home") }, label = { Text("Home") })
                    NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Outlined.List, "Activity") }, label = { Text("Activity") })
                }
            },
            floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Outlined.Add, "Add") } }
        ) { padding ->
            if (tab == 0) {
                HomeScreen(balance, income, spent, connectedBankName, transactions.take(5), money, padding)
            } else {
                ActivityScreen(transactions, money, ::remove, padding)
            }
        }

        if (showAdd) AddTransactionDialog(::add) { showAdd = false }
        if (showMenu) {
            AlertDialog(
                onDismissRequest = { showMenu = false },
                title = { Text("Connected bank") },
                text = { Text(connectedBankName ?: "No bank connected") },
                confirmButton = { TextButton(onClick = { showMenu = false }) { Text("OK") } }
            )
        }
    }
}

@Composable
private fun BankSetupScreen(openBankAuthorization: (Bank) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = supportedBanks.filter { it.name.contains(query, ignoreCase = true) || it.shortName.contains(query, ignoreCase = true) }

    Scaffold { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null)
                Spacer(Modifier.height(12.dp))
                Text("Connect your bank", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text("Choose your bank. Bank Builder will open the real authorization page in Chrome so your banking credentials stay with the bank/provider.")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search banks") },
                    singleLine = true
                )
            }
            items(filtered, key = { it.id }) { bank ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    ListItem(
                        leadingContent = { Icon(Icons.Outlined.AccountBalance, contentDescription = null) },
                        headlineContent = { Text(bank.name) },
                        supportingContent = { Text("Secure connection") },
                        trailingContent = { Button(onClick = { openBankAuthorization(bank) }) { Text("Connect") } }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    balance: Double,
    income: Double,
    spent: Double,
    connectedBankName: String?,
    recent: List<Transaction>,
    money: NumberFormat,
    padding: PaddingValues
) {
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Build your balance", style = MaterialTheme.typography.bodyLarge)
            if (connectedBankName != null) {
                Text("Connected to $connectedBankName", style = MaterialTheme.typography.labelLarge)
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(24.dp)) {
                    Text("Total balance", style = MaterialTheme.typography.labelLarge)
                    Text(money.format(balance), style = MaterialTheme.typography.displaySmall)
                    Text(if (balance >= 0) "Keep building your balance" else "Your spending is above your income")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Income", money.format(income), Icons.Outlined.ArrowDownward, Modifier.weight(1f))
                StatCard("Spent", money.format(spent), Icons.Outlined.ArrowUpward, Modifier.weight(1f))
            }
        }
        item { Text("Recent activity", style = MaterialTheme.typography.titleLarge) }
        if (recent.isEmpty()) item { EmptyState() } else items(recent, key = { it.id }) { TransactionRow(it, money) }
    }
}

@Composable
private fun ActivityScreen(items: List<Transaction>, money: NumberFormat, remove: (Transaction) -> Unit, padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (items.isEmpty()) item { EmptyState() }
        items(items, key = { it.id }) { t ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TransactionRow(t, money, Modifier.weight(1f))
                IconButton(onClick = { remove(t) }) { Icon(Icons.Outlined.Delete, "Delete") }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: ImageVector, modifier: Modifier) {
    Card(modifier, RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null)
            Spacer(Modifier.height(10.dp))
            Text(title)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun TransactionRow(t: Transaction, money: NumberFormat, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier,
        headlineContent = { Text(t.title) },
        supportingContent = { Text(if (t.income) "Income" else "Expense") },
        leadingContent = { Icon(if (t.income) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward, null) },
        trailingContent = { Text((if (t.income) "+" else "-") + money.format(t.amount)) }
    )
}

@Composable
private fun EmptyState() {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.AccountBalanceWallet, null)
            Spacer(Modifier.height(8.dp))
            Text("No transactions yet", style = MaterialTheme.typography.titleMedium)
            Text("Tap + to add your first transaction")
        }
    }
}

@Composable
private fun AddTransactionDialog(add: (Transaction) -> Unit, close: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var income by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = close,
        title = { Text("Add transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Income")
                    Switch(income, { income = it })
                    Text(if (income) "Income" else "Expense")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0,
                onClick = { add(Transaction(UUID.randomUUID().toString(), title.trim(), amount.toDouble(), income)) }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(close) { Text("Cancel") } }
    )
}
