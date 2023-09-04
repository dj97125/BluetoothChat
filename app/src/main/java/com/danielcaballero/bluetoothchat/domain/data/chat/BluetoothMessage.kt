package com.danielcaballero.bluetoothchat.domain.data.chat

data class BluetoothMessage(
    val messagee: String,
    val senderName: String,
    val isFromLocalUser: Boolean
)