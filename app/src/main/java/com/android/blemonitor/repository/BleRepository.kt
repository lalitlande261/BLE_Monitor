package com.android.blemonitor.repository



import android.bluetooth.BluetoothDevice
import android.util.Log
import com.android.blemonitor.service.BleForegroundService
import kotlinx.coroutines.flow.StateFlow

class BleRepository {
    private var bleService: BleForegroundService? = null

    fun bindService(service: BleForegroundService) {
        Log.d("BLE_TRACE", "Repository: bleService instance attached successfully.")
        bleService = service
    }


    fun unbindService() {
        Log.w("BLE_TRACE", "Repository: bleService instance detached.")
        bleService = null
    }

    val connectionState: StateFlow<String> = BleForegroundService.connectionState
    val batteryLevel: StateFlow<Int?> = BleForegroundService.batteryLevel

    fun connect(device: BluetoothDevice) {
        if (bleService == null) {
            Log.e("BLE_TRACE", "ERROR: bleService is NULL when trying to connect. Re-check Service Connection in MainActivity.")
            return
        }

        Log.d("BLE_TRACE", "Repository forwarding connect request to service for device: ${device.address}")
        bleService?.connectToDevice(device)
    }

    fun disconnect() {
        bleService?.disconnectDevice()
    }
}