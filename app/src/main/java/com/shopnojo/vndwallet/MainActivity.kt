package com.shopnojo.vndwallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Amber = Color(0xFFF59E0B)
private val LightScheme = lightColorScheme(
    primary = Color(0xFFB96500),
    onPrimary = Color.White,
    secondary = Color(0xFF765A38),
    background = Color(0xFFFFF8F0),
    surface = Color(0xFFFFFBF7)
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF3D2500),
    secondary = Color(0xFFE4C39D),
    background = Color(0xFF171411),
    surface = Color(0xFF211D19)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var themeChoice by remember { mutableStateOf("System") }
            val dark = when (themeChoice) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme()
            }
            MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme) {
                VndWalletApp(
                    onThemeToggle = {
                        themeChoice = when (themeChoice) {
                            "System" -> "Dark"
                            "Dark" -> "Light"
                            else -> "System"
                        }
                    },
                    themeChoice = themeChoice
                )
            }
        }
    }
}

@Composable
private fun VndWalletApp(onThemeToggle: () -> Unit, themeChoice: String) {
    var selectedTab by remember { mutableIntStateOf(0) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { selectedTab = 1 },
                    containerColor = Amber,
                    contentColor = Color(0xFF2D1B00)
                ) { Icon(Icons.Default.Add, contentDescription = "Add expense") }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Wallet") },
                        label = { Text("Wallet") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Expenses") },
                        label = { Text("Expenses") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        ) { innerPadding ->
            when (selectedTab) {
                0 -> WalletDashboard(Modifier.padding(innerPadding))
                1 -> PlaceholderScreen("Expenses", "Your trip spending will appear here.", Modifier.padding(innerPadding))
                else -> SettingsScreen(themeChoice, onThemeToggle, Modifier.padding(innerPadding))
            }
        }
    }
}

@Composable
private fun WalletDashboard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("VND Wallet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Your Vietnam trip, at a glance", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Column(Modifier.padding(22.dp)) {
                Text("AVAILABLE BALANCE", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("₫0", color = MaterialTheme.colorScheme.onPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("₹0.00 · INR equivalent", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Initial cash", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f), fontSize = 12.sp)
                        Text("₫0", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Total spent", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f), fontSize = 12.sp)
                        Text("₫0", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Text("Cash overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.background(Amber.copy(alpha = 0.17f), RoundedCornerShape(14.dp)).padding(12.dp)
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("Cash denominations", fontWeight = FontWeight.SemiBold)
                    Text("Set your notes and coins to calculate your physical cash.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("→", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
            }
        }
        Text("No expenses yet. Tap + when you’re ready to log a purchase.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsScreen(themeChoice: String, onThemeToggle: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Appearance", fontWeight = FontWeight.SemiBold)
                    Text("Theme: $themeChoice", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.TextButton(onClick = onThemeToggle) {
                    Text("Change")
                }
            }
        }
        Text("Exchange rate and wallet data controls will be added next.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
