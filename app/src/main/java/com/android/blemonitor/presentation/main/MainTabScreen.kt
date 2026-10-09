
package com.android.blemonitor.presentation.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.android.blemonitor.presentation.accessibility.AccessibilityScreen
import com.android.blemonitor.presentation.accessibility.AccessibilityViewModel
import com.android.blemonitor.presentation.ble.BleScreen
import com.android.blemonitor.presentation.ble.BleViewModel

@Composable
fun MainTabScreen(
    bleViewModel: BleViewModel,
    accessibilityViewModel: AccessibilityViewModel,
    onStartScan: () -> Unit,
    onConnectDevice: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Bluetooth,
                            contentDescription = "BLE Monitor"
                        )
                    },
                    label = { Text("BLE Monitor") }
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.AccessibilityNew,
                            contentDescription = "Accessibility"
                        )
                    },
                    label = { Text("Accessibility") }
                )
            }
        }
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> BleScreen(
                    viewModel = bleViewModel,
                    onStartScan = onStartScan,
                    onConnectDevice = onConnectDevice
                )

                1 -> AccessibilityScreen(
                    viewModel = accessibilityViewModel
                )
            }
        }
    }
}

