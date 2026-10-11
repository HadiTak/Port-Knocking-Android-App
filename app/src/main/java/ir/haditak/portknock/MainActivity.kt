package ir.haditak.portknock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        App()
                    }
                }
            }
        }
    }
}

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
    val scope = rememberCoroutineScope()
    val ltr = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
    val running = phase == Phase.Running

    LaunchedEffect(host, verify, delayText, knocks.toList()) {
        Store.save(ctx, host, verify, delayText, knocks.toList())
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = host,
            onValueChange = { host = it.trim() },
            label = { Text("آدرس مقصد (IP یا دامنه)") },
            singleLine = true,
            enabled = !running,
            textStyle = ltr,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )

        knocks.forEachIndexed { i, k ->
            KnockCard(
                index = i,
                k = k,
                active = running && step == i,
                enabled = !running,
                onChange = { knocks[i] = it },
                onDelete = { knocks.removeAt(i) }
            )
        }

        FilledTonalButton(
            onClick = { knocks.add(Knock()) },
            enabled = !running,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+  افزودن ناک", fontSize = 16.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = verify,
                onValueChange = { verify = it.filter(Char::isDigit).take(5) },
                label = { Text("پورت تست") },
                singleLine = true,
                enabled = !running,
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
                textStyle = ltr,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            "اگر پورت تست خالی باشد، نتیجه با پینگ بررسی می‌شود.",
            fontSize = 12.sp,
            color = Color.Gray
        )

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
                color = if (phase == Phase.Success) Color(0xFF66BB6A) else Color(0xFFEF5350),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = {
                phase = Phase.Running
                message = ""
                step = -1
                val d = (delayText.toLongOrNull() ?: 300L).coerceIn(0L, 5000L)
                scope.launch {
                    val r = KnockEngine.run(host, knocks.toList(), d, verify) { step = it }
                    step = -1
                    phase = if (r.ok) Phase.Success else Phase.Fail
                    message = r.message
                }
            },
            enabled = !running,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("ناک کن و نتیجه را بررسی کن", fontSize = 16.sp)
        }
    }
}

@Composable
fun KnockCard(
    index: Int,
    k: Knock,
    active: Boolean,
    enabled: Boolean,
    onChange: (Knock) -> Unit,
    onDelete: () -> Unit
) {
    val ltr = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
    Card(
        border = BorderStroke(
            if (active) 2.dp else 0.dp,
            if (active) Color(0xFFFFC107) else Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ناک ${index + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDelete, enabled = enabled) { Text("حذف") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Proto.values().forEach { p ->
                    ProtoButton(p.name, p == k.proto, enabled) { onChange(k.copy(proto = p)) }
                }
            }
            if (k.proto != Proto.ICMP) {
                OutlinedTextField(
                    value = k.port,
                    onValueChange = { onChange(k.copy(port = it.filter(Char::isDigit).take(5))) },
                    label = { Text("پورت") },
                    singleLine = true,
                    enabled = enabled,
                    textStyle = ltr,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RowScope.ProtoButton(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f)) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f)) { Text(label) }
    }
}
