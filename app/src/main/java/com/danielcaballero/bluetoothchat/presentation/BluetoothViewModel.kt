package com.danielcaballero.bluetoothchat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielcaballero.bluetoothchat.domain.chat.BluetoothDevice
import com.danielcaballero.bluetoothchat.domain.chat.BluetoothDeviceDomain
import com.danielcaballero.bluetoothchat.domain.data.chat.BluetoothController
import com.danielcaballero.bluetoothchat.domain.data.chat.BluetoothMessage
import com.danielcaballero.bluetoothchat.domain.data.chat.ConnectionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BluetoothUiState(
    val scannedDevices: List<BluetoothDevice> = emptyList(),
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val errorMessage: String? = null,
    val message: List<BluetoothMessage> = emptyList()

)

@HiltViewModel
class BluetoothViewModel @Inject constructor(
    private val bluetoothController: BluetoothController
) : ViewModel() {

    private val _state = MutableStateFlow(BluetoothUiState())

    val state = combine(
        bluetoothController.scannedDevices,
        bluetoothController.pairedDevices,
        _state
    ) { scannedDevices, pairedDeevices, state ->

        state.copy(
            scannedDevices = scannedDevices,
            pairedDevices = pairedDeevices,
            message = if (state.isConnected) state.message else emptyList()
        )

    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)

    private var deviceConnectionJob: Job? = null

    init {
        bluetoothController.isConnected.onEach { isConnected ->
            _state.update {
                it.copy(isConnected = isConnected) }

        }.launchIn(viewModelScope)

        bluetoothController.errors.onEach { error ->
            _state.update {
                it.copy(
                    errorMessage = error
                )

            }

        }.launchIn(viewModelScope)
    }

    fun connectToDevice(device: BluetoothDeviceDomain) {
        _state.update { it.copy(isConnecting = true) }
        deviceConnectionJob = bluetoothController.conneectToDevice(device).listen()
    }

    fun disconnectFromDevice() {
        deviceConnectionJob?.cancel()
        bluetoothController.closeConnection()
        _state.update {
            it.copy(
                isConnecting = false,
                isConnected = false
            )
        }
    }

    fun waitForIncomingConnections(){
        _state.update { it.copy(isConnecting = true) }
        deviceConnectionJob = bluetoothController.startBluetoothServer().listen()
    }

    fun startScan() {
        bluetoothController.startDiscovery()
    }

    fun stopScan() {
        bluetoothController.stopDiscovery()

    }

    fun sendMessagee(message: String) {
        viewModelScope.launch {
            val bluetoothMessage = bluetoothController.trySendMessage(message)
            bluetoothMessage?.let {
                _state.update {
                    it.copy(
                        message = it.message + bluetoothMessage
                    )
                }
            }
        }
    }

    private fun Flow<ConnectionResult>.listen(): Job {
        return onEach { result ->
            when (result) {
                ConnectionResult.ConnectionStablished -> {
                    _state.update {
                        it.copy(
                            isConnected = true,
                            isConnecting = false,
                            errorMessage = null
                        )
                    }
                }

                is ConnectionResult.Error -> {
                    _state.update {
                        it.copy(
                            isConnected = false,
                            isConnecting = false,
                            errorMessage = result.message
                        )
                    }
                }

                is ConnectionResult.TransferSucceded -> {
                    _state.update {
                        it.copy(
                            message = it.message + result.message
                        )
                    }
                }
            }

        }.catch {
            bluetoothController.closeConnection()
            _state.update {
                it.copy(
                    isConnected = false,
                    isConnecting = false,
                )
            }
        }.launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothController.release()
    }
}

