package com.android.blemonitor.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class BleForegroundService : Service() {

    private val binder = LocalBinder()
    private var bluetoothGatt: BluetoothGatt? = null
    private var targetDeviceAddress: String? = null
    private var targetDevice: BluetoothDevice? = null
    private var isUserInitiatedDisconnect: Boolean = false

    inner class LocalBinder : Binder() {
        fun getService(): BleForegroundService = this@BleForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()


        startForeground(

            NOTIFICATION_ID, buildNotification(
                "BLE Service Active", "Disconnected"
            )
        )
    }


    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        android.util.Log.d(
            "BLE_TRACE",
            "Connecting to ${device.address}"
        )

        isUserInitiatedDisconnect = false
        targetDevice = device
        targetDeviceAddress = device.address

        _connectionState.value =
            "Connecting to ${device.name ?: device.address}"

        updateNotification(
            "BLE Monitor",
            "Connecting to ${device.name ?: device.address}..."
        )

        bluetoothGatt?.let {
            it.disconnect()
            it.close()
        }
        bluetoothGatt = null

        bluetoothGatt = device.connectGatt(
            this,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice() {
        targetDeviceAddress = null
        bluetoothGatt?.let {
            it.disconnect()
            it.close()
        }
        bluetoothGatt = null
        _connectionState.value = "Disconnected"
        _batteryLevel.value = null
        updateNotification("Disconnected", "No active BLE device")
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            android.util.Log.d(
                "BLE_TRACE",
                "GATT State Change -> Status: $status, New State: $newState"
            )

            if (status != BluetoothGatt.GATT_SUCCESS) {
                android.util.Log.e("BLE_TRACE", "GATT Error Status Code: $status")
                _connectionState.value = "Connection Failed (Status $status)"
                gatt.close()
                bluetoothGatt = null
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {

                    _connectionState.value = "Connected"

                    updateNotification(
                        "BLE Monitor • Connected",
                        "Monitoring ${gatt.device.name ?: gatt.device.address}"
                    )

                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = "Disconnected"
                    _batteryLevel.value = null

                    updateNotification(
                        "BLE Monitor",
                        "Device disconnected"
                    )

                    gatt.close()

                    if (bluetoothGatt === gatt) {
                        bluetoothGatt = null
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        private fun readClassicHeadsetBattery(device: BluetoothDevice): Int? {
            // 1. Try standard API 30+ Android extra parameter via reflection
            return try {
                val method = device.javaClass.getMethod("getBatteryLevel")
                val battery = method.invoke(device) as Int
                if (battery in 0..100) battery else null
            } catch (e: Exception) {
                null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            android.util.Log.d("BLE_TRACE", "onServicesDiscovered -> Status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val batteryService = gatt.getService(BATTERY_SERVICE_UUID)

                if (batteryService != null) {
                    val batteryChar = batteryService.getCharacteristic(BATTERY_LEVEL_UUID)
                    if (batteryChar != null) {
                        android.util.Log.d(
                            "BLE_TRACE",
                            "Reading standard BLE Battery Characteristic..."
                        )
                        gatt.readCharacteristic(batteryChar)
                        return
                    }
                }

                // Fallback for Bluetooth Audio Earbuds (Classic HFP/A2DP Profile Battery)
                android.util.Log.w(
                    "BLE_TRACE",
                    "Standard BLE Battery Service missing. Checking Headset Profile Battery..."
                )
                val classicBattery = readClassicHeadsetBattery(gatt.device)

                if (classicBattery != null) {
                    android.util.Log.d("BLE_TRACE", "Headset Battery Level found: $classicBattery%")
                    _batteryLevel.value = classicBattery
                } else {
                    android.util.Log.e(
                        "BLE_TRACE",
                        "Battery reporting not supported via GATT or Classic profile for this device."
                    )
                    _batteryLevel.value = null
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (
                status == BluetoothGatt.GATT_SUCCESS &&
                characteristic.uuid == BATTERY_LEVEL_UUID &&
                value.isNotEmpty()
            ) {
                val level = value[0].toInt() and 0xFF

                if (level in 0..100) {
                    _batteryLevel.value = level

                    updateNotification(
                        "BLE Monitor",
                        "${gatt.device.name ?: gatt.device.address} • Battery: $level%"
                    )
                }
            }
        }
    }

    private fun updateNotification(title: String, content: String) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    private fun buildNotification(
        title: String,
        content: String
    ): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BLE Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "ble_monitoring_channel"

        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        private val _connectionState = MutableStateFlow("Disconnected")
        val connectionState: StateFlow<String> = _connectionState.asStateFlow()

        private val _batteryLevel = MutableStateFlow<Int?>(null)
        val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()
    }
}