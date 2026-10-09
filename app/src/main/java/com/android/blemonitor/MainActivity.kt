package com.android.blemonitor

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.android.blemonitor.presentation.accessibility.AccessibilityViewModel
import com.android.blemonitor.presentation.ble.BleViewModel
import com.android.blemonitor.presentation.main.MainTabScreen
import com.android.blemonitor.repository.AccessibilityRepository
import com.android.blemonitor.repository.BleRepository
import com.android.blemonitor.service.BleForegroundService

class MainActivity : ComponentActivity() {
    private var isServiceBound = false
    private val bleRepository = BleRepository()
    private val accessibilityRepository = AccessibilityRepository()

    private val bleViewModel = BleViewModel(bleRepository)
    private val accessibilityViewModel = AccessibilityViewModel(accessibilityRepository)
    private var isBleServiceReady = false
    private var pendingConnectAddress: String? = null

    private var scanAfterPermissionGrant = false
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private fun connectToAddress(address: String) {
        val hasPermission =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            pendingConnectAddress = address
            requestPermissionsOnLaunch()
            return
        }

        val adapter = bluetoothAdapter

        if (adapter == null) {
            Toast.makeText(
                this,
                "Bluetooth is not available on this device.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!adapter.isEnabled) {
            pendingConnectAddress = address
            enableBtLauncher.launch(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            )
            return
        }

        bleViewModel.connect(adapter, address)
    }

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(
            name: ComponentName?,
            service: IBinder?
        ) {
            val binder = service as? BleForegroundService.LocalBinder
            if (binder == null) {
                android.util.Log.e("BLE_TRACE", "Invalid service binder")
                return
            }

            val bleService = binder.getService()

            // Attach the service to the repository first
            bleRepository.bindService(bleService)
            isBleServiceReady = true

            android.util.Log.d(
                "BLE_TRACE",
                "Service connected and repository is ready"
            )

            // Process a Connect tap that happened while binding
            val address = pendingConnectAddress
            pendingConnectAddress = null

            if (address != null) {
                connectToAddress(address)
            }
        }


        override fun onServiceDisconnected(name: ComponentName?) {
            isBleServiceReady = false
            bleRepository.unbindService()

            android.util.Log.e(
                "BLE_TRACE",
                "Service disconnected unexpectedly"
            )
        }
    }

    private val enableBtLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (
                result.resultCode == RESULT_OK &&
                bluetoothAdapter?.isEnabled == true
            ) {
                val address = pendingConnectAddress

                if (address != null) {
                    pendingConnectAddress = null
                    connectToAddress(address)
                } else {
                    bleViewModel.startScan(bluetoothAdapter)
                }
            } else {
                Toast.makeText(
                    this,
                    "Bluetooth is required to scan or connect.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }


    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val allGranted = permissions.values.all { it }

            if (allGranted) {
                startAndBindBleService()

                if (scanAfterPermissionGrant) {
                    scanAfterPermissionGrant = false
                    checkBluetoothAndScan()
                }

                val address = pendingConnectAddress

                if (address != null && isBleServiceReady) {
                    pendingConnectAddress = null
                    connectToAddress(address)
                }
            } else {
                scanAfterPermissionGrant = false
                showPermissionDeniedDialog()
            }
        }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage(
                "BLE Monitor needs Bluetooth permissions to scan and connect to devices. " +
                        "Please allow the required permissions in App Settings."
            )
            .setCancelable(false)
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        enableEdgeToEdge()

        setContent {

            MainTabScreen(
                bleViewModel = bleViewModel,
                accessibilityViewModel = accessibilityViewModel,
                onStartScan = { requestPermissionsAndScan() },

                onConnectDevice = { address ->
                    if (isBleServiceReady) {
                        connectToAddress(address)
                    } else {
                        pendingConnectAddress = address

                        android.util.Log.d(
                            "BLE_TRACE",
                            "Waiting for service before connecting..."
                        )

                        if (!isServiceBound) {
                            startAndBindBleService()
                        }
                    }
                }
            )
        }

        // Ask for permissions when the app opens
        requestPermissionsOnLaunch()
    }

    private fun startAndBindBleService() {
        val serviceIntent = Intent(
            this,
            BleForegroundService::class.java
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        if (!isServiceBound) {
            isServiceBound = bindService(
                serviceIntent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )

            android.util.Log.d(
                "BLE_TRACE",
                "Binding requested: $isServiceBound"
            )
        }
    }

    override fun onResume() {
        super.onResume()

        val hasRequiredPermissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            }

        if (hasRequiredPermissions) {
            startAndBindBleService()
        }
    }

    private fun requestPermissionsOnLaunch() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            startAndBindBleService()
        }
    }


    private fun requestPermissionsAndScan() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            scanAfterPermissionGrant = true
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            checkBluetoothAndScan()
        }
    }

    private fun checkBluetoothAndScan() {
        val adapter = bluetoothAdapter

        if (adapter == null) {
            Toast.makeText(
                this,
                "This device does not support Bluetooth.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!adapter.isEnabled) {
            enableBtLauncher.launch(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            )
            return
        }

        bleViewModel.startScan(adapter)
    }

    override fun onDestroy() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }

        bleRepository.unbindService()
        isBleServiceReady = false

        super.onDestroy()
    }

}