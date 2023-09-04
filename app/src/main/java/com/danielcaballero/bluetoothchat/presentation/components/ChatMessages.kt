package com.danielcaballero.bluetoothchat.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielcaballero.bluetoothchat.domain.data.chat.BluetoothMessage
import com.danielcaballero.bluetoothchat.ui.theme.BluetoothChatTheme
import com.danielcaballero.bluetoothchat.ui.theme.OldRose
import com.danielcaballero.bluetoothchat.ui.theme.Vanilla

@Composable
fun ChatMessage(
    messagee: BluetoothMessage,
    modifier: Modifier = Modifier


) {
    Column(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    topStart = if (messagee.isFromLocalUser) 15.dp else 0.dp,
                    topEnd = 15.dp,
                    bottomStart = 15.dp,
                    bottomEnd = if (messagee.isFromLocalUser) 0.dp else 15.dp
                )
            )
            .background(
                if (messagee.isFromLocalUser) OldRose else Vanilla
            )
            .padding(16.dp)
    ) {
        Text(text = messagee.senderName, fontSize = 10.sp, color = Color.Black)

        Text(
            text = messagee.messagee,
            modifier = Modifier.widthIn(max = 250.dp),
            color = Color.Black
        )

    }

}


@Preview
@Composable
fun ChatMessagePreview() {
    BluetoothChatTheme {
        ChatMessage(
            messagee = BluetoothMessage(
                messagee = "Hello World",
                senderName = "Samsung",
                isFromLocalUser = true
            )
        )

    }
}