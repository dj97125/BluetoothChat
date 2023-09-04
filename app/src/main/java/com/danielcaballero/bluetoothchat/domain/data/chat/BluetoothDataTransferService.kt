package com.danielcaballero.bluetoothchat.domain.data.chat

import android.bluetooth.BluetoothSocket
import com.danielcaballero.bluetoothchat.domain.chat.TransferFailedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException




class BluetoothDataTransferService(
    private val socket: BluetoothSocket
) {

    fun listenForIncomingMessagees(): Flow<BluetoothMessage> = flow {
        if (!socket.isConnected) return@flow
        val buffer = ByteArray(1024)
        while (true) {
            val byteCount = try {
                socket.inputStream.read(buffer)
            } catch (e: IOException) {
                throw TransferFailedException()
            }
            emit(
                buffer.decodeToString(
                    endIndex = byteCount
                ).toBluethoothMessagee(
                    isFromLocalUser = false
                )
            )

        }
    }.flowOn(Dispatchers.IO)

    suspend fun sendMessage(bytes: ByteArray): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                socket.outputStream.write(bytes)
            } catch (e: IOException) {
                e.printStackTrace()
                return@withContext false
            }

            true
        }
    }

}