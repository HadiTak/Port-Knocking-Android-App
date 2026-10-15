package ir.haditak.portknock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Bg = Color(0xFF0D1218)
private val PanelBg = Color(0xFF171E27)
private val InnerBg = Color(0xFF1E2833)
private val CircleBg = Color(0xFF222B36)
private val Accent = Color(0xFF4DD0C4)
private val Amber = Color(0xFFFFC107)
private val Good = Color(0xFF4CAF50)
private val Bad = Color(0xFFEF5350)
private val Muted = Color(0xFF9FB0C0)

private enum class CircleState { Normal, Active, Done }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PortKnockTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(Bg, Color(0xFF122030))))
                        ) {
                            App()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortKnockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color(0xFF00201D),
            background = Bg,
            surface = PanelBg,
            onSurface = Color(0xFFE6EDF3),
            onBackground = Color(0xFFE6EDF3),
            surfaceVariant = CircleBg,
            onSurfaceVariant = Muted
        ),
        content = content
    )
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Accent)
}

private fun Knock.label(): String =
    if (proto == Proto.ICMP) "ICMP" else "${proto.name} ${port.ifEmpty { "؟" }}"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    val saved = remember { Store.load(ctx) }
    var host by remember { mutableStateOf(saved.host) }
    var verify by remember { mutableStateOf(saved.verify) }
    var delayText by remember { mutableStateOf(saved.delay) }
    val knocks = remember { mutableStateListOf<Knock>().apply { addAll(saved.knocks) } }
    var phase by remember { mutableStateOf(Phase.Idle) }
    var step by remember { mutableIntStateOf(-1) }
    var message by remember { mutableStateOf("") }
    var editing by remember { mutableIntStateOf(-1) }
    var lastEditing by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val ltr = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
    val running = phase == Phase.Running

    LaunchedEffect(host, verify, delayText, knocks.toList()) {
        Store.save(ctx, host, verify, delayText, knocks.toList())
    }
    LaunchedEffect(editing) {
        if (editing >= 0) lastEditing = editing
    }

    val status = when (phase) {
        Phase.Idle -> "در بسته است 🔒"
        Phase.Running ->
            if (step in knocks.indices) "در حال ناک ${step + 1} از ${knocks.size}…"
            else "در حال بررسی اتصال…"
        Phase.Success -> "در باز شد ✅"
        Phase.Fail -> "در بسته ماند ❌"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("🚪", fontSize = 34.sp)
            Column {
                Text("Port Knock", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("ناک کن تا در باز شود", fontSize = 12.sp, color = Muted)
            }
        }

        // destination
        Panel {
            SectionTitle("مقصد")
            OutlinedTextField(
                value = host,
                onValueChange = { host = it.trim() },
                label = { Text("آدرس مقصد (IP یا دامنه)") },
                singleLine = true,
                enabled = !running,
                shape = RoundedCornerShape(14.dp),
                textStyle = ltr,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // knocks
        Panel {
            SectionTitle("ناک‌ها")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                knocks.forEachIndexed { i, k ->
                    val state = when {
                        running && i < step -> CircleState.Done
                        running && i == step -> CircleState.Active
                        phase == Phase.Success -> CircleState.Done
                        else -> CircleState.Normal
                    }
                    KnockCircle(
                        index = i,
                        k = k,
                        selected = editing == i,
                        state = state,
                        enabled = !running,
                        onClick = { editing = if (editing == i) -1 else i }
                    )
                }
                AddCircle(enabled = !running) {
                    knocks.add(Knock())
                    editing = knocks.lastIndex
                }
            }

            if (knocks.isEmpty()) {
                Text("هنوز ناکی نداری؛ با + اضافه کن.", fontSize = 12.sp, color = Muted)
            } else {
                Text(
                    "به ترتیب شماره اجرا می‌شوند. روی هر دایره بزن تا ویرایش شود.",
                    fontSize = 12.sp,
                    color = Muted
                )
            }

            AnimatedVisibility(
                visible = editing >= 0 && !running,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val idx = if (editing >= 0) editing else lastEditing
                val k = knocks.getOrNull(idx)
                if (k != null) {
                    KnockEditor(
                        index = idx,
                        total = knocks.size,
                        k = k,
                        onChange = { knocks[idx] = it },
                        onMinimize = { editing = -1 },
                        onDelete = {
                            knocks.removeAt(idx)
                            editing = -1
                        },
                        onMove = { d ->
                            val j = idx + d
                            if (j in knocks.indices) {
                                val t = knocks[idx]
                                knocks[idx] = knocks[j]
                                knocks[j] = t
                                editing = j
                            }
                        }
                    )
                }
            }
        }

        // test settings
        Panel {
            SectionTitle("تنظیمات بررسی")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = verify,
                    onValueChange = { verify = it.filter(Char::isDigit).take(5) },
                    label = { Text("پورت تست") },
                    singleLine = true,
                    enabled = !running,
                    shape = RoundedCornerShape(14.dp),
                    textStyle = ltr,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = delayText,
                    onValueChange = { delayText = it.filter(Char::isDigit).take(4) },
                    label = { Text("تأخیر (ms)") },
                    singleLine = true,
                    enabled = !running,
                    shape = RoundedCornerShape(14.dp),
                    textStyle = ltr,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Text("اگر پورت تست خالی باشد، نتیجه با پینگ بررسی می‌شود.", fontSize = 12.sp, color = Muted)
        }

        // door scene + result
        Panel {
            DoorScene(
                phase = phase,
                step = step,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
            Text(
                status,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (message.isNotEmpty()) {
                Text(
                    message,
                    color = if (phase == Phase.Success) Good else Bad,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            onClick = {
                phase = Phase.Running
                message = ""
                step = -1
                editing = -1
                val d = (delayText.toLongOrNull() ?: 300L).coerceIn(0L, 5000L)
                scope.launch {
                    val r = KnockEngine.run(host, knocks.toList(), d, verify) { step = it }
                    step = -1
                    phase = if (r.ok) Phase.Success else Phase.Fail
                    message = r.message
                }
            },
            enabled = !running,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                if (running) "در حال ناک…" else "ناک کن و نتیجه را بررسی کن",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun KnockCircle(
    index: Int,
    k: Knock,
    selected: Boolean,
    state: CircleState,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val fill by animateColorAsState(
        when (state) {
            CircleState.Active -> Amber.copy(alpha = 0.28f)
            CircleState.Done -> Good.copy(alpha = 0.28f)
            CircleState.Normal -> CircleBg
        },
        label = "fill"
    )
    val borderColor by animateColorAsState(
        when {
            selected -> Accent
            state == CircleState.Active -> Amber
            state == CircleState.Done -> Good
            else -> Color(0xFF3A4654)
        },
        label = "border"
    )
    val scale by animateFloatAsState(
        if (state == CircleState.Active) 1.12f else 1f,
        spring(),
        label = "scale"
    )
    Box(
        modifier = Modifier
            .size(68.dp)
            .scale(scale)
            .background(fill, CircleShape)
            .border(if (selected) 3.dp else 1.5.dp, borderColor, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${index + 1}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                k.label(),
                fontSize = 10.sp,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun AddCircle(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .border(1.5.dp, Accent, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("+", fontSize = 32.sp, color = Accent)
    }
}

@Composable
private fun KnockEditor(
    index: Int,
    total: Int,
    k: Knock,
    onChange: (Knock) -> Unit,
    onMinimize: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit
) {
    val ltr = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = InnerBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ناک ${index + 1}", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onMinimize) { Text("—", fontSize = 22.sp, color = Muted) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Proto.values().forEach { p ->
                    ProtoButton(p.name, p == k.proto) { onChange(k.copy(proto = p)) }
                }
            }
            if (k.proto != Proto.ICMP) {
                OutlinedTextField(
                    value = k.port,
                    onValueChange = { onChange(k.copy(port = it.filter(Char::isDigit).take(5))) },
                    label = { Text("پورت") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    textStyle = ltr,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    TextButton(onClick = { onMove(-1) }, enabled = index > 0) { Text("زودتر") }
                    TextButton(onClick = { onMove(1) }, enabled = index < total - 1) { Text("دیرتر") }
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = Bad)
                ) { Text("حذف") }
            }
        }
    }
}

@Composable
private fun RowScope.ProtoButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.weight(1f)) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) { Text(label) }
    }
}
