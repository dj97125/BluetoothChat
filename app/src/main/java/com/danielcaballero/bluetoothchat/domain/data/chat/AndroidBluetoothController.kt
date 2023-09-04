package com.danielcaballero.bluetoothchat.domain.data.chat

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import com.danielcaballero.bluetoothchat.domain.chat.BluetoothDeviceDomain
import com.danielcaballero.bluetoothchat.domain.chat.BluetoothStateReceiver
import com.danielcaballero.bluetoothchat.domain.chat.FoundDeviceReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID


interface BluetoothController {
    val isConnected: StateFlow<Boolean>
    val scannedDevices: StateFlow<List<BluetoothDeviceDomain>>
    val pairedDevices: StateFlow<List<BluetoothDeviceDomain>>
    val errors: SharedFlow<String>

    fun startDiscovery()
    fun stopDiscovery()

    fun release()

    fun startBluetoothServer(): Flow<ConnectionResult>

    fun conneectToDevice(device: BluetoothDeviceDomain): Flow<ConnectionResult>

    suspend fun trySendMessage(message: String): BluetoothMessage?

    fun closeConnection()


}

@SuppressLint("MissingPermission")
class AndroidBluetoothController(
    private val context: Context
) : BluetoothController {

    private val bluetoothManager by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }
    private val bluetoothAdapter by lazy {
        bluetoothManager?.adapter
    }

    private var dataTransferService: BluetoothDataTransferService? = null

    private val _scannedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())

    private val _isConnected = MutableStateFlow<Boolean>(false)
    override val isConnected: StateFlow<Boolean>
        get() = _isConnected.asStateFlow()
    override val scannedDevices: StateFlow<List<BluetoothDeviceDomain>>
        get() = _scannedDevices.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val pairedDevices: StateFlow<List<BluetoothDeviceDomain>>
        get() = _pairedDevices.asStateFlow()

    private val _errors = MutableSharedFlow<String>()
    override val errors: SharedFlow<String>
        get() = _errors.asSharedFlow()

    private val foundDeviceReceiver = FoundDeviceReceiver { device ->
        _scannedDevices.update { devices ->
            val newDevice = device.toBluetoothDeviceDomain()
            if(newDevice in devices) devices else devices + newDevice
        }
    }

    private val bluetoothStateReceiver = BluetoothStateReceiver { isConnected, bluetoothDevice ->

        if(bluetoothAdapter?.bondedDevices?.contains(bluetoothDevice) == true){
            _isConnected.update { isConnected }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                _errors.emit("Can't connect to a non-paired device")
            }

        }

    }

    init {
        updatePairedDevices()
        context.registerReceiver(
            bluetoothStateReceiver,
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
        )
    }

    override fun startDiscovery() {

        if (
            !hasPermission(Manifest.permission.BLUETOOTH_SCAN)
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            return
        }

        if (
            !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            && Build.VERSION.SDK_INT <= Build.VERSION_CODES.S
        ) {
            return
        }

        context.registerReceiver(
            foundDeviceReceiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND)
        )

        updatePairedDevices()

        bluetoothAdapter?.startDiscovery()
    }

    override fun stopDiscovery() {

        if (
            !hasPermission(Manifest.permission.BLUETOOTH_SCAN)
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            return
        }

        if (
            !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            && Build.VERSION.SDK_INT <= Build.VERSION_CODES.S
        ) {
            return
        }

        bluetoothAdapter?.cancelDiscovery()
    }

    override fun release() {
        context.unregisterReceiver(foundDeviceReceiver)
        context.unregisterReceiver(bluetoothStateReceiver)
        closeConnection()
    }



    override fun startBluetoothServer(): Flow<ConnectionResult> = flow {

        if (
            !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            throw SecurityException("No BLUETOOTH permission")
        }

        if (
            !hasPermission(Manifest.permission.BLUETOOTH_ADMIN)
            && Build.VERSION.SDK_INT <= Build.VERSION_CODES.S
        ) {
            throw SecurityException("No BLUETOOTH permission")
        }



        currentServerSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
            "chat_service",
            UUID.fromString(SERVICE_UUID)
        )

        var shouldLoop = true
        while (shouldLoop) {
            currentClientSocket = try {
                currentServerSocket?.accept()
            } catch (e: IOException) {
                shouldLoop = false
                null

            }
            emit(ConnectionResult.ConnectionStablished)
            currentClientSocket?.let {
                currentServerSocket?.close()
                val service = BluetoothDataTransferService(it)
                dataTransferService = service

                emitAll(
                    service.listenForIncomingMessagees()
                        .map { ConnectionResult.TransferSucceded(it) })
            }
        }
    }.onCompletion { closeConnection() }.flowOn(Dispatchers.IO)

    private var currentServerSocket: BluetoothServerSocket? = null
    private var currentClientSocket: BluetoothSocket? = null

    override fun conneectToDevice(device: BluetoothDeviceDomain): Flow<ConnectionResult> =
        flow {

            if (
                !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                throw SecurityException("No BLUETOOTH permission")
            }

            if (
                !hasPermission(Manifest.permission.BLUETOOTH_ADMIN)
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.S
            ) {
                throw SecurityException("No BLUETOOTH permission")
            }

            val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)

            currentClientSocket = bluetoothAdapter?.getRemoteDevice(device.address)
                ?.createRfcommSocketToServiceRecord(
                    UUID.fromString(
                        SERVICE_UUID
                    )
                )
            stopDiscovery()

            if(bluetoothAdapter?.bondedDevices?.contains(bluetoothDevice) == false) {

            }

            currentClientSocket?.let { socket ->
                try {
                    socket.connect()
                    emit(ConnectionResult.ConnectionStablished)

                    BluetoothDataTransferService(socket).also {
                        dataTransferService = it
                        emitAll(
                            it.listenForIncomingMessagees()
                                .map { ConnectionResult.TransferSucceded(it) })
                    }
                } catch (e: IOException) {
                    socket.close()
                    currentClientSocket = null
                    emit(ConnectionResult.Error("Connection was interrupted"))
                }
            }
        }.onCompletion { closeConnection() }.flowOn(Dispatchers.IO)

    override suspend fun trySendMessage(message: String): BluetoothMessage? {
        if (
            !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            return null
        }

        if (
            !hasPermission(Manifest.permission.BLUETOOTH_ADMIN)
            && Build.VERSION.SDK_INT <= Build.VERSION_CODES.S
        ) {
            return null
        }

        if(dataTransferService == null) {
            return null
        }

        val bluetoothMessagee = BluetoothMessage(
            messagee = message,
            senderName = bluetoothAdapter?.name ?: "Unknown name",
            isFromLocalUser = true
        )

        dataTransferService?.sendMessage(bluetoothMessagee.toByteArray())

        return bluetoothMessagee


    }

    override fun closeConnection() {
        currentClientSocket?.close()
        currentServerSocket?.close()
        currentClientSocket = null
        currentServerSocket = null
    }

    private fun updatePairedDevices() {

        if (
            !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            return
        }

        if (
            !hasPermission(Manifest.permission.BLUETOOTH_ADMIN)
            && Build.VERSION.SDK_INT <= Build.VERSION_CODES.S
        ) {
            return
        }

        bluetoothAdapter
            ?.bondedDevices
            ?.map { it.toBluetoothDeviceDomain() }
            ?.also { devices ->
                _pairedDevices.update { devices }
            }
    }

    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val SERVICE_UUID = "27b7d1da-08c7-4505-a6d1-2459987e5e2d"
    }
}

@SuppressLint("MissingPermission")
fun BluetoothDevice.toBluetoothDeviceDomain(): BluetoothDeviceDomain {
    return BluetoothDeviceDomain(
        name = name,
        address = address
    )
}



