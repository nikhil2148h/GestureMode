package com.example.gesturemode

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            // Update state
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GestureControlScreen(
                        onStartService = { startGestureService() },
                        onStopService = { stopGestureService() },
                        onRequestCamera = { requestPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        onRequestOverlay = { requestOverlayPermission() },
                        onRequestAccessibility = { requestAccessibilityPermission() },
                        context = this
                    )
                }
            }
        }
    }

    private fun startGestureService() {
        val intent = Intent(this, GestureControlService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    private fun stopGestureService() {
        val intent = Intent(this, GestureControlService::class.java)
        stopService(intent)
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
}

@Composable
fun GestureControlScreen(
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onRequestCamera: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    context: Context
) {
    var isServiceRunning by remember { mutableStateOf(false) }
    
    // Polling states for simplicity in sample
    var hasCamera by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) 
    }
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    
    val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
    var hasAccessibility by remember { 
        mutableStateOf(enabledServices.any { it.id.contains(context.packageName) })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Gesture Control System", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        // Permissions Status
        PermissionItem(name = "Camera Permission", isGranted = hasCamera, onClick = onRequestCamera)
        PermissionItem(name = "Overlay Permission", isGranted = hasOverlay, onClick = onRequestOverlay)
        PermissionItem(name = "Accessibility Service", isGranted = hasAccessibility, onClick = onRequestAccessibility)

        Spacer(modifier = Modifier.height(32.dp))

        val allPermissionsGranted = hasCamera && hasOverlay && hasAccessibility

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("System Status: ", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = isServiceRunning,
                onCheckedChange = { checked ->
                    if (checked && allPermissionsGranted) {
                        isServiceRunning = true
                        onStartService()
                    } else if (!checked) {
                        isServiceRunning = false
                        onStopService()
                    }
                },
                enabled = allPermissionsGranted || isServiceRunning
            )
        }
        
        if (!allPermissionsGranted) {
            Text("Please grant all permissions to enable.", color = MaterialTheme.colorScheme.error)
            Text("Note: Important! Make sure you download `hand_landmarker.task` from Google MediaPipe and place it in the app's `src/main/assets` directory before running.", 
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top=16.dp))
        }
    }
}

@Composable
fun PermissionItem(name: String, isGranted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name)
        Button(onClick = onClick, enabled = !isGranted) {
            Text(if (isGranted) "Granted" else "Grant")
        }
    }
}
