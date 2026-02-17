package org.bothubclient.presentation.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        backgroundColor = Color(0xFFB71C1C),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = message,
            color = Color.White,
            modifier = Modifier.padding(12.dp),
            fontSize = 12.sp
        )
    }
}
