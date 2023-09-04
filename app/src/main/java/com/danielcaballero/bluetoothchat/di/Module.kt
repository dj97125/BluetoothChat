package com.danielcaballero.bluetoothchat.di

import android.content.Context
import com.danielcaballero.bluetoothchat.domain.data.chat.AndroidBluetoothController
import com.danielcaballero.bluetoothchat.domain.data.chat.BluetoothController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
interface Module {

    companion object {
        @Provides
        @Singleton
        fun provideBluetoothController(@ApplicationContext context: Context): BluetoothController =
            AndroidBluetoothController(context)
    }

}