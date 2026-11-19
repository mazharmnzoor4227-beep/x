package com.mazharansari.x.ui.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazharansari.x.data.BotDefinition
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.chat.AxionSheet
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type

/**
 * Create-a-bot sheet (redesign round 2, spec §1.4/§3.3): the old
 * `CreateBotCard` from BotsScreen, moved into a bottom sheet launched from
 * Home's "New bot" button. Name / description / persona / preferred
 * provider only — there is NO logo picker any more (bot avatars are
 * monogram tiles, spec §2.5). Creating a bot opens THAT bot's own chat
 * ([onCreated] → `openBotChat` + `Route.BotChat`); it never lands on a
 * blank home chat.
 */
@Composable
fun CreateBotSheet(vm: ChatViewModel, onCreated: (BotDefinition) -> Unit, onDismiss: () -> Unit) {
    val p = P
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var persona by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf<String?>(null) }
    val providers = listOf(null to "Auto", "Google Gemini" to "Gemini", "Groq" to "Groq", "OpenRouter" to "OpenRouter", "xAI Grok" to "Grok")
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = p.text2, unfocusedBorderColor = p.border,
        focusedTextColor = p.text, unfocusedTextColor = p.text,
        cursorColor = p.text, focusedLabelColor = p.text2, unfocusedLabelColor = p.text3)
    AxionSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("New bot", style = Type.title, color = p.text)
            Text("Name it anything. A bot that spawns workers is the parent for its run — the name is what makes it the boss, not a built-in role. Tell it its role in chat (\"You are the CEO — manage everything\") and it will spawn a worker bot per task.",
                style = Type.small, color = p.text2, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(description, { description = it }, label = { Text("What it does") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(persona, { persona = it }, label = { Text("How it should work (persona)") }, minLines = 3, colors = colors, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text("Preferred provider (free)", fontSize = 12.sp, color = p.text3)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                providers.forEach { (id, label) ->
                    val selected = provider == id
                    Box(Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (selected) p.accentSoft else p.chip)
                        .border(1.dp, if (selected) p.accent else p.border, RoundedCornerShape(6.dp))
                        .clickable { provider = id }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(label, fontSize = 12.sp, color = if (selected) p.accent else p.text2, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = p.text2) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val created = vm.g.botStore.create(name, description, persona, provider)
                        onDismiss()
                        onCreated(created)
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Create bot") }
            }
        }
    }
}
