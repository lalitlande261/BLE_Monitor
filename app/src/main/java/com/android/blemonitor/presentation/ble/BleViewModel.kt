
package com.android.blemonitor.presentation.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import com.android.blemonitor.data.model.BleDevice
import com.android.blemonitor.repository.BleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BleViewModel(
    private val repository: BleRepository
) : ViewModel() {

    val connectionState = repository.connectionState
    val batteryLevel = repository.batteryLevel

    private val _discoveredDevices =
        MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDevice>> =
        _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> =
        _connectedDeviceName.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var activeScanner: BluetoothLeScanner? = null
    private var activeCallback: ScanCallback? = null
    private var timeoutRunnable: Runnable? = null

    @SuppressLint("MissingPermission")
    fun startScan(bluetoothAdapter: BluetoothAdapter?) {
        stopCurrentScan()
        _scanError.value = null
        _discoveredDevices.value = emptyList()

        if (bluetoothAdapter == null) {
            _scanError.value = "Bluetooth is not available on this device."
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            _scanError.value = "Bluetooth is turned off. Please enable it."
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            _scanError.value = "BLE scanning is unavailable. Try again."
            return
        }

        val callback = object : ScanCallback() {

            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {
                val device = result.device
                val address = device.address

                val newDevice = BleDevice(
                    name = device.name ?: "Unknown Device",
                    address = address,
                    rssi = result.rssi
                )

                val updatedList =
                    _discoveredDevices.value.toMutableList()
                val index = updatedList.indexOfFirst {
                    it.address == address
                }

                if (index >= 0) {
                    updatedList[index] = newDevice
                } else {
                    updatedList.add(newDevice)
                }

                _discoveredDevices.value = updatedList
            }

            override fun onScanFailed(errorCode: Int) {
                if (activeCallback !== this) return

                stopCurrentScan()

                _scanError.value = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED ->
                        "A scan is already running. Please try again."

                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
                        "Android could not register the scan. Try again."

                    SCAN_FAILED_INTERNAL_ERROR ->
                        "Bluetooth encountered an internal error."

                    SCAN_FAILED_FEATURE_UNSUPPORTED ->
                        "BLE scanning is not supported."

                    SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES ->
                        "Bluetooth resources are busy. Try again later."

                    SCAN_FAILED_SCANNING_TOO_FREQUENTLY ->
                        "Too many scan requests. Wait a moment and retry."

                    else -> "BLE scan failed (error $errorCode)."
                }
            }
        }

        activeScanner = scanner
        activeCallback = callback

        try {
            scanner.startScan(callback)
            _isScanning.value = true

            val timeout = Runnable {
                stopCurrentScan()
            }
            timeoutRunnable = timeout
            handler.postDelayed(timeout, 10_000L)

        } catch (e: SecurityException) {
            stopCurrentScan()
            _scanError.value =
                "Bluetooth permission is missing. Please grant permission."

        } catch (e: IllegalStateException) {
            stopCurrentScan()
            _scanError.value =
                "Bluetooth is unavailable right now. Please try again."
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopCurrentScan() {
        timeoutRunnable?.let(handler::removeCallbacks)
        timeoutRunnable = null

        val scanner = activeScanner
        val callback = activeCallback

        activeScanner = null
        activeCallback = null
        _isScanning.value = false

        if (scanner != null && callback != null) {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
                // Permission may have been revoked.
            } catch (_: IllegalStateException) {
                // Bluetooth may have become unavailable.
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(
        bluetoothAdapter: BluetoothAdapter?,
        address: String
    ) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _scanError.value =
                "Bluetooth is unavailable. Enable it before connecting."
            return
        }

        try {
            val device = bluetoothAdapter.getRemoteDevice(address)

            val name = _discoveredDevices.value
                .find { it.address == address }
                ?.name
                ?: device.name
                ?: "Unknown Device"

            _connectedDeviceName.value = name
            _scanError.value = null
            repository.connect(device)

        } catch (_: IllegalArgumentException) {
            _scanError.value = "Invalid Bluetooth device address."
        } catch (_: SecurityException) {
            _scanError.value =
                "Bluetooth permission is missing. Please grant permission."
        }
    }

    fun disconnect() {
        repository.disconnect()
        _connectedDeviceName.value = null
    }

    override fun onCleared() {
        stopCurrentScan()
        super.onCleared()
    }
}