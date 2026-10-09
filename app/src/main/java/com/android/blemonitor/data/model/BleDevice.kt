package com.android.blemonitor.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val isConnected: Boolean = false,
    val batteryLevel: Int? = null
) : Parcelable