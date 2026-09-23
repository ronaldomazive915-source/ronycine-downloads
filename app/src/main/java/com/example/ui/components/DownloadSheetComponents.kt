package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadTask
import com.example.util.DownloadUtils

@Composable
fun CompactDownloadOptionCard(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    description: String,
    isInstalled: Boolean?,
    isRecommended: Boolean = false,
    statusNotice: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color(0xFF15151F),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isRecommended) Color(0xFF8B5CF6).copy(alpha = 0.4f) else Color(0xFF1E1E2C)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(iconBg, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Description & Status
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (isRecommended) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "RECOMENDADO",
                                color = Color(0xFFC4B5FD),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    if (statusNotice != null) {
                        Text(
                            text = statusNotice,
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = description,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isInstalled != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isInstalled) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF10B981), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Instalado",
                                color = Color(0xFF10B981),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "Toque para instalar na Play Store",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF4B5563),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun ActiveDownloadInlineCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        color = Color(0xFF13131D),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262638)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!task.posterPath.isNullOrBlank()) {
                    AsyncImage(
                        model = task.posterPath,
                        contentDescription = null,
                        modifier = Modifier
                            .width(36.dp)
                            .height(52.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1F1F2C)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!task.subTitle.isNullOrBlank()) {
                        Text(
                            text = task.subTitle,
                            color = Color(0xFFA78BFA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    val progressFloat = if (task.totalSize > 0) task.downloadedSize.toFloat() / task.totalSize else 0f
                    val percentInt = (progressFloat * 100).toInt().coerceIn(0, 100)

                    val statusDesc = when (task.status) {
                        DownloadStatus.QUEUED -> "Na fila..."
                        DownloadStatus.PREPARING -> "Preparando..."
                        DownloadStatus.DOWNLOADING -> {
                            val speedStr = if (task.speed > 0) " • ${DownloadUtils.formatDownloadSpeed(task.speed)}" else ""
                            "$percentInt% • ${DownloadUtils.formatFileSize(task.downloadedSize)} / ${DownloadUtils.formatFileSize(task.totalSize)}$speedStr"
                        }
                        DownloadStatus.PAUSED -> "Pausado em $percentInt%"
                        DownloadStatus.COMPLETED -> "Concluído • ${DownloadUtils.formatFileSize(task.totalSize)}"
                        DownloadStatus.ERROR -> task.errorMessage ?: "Erro no download"
                        DownloadStatus.CANCELED -> "Cancelado"
                        DownloadStatus.IDLE -> "Parado"
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusDesc,
                        color = if (task.status == DownloadStatus.COMPLETED) Color(0xFF10B981)
                                else if (task.status == DownloadStatus.ERROR) Color(0xFFEF4444)
                                else Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.status == DownloadStatus.DOWNLOADING) {
                        IconButton(onClick = onPause, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Pause, contentDescription = "Pausar", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                        }
                    } else if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.ERROR) {
                        IconButton(onClick = onResume, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Continuar", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                        }
                    }

                    IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PAUSED) {
                Spacer(modifier = Modifier.height(8.dp))
                val progressFloat = if (task.totalSize > 0) (task.downloadedSize.toFloat() / task.totalSize).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = { progressFloat },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF8B5CF6),
                    trackColor = Color(0xFF262638)
                )
            }
        }
    }
}
