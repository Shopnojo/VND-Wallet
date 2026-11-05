package com.shopnojo.vndwallet

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ExchangeRate(val inrPerVnd: Double, val date: String, val fetchedAt: Long)

class ExchangeRateRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vnd_wallet", Context.MODE_PRIVATE)

    fun cached(): ExchangeRate? {
        val rate = prefs.getString("rate", null)?.toDoubleOrNull() ?: return null
        if (rate <= 0.0) return null
        return ExchangeRate(rate, prefs.getString("rate_date", "") ?: "", prefs.getLong("rate_updated_at", 0L))
    }

    fun isRefreshDue(): Boolean {
        val lastDate = prefs.getString("rate_date", "") ?: ""
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        return lastDate != today
    }

    fun fetchLatest(): ExchangeRate {
        // Free public reference-rate API. Its figures are indicative, not a cash-exchange quote.
        val connection = (URL("https://theratesapi.com/api/latest?base=VND&symbols=INR").openConnection() as HttpURLConnection)
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Rate service returned HTTP $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val rate = json.optJSONObject("rates")?.optDouble("INR", 0.0) ?: 0.0
            val date = json.optString("date", "")
            if (rate <= 0.0 || date.isBlank()) throw IllegalStateException("Rate service returned invalid data")
            val fetched = ExchangeRate(rate, date, System.currentTimeMillis())
            prefs.edit()
                .putString("rate", rate.toString())
                .putString("rate_date", date)
                .putLong("rate_updated_at", fetched.fetchedAt)
                .apply()
            return fetched
        } finally {
            connection.disconnect()
        }
    }
}
