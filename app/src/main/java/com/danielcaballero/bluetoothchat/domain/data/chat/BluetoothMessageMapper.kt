package com.danielcaballero.bluetoothchat.domain.data.chat


fun String.toBluethoothMessagee(isFromLocalUser: Boolean): BluetoothMessage {
    val name = substringBeforeLast("#")
    val message = substringAfter("#")

    return BluetoothMessage(
        messagee = message,
        senderName = name,
        isFromLocalUser = isFromLocalUser
    )

}

fun BluetoothMessage.toByteArray(): ByteArray {
    return "$senderName#$messagee".encodeToByteArray()
}