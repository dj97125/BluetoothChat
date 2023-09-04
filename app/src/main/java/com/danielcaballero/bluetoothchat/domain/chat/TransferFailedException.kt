package com.danielcaballero.bluetoothchat.domain.chat

import java.io.IOException

class TransferFailedException: IOException("Reading incoming data failed")