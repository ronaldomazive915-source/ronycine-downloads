package com.example.ui.screens.admin

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.ImportConfig
import com.example.data.remote.ImportItem
import com.example.data.remote.ImportJob
import com.example.data.remote.ImportSummary
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MassImportCentral(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val importJobs by adminViewModel.importJobs.collectAsState()
    val selectedJobId by adminViewModel.selectedJobId.collectAsState()
    val currentJob by adminViewModel.currentImportJob.collectAsState()
    val currentItems by adminViewModel.currentImportItems.collectAsState()
    val globalStats by adminViewModel.globalImportStats.collectAsState()
    
    var showNewJobDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Dashboard Header
        ImportDashboardHeader(
            stats = globalStats,
            onNewJob = { showNewJobDialog = true },
            onRefresh = { adminViewModel.refreshGlobalStats() }
        )
        
        if (selectedJobId == null) {
            // Jobs List
            JobsList(
                jobs = importJobs,
                onSelectJob = { adminViewModel.selectJob(it.id) }
            )
        } else {
            // Job Detail
            JobDetailScreen(
                job = currentJob,
                items = currentItems,
                onBack = { adminViewModel.selectJob(null) },
                onPause = { adminViewModel.pauseJob(it) },
                onResume = { adminViewModel.resumeJob(it) },
                onCancel = { adminViewModel.cancelJob(it) },
                onReprocessFailed = { adminViewModel.reprocessFailed(it) }
            )
        }
    }
    
    if (showNewJobDialog) {
        NewImportJobDialog(
            onDismiss = { showNewJobDialog = false },
            onStart = { type, source, ids, config ->
                adminViewModel.startNewMassImport(type, source, ids, config)
                showNewJobDialog = false
            }
        )
    }
}

@Composable
fun ImportDashboardHeader(
    stats: ImportSummary,
    onNewJob: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        color = DarkSurface,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "CENTRAL DE IMPORTAÇÃO",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Gerencie importações em massa com segurança",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = Color.LightGray)
                    }
                    
                    Button(
                        onClick = onNewJob,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("NOVA IMPORTAÇÃO", fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "Filmes",
                    value = stats.totalMovies.toString(),
                    icon = Icons.Default.Movie,
                    color = BrandRed,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Séries",
                    value = stats.totalSeries.toString(),
                    icon = Icons.Default.Tv,
                    color = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Ativas",
                    value = stats.activeJobsCount.toString(),
                    icon = Icons.Default.RunningWithErrors,
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
        border = BorderStroke(1.dp, CardBorder.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun JobsList(
    jobs: List<ImportJob>,
    onSelectJob: (ImportJob) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "RECENTES",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        
        if (jobs.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("Nenhuma importação encontrada", color = Color.DarkGray)
                }
            }
        } else {
            items(jobs) { job ->
                JobItem(job = job, onClick = { onSelectJob(job) })
            }
        }
    }
}

@Composable
fun JobItem(job: ImportJob, onClick: () -> Unit) {
    val progress = if (job.total > 0) job.processed.toFloat() / job.total else 0f
    val date = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(job.createdAt))
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(job.status)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (job.source == "tmdb_ids") "IDs TMDB" else "Importação",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Text(date, color = Color.Gray, fontSize = 11.sp)
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${job.processed} de ${job.total} processados",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = when (job.status) {
                    "completed" -> Color(0xFF10B981)
                    "failed" -> BrandRed
                    "processing" -> BrandRed
                    else -> Color.Gray
                },
                trackColor = Color.White.copy(alpha = 0.05f)
            )
            
            if (job.status == "processing" || job.status == "queued") {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    JobMiniStat("Sucesso", job.success, Color(0xFF10B981))
                    JobMiniStat("Falha", job.failed, BrandRed)
                    JobMiniStat("Dupl.", job.duplicates, Color(0xFF3B82F6))
                }
            }
        }
    }
}

@Composable
fun JobMiniStat(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text("$label: $count", color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable
fun StatusBadge(status: String) {
    val (label, color) = when (status) {
        "queued" -> "AGUARDANDO" to Color.Gray
        "processing" -> "PROCESSANDO" to Color(0xFF3B82F6)
        "paused" -> "PAUSADO" to Color(0xFFF59E0B)
        "completed" -> "CONCLUÍDO" to Color(0xFF10B981)
        "cancelled" -> "CANCELADO" to Color.DarkGray
        "failed" -> "FALHOU" to BrandRed
        else -> status.uppercase() to Color.Gray
    }
    
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun JobDetailScreen(
    job: ImportJob?,
    items: List<ImportItem>,
    onBack: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onReprocessFailed: (String) -> Unit
) {
    if (job == null) return
    
    val scrollState = rememberScrollState()
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Sub Header
        Surface(color = DarkSurface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Detalhes do Job", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("ID: ${job.id}", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                JobStatusCard(
                    job = job,
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = onCancel,
                    onReprocessFailed = onReprocessFailed
                )
            }
            
            item {
                Text(
                    "LOG DE PROCESSAMENTO",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            
            if (items.isEmpty()) {
                item {
                    Text("Nenhum item processado ainda.", color = Color.DarkGray, modifier = Modifier.padding(16.dp))
                }
            } else {
                items(items) { item ->
                    ImportItemRow(item)
                }
            }
        }
    }
}

@Composable
fun JobStatusCard(
    job: ImportJob,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onReprocessFailed: (String) -> Unit
) {
    val progress = if (job.total > 0) job.processed.toFloat() / job.total else 0f
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(job.status)
                if (job.status == "processing") {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = BrandRed
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text("Progresso Geral", color = Color.Gray, fontSize = 12.sp)
                    Text(
                        "${job.processed} de ${job.total} itens",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text("${(progress * 100).toInt()}%", color = BrandRed, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
            
            Spacer(Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape),
                color = BrandRed,
                trackColor = Color.White.copy(alpha = 0.05f)
            )
            
            Spacer(Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailStatBox("Sucedidos", job.success, Color(0xFF10B981), Modifier.weight(1f))
                DetailStatBox("Falhas", job.failed, BrandRed, Modifier.weight(1f))
                DetailStatBox("Duplicados", job.duplicates, Color(0xFF3B82F6), Modifier.weight(1f))
            }
            
            if (job.lastError != null) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = BrandRed.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.2f))
                ) {
                    Text(
                        job.lastError,
                        color = BrandRed,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (job.status) {
                    "processing" -> {
                        Button(
                            onClick = { onPause(job.id) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Pause, null)
                            Spacer(Modifier.width(8.dp))
                            Text("PAUSAR")
                        }
                    }
                    "paused" -> {
                        Button(
                            onClick = { onResume(job.id) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("RETOMAR")
                        }
                    }
                }
                
                if (job.status != "completed" && job.status != "cancelled") {
                    Button(
                        onClick = { onCancel(job.id) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("CANCELAR", color = Color.Gray)
                    }
                }
                
                if (job.failed > 0 && (job.status == "completed" || job.status == "paused" || job.status == "failed")) {
                    Button(
                        onClick = { onReprocessFailed(job.id) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("REPROCESSAR FALHAS")
                    }
                }
            }
        }
    }
}

@Composable
fun DetailStatBox(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(count.toString(), color = color, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(label, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ImportItemRow(item: ImportItem) {
    val date = item.processedAt?.let { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it)) } ?: ""
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when (item.status) {
                        "success" -> Color(0xFF10B981).copy(alpha = 0.1f)
                        "failed" -> BrandRed.copy(alpha = 0.1f)
                        "duplicate" -> Color(0xFF3B82F6).copy(alpha = 0.1f)
                        else -> Color.White.copy(alpha = 0.05f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (item.status) {
                    "success" -> Icons.Default.Check
                    "failed" -> Icons.Default.Error
                    "duplicate" -> Icons.Default.History
                    else -> Icons.Default.Pending
                },
                contentDescription = null,
                tint = when (item.status) {
                    "success" -> Color(0xFF10B981)
                    "failed" -> BrandRed
                    "duplicate" -> Color(0xFF3B82F6)
                    else -> Color.Gray
                },
                modifier = Modifier.size(16.dp)
            )
        }
        
        Spacer(Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title ?: "TMDB ID: ${item.tmdbId}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.error != null) {
                Text(item.error, color = BrandRed.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1)
            } else {
                Text("${item.mediaType.uppercase()} • $date", color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewImportJobDialog(
    onDismiss: () -> Unit,
    onStart: (String, String, List<Pair<Int, String>>, ImportConfig) -> Unit
) {
    var source by remember { mutableStateOf("tmdb_ids") }
    var pastedIds by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("movie") }
    
    var concurrentWorkers by remember { mutableFloatStateOf(5f) }
    var updateExisting by remember { mutableStateOf(false) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Configurar Importação", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = Color.Gray)
                    }
                }
                
                Spacer(Modifier.height(20.dp))
                
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Source Selection
                    Column {
                        Text("FONTE DE DADOS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SourceChip("Lista de IDs", source == "tmdb_ids", { source = "tmdb_ids" }, Modifier.weight(1f))
                            SourceChip("Busca TMDB", source == "tmdb_search", { source = "tmdb_search" }, Modifier.weight(1f))
                        }
                    }
                    
                    if (source == "tmdb_ids") {
                        OutlinedTextField(
                            value = pastedIds,
                            onValueChange = { pastedIds = it },
                            placeholder = { Text("Ex: 550, 603, 157336...", color = Color.Gray) },
                            label = { Text("IDs TMDB (separados por vírgula)") },
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = CardBorder
                            )
                        )
                        
                        Column {
                            Text("TIPO DE CONTEÚDO", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SourceChip("Filmes", type == "movie", { type = "movie" }, Modifier.weight(1f))
                                SourceChip("Séries", type == "tv", { type = "tv" }, Modifier.weight(1f))
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().height(100.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Text("Funcionalidade de busca TMDB em breve...", color = Color.Gray)
                        }
                    }
                    
                    // Config
                    Column {
                        Text("CONFIGURAÇÕES TÉCNICAS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Trabalhadores Simultâneos: ${concurrentWorkers.toInt()}", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Slider(
                                value = concurrentWorkers,
                                onValueChange = { concurrentWorkers = it },
                                valueRange = 1f..10f,
                                steps = 9,
                                modifier = Modifier.weight(1.5f),
                                colors = SliderDefaults.colors(thumbColor = BrandRed, activeTrackColor = BrandRed)
                            )
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = updateExisting,
                                onCheckedChange = { updateExisting = it },
                                colors = CheckboxDefaults.colors(checkedColor = BrandRed)
                            )
                            Text("Atualizar registros existentes", color = Color.LightGray, fontSize = 13.sp)
                        }
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        val ids = pastedIds.split(",")
                            .mapNotNull { it.trim().toIntOrNull() }
                            .map { it to type }
                        
                        onStart(
                            type,
                            source,
                            ids,
                            ImportConfig(
                                concurrentWorkers = concurrentWorkers.toInt(),
                                updateExisting = updateExisting
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(16.dp),
                    enabled = source == "tmdb_ids" && pastedIds.isNotBlank()
                ) {
                    Text("INICIAR FILA DE IMPORTAÇÃO", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = if (selected) BrandRed.copy(alpha = 0.2f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) BrandRed else CardBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Text(
            label,
            color = if (selected) BrandRed else Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 10.dp)
        )
    }
}

@Composable
fun InfoBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text,
            color = Color.LightGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            textAlign = TextAlign.Center
        )
    }
}
