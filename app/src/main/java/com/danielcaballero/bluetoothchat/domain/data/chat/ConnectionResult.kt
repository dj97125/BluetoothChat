package com.danielcaballero.bluetoothchat.domain.data.chat


sealed interface ConnectionResult {
    object ConnectionStablished : ConnectionResult
    data class TransferSucceded(val message: BluetoothMessage) : ConnectionResult
    data class Error(val message: String) : ConnectionResult
}