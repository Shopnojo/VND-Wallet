package com.shopnojo.vndwallet

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.UUID

private val amber = androidx.compose.ui.graphics.Color(0xFFF59E0B)
private val denoms = defaultDenominations().keys.toList()

@Composable
fun VndWalletApp() {
    val context = LocalContext.current
    val store = remember { WalletStore(context) }
    val rates = remember { ExchangeRateRepository(context) }
    var state by remember { mutableStateOf(store.load()) }
    var tab by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var loading by remember { mutableStateOf(false) }
    var rateMessage by remember { mutableStateOf("") }
    fun save(next: WalletState) { state = next; store.save(next) }
    fun refresh() {
        if (loading) return
        loading = true
        Thread {
            try {
                val r = rates.fetchLatest()
                Handler(Looper.getMainLooper()).post {
                    save(state.copy(rateInrPerVnd = r.inrPerVnd, rateDate = r.date, rateUpdatedAt = r.fetchedAt))
                    loading = false; rateMessage = "Updated from reference data: ${r.date}"
                }
            } catch (_: Exception) {
                Handler(Looper.getMainLooper()).post {
                    loading = false
                    rateMessage = if (rates.cached() != null) "Refresh failed; using saved rate offline." else "Rate unavailable; using starter estimate."
                }
            }
        }.start()
    }
    LaunchedEffect(Unit) {
        rates.cached()?.let { save(state.copy(rateInrPerVnd = it.inrPerVnd, rateDate = it.date, rateUpdatedAt = it.fetchedAt)) }
        if (rates.isRefreshDue()) refresh()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestState by rememberUpdatedState(state)
    val latestSave by rememberUpdatedState<(WalletState) -> Unit>({ next -> save(next) })
    val latestRefresh by rememberUpdatedState<() -> Unit>({ refresh() })
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                rates.cached()?.let { cached ->
                    val current = latestState
                    if (current.rateInrPerVnd != cached.inrPerVnd || current.rateDate != cached.date || current.rateUpdatedAt != cached.fetchedAt) {
                        latestSave(current.copy(rateInrPerVnd = cached.inrPerVnd, rateDate = cached.date, rateUpdatedAt = cached.fetchedAt))
                    }
                }
                if (rates.isRefreshDue()) latestRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val dark = state.theme == "Dark" || (state.theme == "System" && androidx.compose.foundation.isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) darkColorScheme(
        primary = Color(0xFFFFB74D), onPrimary = Color(0xFF30200B),
        background = Color(0xFF141217), onBackground = Color(0xFFF5F1F8),
        surface = Color(0xFF302D35), onSurface = Color(0xFFF5F1F8),
        surfaceVariant = Color(0xFF3B3741), onSurfaceVariant = Color(0xFFC8C1CE)
    ) else lightColorScheme(
        primary = Color(0xFF9B5200), onPrimary = Color.White,
        background = Color(0xFFFFF9F2), onBackground = Color(0xFF17131A),
        surface = Color.White, onSurface = Color(0xFF17131A),
        surfaceVariant = Color(0xFFF0EAF2), onSurfaceVariant = Color(0xFF5F5965)
    )) {
        Scaffold(
            floatingActionButton = { if (tab != 2) FloatingActionButton(onClick = { editing = null; dialog = "expense" }, containerColor = amber) { Icon(Icons.Default.Add, "Add expense") } },
            bottomBar = { NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Default.AccountBalanceWallet, null) }, label = { Text("Wallet") })
                NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Default.ReceiptLong, null) }, label = { Text("Expenses") })
                NavigationBarItem(tab == 2, { tab = 2 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            } }
        ) { pad ->
            when (tab) {
                0 -> WalletScreen(state, dark, Modifier.padding(pad), { dialog = "cash" }, { dialog = "addcash" }, { editing = null; dialog = "expense" }, { save(it) }, { val target = state.physicalCash; save(state.copy(balanceAdjustment = target + state.totalSpent - state.initialCash - state.addedCash, inventoryNeedsReconciliation = false)) })
                1 -> ExpensesScreen(state, Modifier.padding(pad), { editing = it; dialog = "expense" }, { e ->
                    val hasNotes = e.notesPaid.isNotEmpty() || e.changeNotes.isNotEmpty()
                    val restored = if (hasNotes) adjustDenominations(state.denominations, e, false) else state.denominations
                    save(state.copy(expenses = state.expenses.filterNot { it.id == e.id }, denominations = restored, inventoryNeedsReconciliation = state.inventoryNeedsReconciliation || (state.exactCashTracking && !hasNotes)))
                })
                else -> SettingsScreen(state, Modifier.padding(pad), loading, rateMessage, ::refresh, { save(state.copy(theme = it)) }, { save(state.copy(exactCashTracking = it, inventoryNeedsReconciliation = it)) }, { dialog = "addcash" })
            }
        }
    }
    if (dialog == "expense") ExpenseDialog(editing, state.exactCashTracking, { dialog = ""; editing = null }) { desc, price, paid, notes, change ->
        val old = editing
        val e = Expense(old?.id ?: UUID.randomUUID().toString(), desc, price, paid, old?.timestamp ?: System.currentTimeMillis(), notes, change)
        val oldHasNotes = old != null && (old.notesPaid.isNotEmpty() || old.changeNotes.isNotEmpty())
        val restored = if (oldHasNotes) adjustDenominations(state.denominations, old!!, false) else state.denominations
        val newHasNotes = notes.isNotEmpty() || change.isNotEmpty()
        val updatedDenoms = if (state.exactCashTracking && newHasNotes) adjustDenominations(restored, e, true) else restored
        val needsReconcile = (state.exactCashTracking && !newHasNotes) || (state.inventoryNeedsReconciliation && !oldHasNotes)
        save(state.copy(expenses = (state.expenses.filterNot { it.id == e.id } + e).sortedByDescending { it.timestamp }, denominations = updatedDenoms, inventoryNeedsReconciliation = needsReconcile || (!state.exactCashTracking && paid > price)))
        dialog = ""; editing = null
    }
    if (dialog == "cash") DenomDialog(state.denominations, { dialog = "" }) { save(state.copy(denominations = it, inventoryNeedsReconciliation = false)); dialog = "" }
    if (dialog == "addcash") AddCashDialog(state.totalCashIn == 0L, { dialog = "" }) { amount, initial ->
        save(if (initial) state.copy(initialCash = amount, addedCash = 0) else state.copy(addedCash = (state.addedCash + amount).coerceAtLeast(state.addedCash), inventoryNeedsReconciliation = true))
        dialog = ""
    }
}

@Composable private fun WalletScreen(s: WalletState, dark: Boolean, modifier: Modifier, cash: () -> Unit, addCash: () -> Unit, addExpense: () -> Unit, unused: (WalletState) -> Unit, syncBalance: () -> Unit) {
    LazyColumn(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("VND Wallet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Your Vietnam trip, at a glance", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Box(
                Modifier.fillMaxWidth().background(
                    Brush.linearGradient(if (dark) listOf(Color(0xFFFFC15A), Color(0xFFFFA726), Color(0xFFE88900)) else listOf(Color(0xFFFFC15A), Color(0xFFFFAB2E), Color(0xFFF59E0B))),
                    RoundedCornerShape(28.dp)
                )
            ) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Text("AVAILABLE BALANCE", color = Color(0xFF4B2A66), fontSize = 12.sp)
                    Text(formatVnd(s.availableBalance), color = Color(0xFF3B176D), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text("${formatInr(s.availableBalance, s.rateInrPerVnd)} · INR equivalent", color = Color(0xFF3B176D))
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Initial cash", color = Color(0xFF4B2A66)); Text(formatVnd(s.initialCash), color = Color(0xFF3B176D), fontWeight = FontWeight.Bold) }
                        Column(horizontalAlignment = Alignment.End) { Text("Total spent", color = Color(0xFF4B2A66)); Text(formatVnd(s.totalSpent), color = Color(0xFF3B176D), fontWeight = FontWeight.Bold) }
                    }
                    if (s.addedCash > 0) Text("Additional cash added: ${formatVnd(s.addedCash)}", color = Color(0xFF4B2A66), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MiniCard("Available in INR", formatInr(s.availableBalance, s.rateInrPerVnd), Modifier.weight(1f)); MiniCard("Purchases", s.expenses.size.toString(), Modifier.weight(1f)) } }
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = cash), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Physical cash  ·  Edit denominations", fontWeight = FontWeight.SemiBold)
                    Text("${formatVnd(s.physicalCash)} · ${formatInr(s.physicalCash, s.rateInrPerVnd)} · ${s.denominations.values.sum()} notes/coins", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.inventoryNeedsReconciliation) Text("Physical cash may need reconciliation.", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            OutlinedButton(onClick = syncBalance, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sync balance to physical cash")
            }
            Text("Sets Available Balance to the total of your denomination counts. Your spending history stays unchanged.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        item { Button(onClick = addCash, modifier = Modifier.fillMaxWidth()) { Text(if (s.totalCashIn == 0L) "Set starting cash" else "Add more cash") } }
        item { Text("Recent expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (s.expenses.isEmpty()) item { Text("Nothing logged yet. Tap + to add your first purchase.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(s.expenses.take(4), key = { it.id }) { ExpenseLine(it, s.rateInrPerVnd, {}, false) }
    }
}

@Composable private fun MiniCard(title: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(15.dp)) { Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall); Text(value, fontWeight = FontWeight.Bold) } }
}

@Composable private fun ExpensesScreen(s: WalletState, modifier: Modifier, edit: (Expense) -> Unit, delete: (Expense) -> Unit) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("Expenses", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("${s.expenses.size} purchases · ${formatVnd(s.totalSpent)} spent", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (s.expenses.isEmpty()) Text("No expenses yet. Tap + to log a purchase.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(s.expenses, key = { it.id }) { e ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        ExpenseLine(e, s.rateInrPerVnd, { edit(e) }, true)
                        Row(Modifier.align(Alignment.End)) { TextButton(onClick = { edit(e) }) { Icon(Icons.Default.Edit, null); Text("Edit") }; TextButton(onClick = { delete(e) }) { Icon(Icons.Default.Delete, null); Text("Delete") } }
                    }
                }
            }
        }
    }
}

@Composable private fun ExpenseLine(e: Expense, rate: Double, click: () -> Unit, editable: Boolean) {
    Row(Modifier.fillMaxWidth().clickable(enabled = editable, onClick = click), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(e.description, fontWeight = FontWeight.SemiBold)
            Text(formatDate(e.timestamp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (e.change > 0) Text("Paid ${formatVnd(e.amountPaid)} · Change ${formatVnd(e.change)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) { Text("-${formatVnd(e.price)}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold); Text(formatInr(e.price, rate), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun SettingsScreen(s: WalletState, modifier: Modifier, loading: Boolean, message: String, refresh: () -> Unit, setTheme: (String) -> Unit, exact: (Boolean) -> Unit, addCash: () -> Unit) {
    LazyColumn(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("VND → INR exchange rate", fontWeight = FontWeight.Bold)
                Text("1 VND = ₹${String.format(java.util.Locale.US, "%.8f", s.rateInrPerVnd)}", style = MaterialTheme.typography.titleLarge)
                Text(if (s.rateDate.isBlank()) "Using starter estimate until updated." else "Reference rate date: ${s.rateDate}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.rateUpdatedAt > 0) Text("Last checked: ${formatDate(s.rateUpdatedAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text("Indicative central-bank reference rate. Money changers, banks and payment providers may apply different rates or fees.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Button(onClick = refresh, enabled = !loading) { if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text(if (loading) "Refreshing…" else "Refresh now") }
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            } }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Appearance", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("System", "Light", "Dark").forEach { v -> if (s.theme == v) Button(onClick = { setTheme(v) }) { Text(v) } else OutlinedButton(onClick = { setTheme(v) }) { Text(v) } } }
            } }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cash tracking", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Exact denomination notes"); Text("Allow recording note counts paid and received in the expense form.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(s.exactCashTracking, exact) }
                OutlinedButton(onClick = addCash, modifier = Modifier.fillMaxWidth()) { Text("Add cash / correct starting amount") }
            } }
        }
        item { Text("Expenses and cash counts are saved on this device. Rates refresh when online and the last saved rate remains available offline.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun ExpenseDialog(existing: Expense?, exact: Boolean, dismiss: () -> Unit, save: (String, Long, Long, Map<Long, Int>, Map<Long, Int>) -> Unit) {
    val view = LocalView.current
    var desc by remember(existing?.id) { mutableStateOf(existing?.description ?: "") }
    var priceText by remember(existing?.id) { mutableStateOf(existing?.price?.let(::formatEditableMoney) ?: "") }
    var paidText by remember(existing?.id) { mutableStateOf(existing?.amountPaid?.let(::formatEditableMoney) ?: "") }
    val price = priceText.toLongOrNullMoney() ?: 0L
    val paid = paidText.toLongOrNullMoney() ?: price
    AlertDialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.94f),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 10.dp,
        title = { Text(if (existing == null) "Add expense" else "Edit expense") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(priceText, { priceText = formatMoneyWhileTyping(it) }, label = { Text("Actual price (VND)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(paidText, { paidText = formatMoneyWhileTyping(it) }, label = { Text("Amount handed over (optional)") }, supportingText = { Text("Leave blank if you paid the exact price.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (price > 0 && paid >= price) Text("Change: \${formatVnd(paid - price)}. Only \${formatVnd(price)} is deducted.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { Button(onClick = { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); save(desc.trim().ifBlank { "Purchase" }, price, paidText.toLongOrNullMoney()?.takeIf { it >= price } ?: price, emptyMap(), emptyMap()) }, enabled = price > 0 && (paidText.isBlank() || paid >= price)) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}

private fun formatEditableMoney(value: Long): String = java.text.DecimalFormat("#,##0", java.text.DecimalFormatSymbols(java.util.Locale.US)).format(value)
private fun formatMoneyWhileTyping(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    return digits.toLongOrNull()?.let(::formatEditableMoney) ?: digits
}
private fun String.toLongOrNullMoney(): Long? = filter(Char::isDigit).toLongOrNull()

@Composable private fun DenomDialog(current: Map<Long, Int>, dismiss: () -> Unit, save: (Map<Long, Int>) -> Unit) {
    val view = LocalView.current
    val counts = remember { denoms.associateWith { current[it] ?: 0 }.toMutableMap() }
    AlertDialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.94f),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 10.dp,
        title = { Text("Physical cash denominations") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Enter the notes and coins currently in your wallet.") }
                items(denoms) { value ->
                    var count by remember(value) { mutableStateOf((counts[value] ?: 0).toString()) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatVnd(value), Modifier.weight(1f))
                        OutlinedTextField(count, { count = it.filter(Char::isDigit); counts[value] = count.toIntOrNull()?.coerceAtLeast(0) ?: 0 }, Modifier.width(112.dp), label = { Text("Count") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); save(counts.toMap()) }) { Text("Save counts") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}

@Composable private fun AddCashDialog(initialDefault: Boolean, dismiss: () -> Unit, save: (Long, Boolean) -> Unit) {
    val view = LocalView.current
    var amount by remember { mutableStateOf("") }
    var initial by remember { mutableStateOf(initialDefault) }
    AlertDialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false), modifier = Modifier.fillMaxWidth(0.94f), shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface, textContentColor = MaterialTheme.colorScheme.onSurface, tonalElevation = 10.dp, title = { Text(if (initialDefault) "Set starting cash" else "Add more cash") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(amount, { amount = formatMoneyWhileTyping(it) }, label = { Text("Amount in VND") }, supportingText = { Text("Enter the new cash amount you received.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(initial, { initial = it }); Spacer(Modifier.width(8.dp)); Column { Text("Replace starting cash"); Text("Turn off to add this amount to your existing funds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            val parsed = amount.toLongOrNullMoney() ?: 0L
            if (parsed > 0) Text(if (initial) "Starting cash will be set to ${formatVnd(parsed)}." else "Available balance will increase by ${formatVnd(parsed)}.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
    }, confirmButton = { Button(onClick = { amount.toLongOrNullMoney()?.takeIf { it > 0 }?.let { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); save(it, initial) } }, enabled = (amount.toLongOrNullMoney() ?: 0) > 0) { Text(if (initial) "Set starting cash" else "Add cash") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
