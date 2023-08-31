package com.danielcaballero.bluetoothchat.domain.data.chat

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import com.danielcaballero.bluetoothchat.domain.chat.BluetoothDeviceDomain

@SuppressLint("MissingPermission")
fun BluetoothDevice.toBluetoothDeviceDomain(): BluetoothDeviceDomain {
    return BluetoothDeviceDomain(
        name = name,
        address = address
    )
}