package com.mazharansari.x.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazharansari.x.ui.components.Gk
import com.mazharansari.x.ui.components.GkIcon
import com.mazharansari.x.ui.components.rememberTap
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Shared bits for the new-series dock tabs (Feed / Ideas / Goals / Library) and the
 * unified screens (Activity / Computer / Mark / Code / CLI pair). Every screen here
 * is full-bleed with its own ScreenHeader, so the list content always leaves room
 * for the bottom dock pill (92 dp).
 */
internal val DOCK_CLEARANCE = 100.dp

/** "2:14 PM" today, "Yesterday", else "3 Nov" — the same shape as the chat list. */
internal fun timeLabel(t: Long): String {
    if (t <= 0) return ""
    val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val dayStart = cal.timeInMillis
    return when {
        t >= dayStart -> SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(t))
        t >= dayStart - 86_400_000L -> "Yesterday"
        else -> SimpleDateFormat("d MMM", Locale.ENGLISH).format(Date(t))
    }
}

internal fun dateTimeLabel(t: Long): String =
    if (t <= 0) "" else SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH).format(Date(t))

/** Real empty state: what is missing, and where it comes from — never demo rows. */
@Composable
internal fun DockEmpty(title: String, hint: String) {
    val p = P
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GkIcon(Gk.sparkles, null, 28.dp, p.text3)
        Spacer(Modifier.height(12.dp))
        Text(title, style = Type.row.copy(fontSize = 17.sp), color = p.text)
        Spacer(Modifier.height(6.dp))
        Text(hint, style = Type.small, color = p.text2)
    }
}

@Composable
internal fun SectionHead(text: String) {
    Text(text, style = Type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = P.text2,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp))
}

/** One-line text field + Add button, used by the Ideas / Goals add rows. */
@Composable
internal fun AddTextRow(value: String, onValue: (String) -> Unit, hint: String, onAdd: () -> Unit, addLabel: String = "Add") {
    val p = P
    val tap = rememberTap(onAdd)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(50)).background(p.card).border(1.dp, p.border, RoundedCornerShape(50)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(value, onValue, singleLine = true, textStyle = Type.body.copy(color = p.text), cursorBrush = SolidColor(p.text),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(hint, style = Type.body, color = p.text3)
                    inner()
                })
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.height(48.dp).clip(RoundedCornerShape(50)).background(if (value.isBlank()) p.surface2 else p.text).clickable(enabled = value.isNotBlank(), onClick = tap).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text(addLabel, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = if (value.isBlank()) p.text3 else p.onAccent)
        }
    }
}

/** Small square check box: filled when done, outline when not. */
@Composable
internal fun CheckBox(done: Boolean, onToggle: () -> Unit) {
    val p = P
    val tap = rememberTap(onToggle)
    Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(if (done) p.text else p.surface2).clickable(onClick = tap), contentAlignment = Alignment.Center) {
        if (done) GkIcon(Gk.check, null, 15.dp, p.onAccent)
    }
}
