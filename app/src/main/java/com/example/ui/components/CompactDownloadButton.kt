package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CompactDownloadButton(
    isResolving: Boolean,
    isError: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !isResolving,
        modifier = modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isError) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF2A2A32),
            contentColor = if (isError) Color(0xFFEF4444) else Color.White,
            disabledContainerColor = Color(0xFF2A2A32).copy(alpha = 0.5f)
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isResolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Preparando...", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            } else if (isError) {
                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Erro", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Baixar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
