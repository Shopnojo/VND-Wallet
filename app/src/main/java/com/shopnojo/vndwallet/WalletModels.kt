package com.shopnojo.vndwallet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val price: Long,
    val amountPaid: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val notesPaid: Map<Long, Int> = emptyMap(),
    val changeNotes: Map<Long, Int> = emptyMap()
) {
    val change: Long get() = (amountPaid - price).coerceAtLeast(0)
}

data class WalletState(
    val initialCash: Long = 0,
    val addedCash: Long = 0,
    val balanceAdjustment: Long = 0,
    val expenses: List<Expense> = emptyList(),
    val denominations: Map<Long, Int> = defaultDenominations(),
    val rateInrPerVnd: Double = 0.00367632,
    val rateDate: String = "",
    val rateUpdatedAt: Long = 0,
    val theme: String = "System",
    val exactCashTracking: Boolean = false,
    val inventoryNeedsReconciliation: Boolean = false
) {
    val totalSpent: Long get() = expenses.sumOf { it.price }
    val availableBalance: Long get() = (initialCash + addedCash + balanceAdjustment - totalSpent).coerceAtLeast(0)
    val physicalCash: Long get() = denominations.entries.sumOf { it.key * it.value.coerceAtLeast(0) }
    val totalCashIn: Long get() = initialCash + addedCash
}

fun defaultDenominations(): Map<Long, Int> = linkedMapOf(
    500_000L to 0, 200_000L to 0, 100_000L to 0, 50_000L to 0,
    20_000L to 0, 10_000L to 0, 5_000L to 0, 2_000L to 0,
    1_000L to 0, 500L to 0, 200L to 0, 100L to 0
)

fun formatVnd(amount: Long): String = "₫" + String.format(Locale.US, "%,d", amount)
fun formatInr(vnd: Long, rate: Double): String = "₹" + String.format(Locale("en", "IN"), "%,.2f", vnd * rate)
fun formatDate(timestamp: Long): String = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))

class WalletStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vnd_wallet", Context.MODE_PRIVATE)

    fun load(): WalletState {
        val jsonText = prefs.getString("wallet", null) ?: return WalletState(
            rateInrPerVnd = prefs.getString("rate", "0.00367632")!!.toDoubleOrNull() ?: 0.00367632,
            rateDate = prefs.getString("rate_date", "") ?: "",
            rateUpdatedAt = prefs.getLong("rate_updated_at", 0L)
        )
        return try {
            val root = JSONObject(jsonText)
            val denoms = linkedMapOf<Long, Int>()
            val denomJson = root.optJSONObject("denominations") ?: JSONObject()
            defaultDenominations().keys.forEach { denoms[it] = denomJson.optInt(it.toString(), 0).coerceAtLeast(0) }
            val expensesJson = root.optJSONArray("expenses") ?: JSONArray()
            val expenses = buildList {
                for (i in 0 until expensesJson.length()) {
                    val item = expensesJson.optJSONObject(i) ?: continue
                    add(Expense(
                        id = item.optString("id", UUID.randomUUID().toString()),
                        description = item.optString("description", "Expense"),
                        price = item.optLong("price", 0).coerceAtLeast(0),
                        amountPaid = item.optLong("amountPaid", item.optLong("price", 0)).coerceAtLeast(0),
                        timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                        notesPaid = readCounts(item.optJSONObject("notesPaid")),
                        changeNotes = readCounts(item.optJSONObject("changeNotes"))
                    ))
                }
            }
            WalletState(
                initialCash = root.optLong("initialCash", 0).coerceAtLeast(0),
                addedCash = root.optLong("addedCash", 0).coerceAtLeast(0),
                balanceAdjustment = root.optLong("balanceAdjustment", 0),
                expenses = expenses.sortedByDescending { it.timestamp },
                denominations = denoms,
                rateInrPerVnd = root.optDouble("rateInrPerVnd", 0.00367632).takeIf { it > 0 } ?: 0.00367632,
                rateDate = root.optString("rateDate", ""),
                rateUpdatedAt = root.optLong("rateUpdatedAt", 0),
                theme = root.optString("theme", "System"),
                exactCashTracking = root.optBoolean("exactCashTracking", false),
                inventoryNeedsReconciliation = root.optBoolean("inventoryNeedsReconciliation", false)
            )
        } catch (_: Exception) { WalletState() }
    }

    fun save(state: WalletState) {
        val root = JSONObject()
            .put("initialCash", state.initialCash)
            .put("addedCash", state.addedCash)
            .put("balanceAdjustment", state.balanceAdjustment)
            .put("rateInrPerVnd", state.rateInrPerVnd)
            .put("rateDate", state.rateDate)
            .put("rateUpdatedAt", state.rateUpdatedAt)
            .put("theme", state.theme)
            .put("exactCashTracking", state.exactCashTracking)
            .put("inventoryNeedsReconciliation", state.inventoryNeedsReconciliation)
        val denoms = JSONObject()
        state.denominations.forEach { (value, count) -> denoms.put(value.toString(), count.coerceAtLeast(0)) }
        root.put("denominations", denoms)
        val expenses = JSONArray()
        state.expenses.forEach { expense ->
            val paid = JSONObject()
            expense.notesPaid.forEach { (value, count) -> paid.put(value.toString(), count) }
            val change = JSONObject()
            expense.changeNotes.forEach { (value, count) -> change.put(value.toString(), count) }
            expenses.put(JSONObject()
                .put("id", expense.id)
                .put("description", expense.description)
                .put("price", expense.price)
                .put("amountPaid", expense.amountPaid)
                .put("timestamp", expense.timestamp)
                .put("notesPaid", paid)
                .put("changeNotes", change))
        }
        prefs.edit().putString("wallet", root.toString()).apply()
    }

    private fun readCounts(json: JSONObject?): Map<Long, Int> {
        if (json == null) return emptyMap()
        val result = linkedMapOf<Long, Int>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            key.toLongOrNull()?.let { result[it] = json.optInt(key, 0).coerceAtLeast(0) }
        }
        return result
    }
}

fun adjustDenominations(current: Map<Long, Int>, expense: Expense, applyExpense: Boolean): Map<Long, Int> {
    val next = current.toMutableMap()
    expense.notesPaid.forEach { (value, count) ->
        next[value] = ((next[value] ?: 0) + if (applyExpense) -count else count).coerceAtLeast(0)
    }
    expense.changeNotes.forEach { (value, count) ->
        next[value] = ((next[value] ?: 0) + if (applyExpense) count else -count).coerceAtLeast(0)
    }
    return next
}
