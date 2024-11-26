package com.example.wifi_monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.net.NetworkInterface

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkMonitorApp() {
    var wifiNetworks by remember { mutableStateOf(listOf<String>()) }
    var activeConnections by remember { mutableStateOf(listOf<ConnectionInfo>()) }
    var alerts by remember { mutableStateOf(listOf<NetworkAlert>()) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current //contexto actual

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            while (true) {
                val networks = getCurrentWifiNetworks(context) // Pasar el contexto correcto
                val connections = getCurrentNetworkConnections()

                val newAlerts = checkForNewConnections(activeConnections, connections)

                withContext(Dispatchers.Main) {
                    wifiNetworks = networks
                    activeConnections = connections
                    alerts = alerts + newAlerts
                }

                delay(30000) // Check every 30 seconds
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Network Security Monitor") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // WiFi Networks Section
            Text("Available WiFi Networks:", style = MaterialTheme.typography.titleLarge)
            wifiNetworks.forEach { network ->
                Text(network, modifier = Modifier.padding(4.dp))
            }
            // Active Connections Section
            Spacer(modifier = Modifier.height(16.dp))
            Text("Active Connections:", style = MaterialTheme.typography.titleLarge)
            activeConnections.forEach { connection ->
                Text(
                    "IP: ${connection.ipAddress} | Network: ${connection.networkName}",
                    modifier = Modifier.padding(4.dp)
                )
            }

            // Alerts Section
            Spacer(modifier = Modifier.height(16.dp))
            Text("Security Alerts:", style = MaterialTheme.typography.titleLarge)
            alerts.forEach { alert ->
                Text(
                    alert.message,
                    color = Color.Red,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.LightGray)
                        .padding(8.dp)
                )
            }
        }
    }
}

// Definición de clases de datos y funciones auxiliares
data class ConnectionInfo(
    val ipAddress: String,
    val networkName: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class NetworkAlert(
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun getCurrentWifiNetworks(context: Context): List<String> {
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return emptyList()
    }

    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wifiManager.scanResults.map {
                it.wifiSsid.toString().removeSurrounding("\"")
            }
        } else {
            @Suppress("DEPRECATION")
            wifiManager.scanResults.map {
                it.SSID.removeSurrounding("\"")
            }
        }
    } catch (e: SecurityException) {
        e.printStackTrace()
        emptyList()
    }
}

fun getCurrentNetworkConnections(): List<ConnectionInfo> {
    val connections = mutableListOf<ConnectionInfo>()
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (networkInterface in interfaces) {
            if (networkInterface.isUp) {
                for (inetAddress in networkInterface.inetAddresses) {
                    if (!inetAddress.isLoopbackAddress) {
                        connections.add(
                            ConnectionInfo(
                                ipAddress = inetAddress.hostAddress ?: "Unknown",
                                networkName = networkInterface.name
                            )
                        )
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return connections
}

fun checkForNewConnections(
    previousConnections: List<ConnectionInfo>,
    currentConnections: List<ConnectionInfo>
): List<NetworkAlert> {
    val newConnections = currentConnections.filter { current ->
        previousConnections.none { previous ->
            previous.ipAddress == current.ipAddress
        }
    }

    return newConnections.map { connection ->
        NetworkAlert("New Connection Detected: ${connection.ipAddress} on ${connection.networkName}")
    }
}