package com.mazharansari.x.ui.bots

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.mazharansari.x.data.BotDefinitionStore
import com.mazharansari.x.data.BotIconKind
import com.mazharansari.x.data.BotLogo
import com.mazharansari.x.ui.chat.AxionSheet
import com.mazharansari.x.ui.components.Gk
import com.mazharansari.x.ui.components.GkIcon
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type

/**
 * Profile editing (navigation restructure, 2026-11-20): tapping ANY bot's
 * profile — X Bot itself, a user-created bot, or any sub-bot X Bot spawned —
 * opens [BotProfileSheet]. The user can change the bot's NAME and its ICON.
 * Icon options:
 * - Monogram (the professional default: two initials on a muted tile),
 * - Static image (a photo the user picks — copied into the app's own
 *   storage, so it survives the gallery permission being revoked),
 * - 3D (a 3D-style drawn tile, five styles — no .glb renderer is wired
 *   in-tree, so the drawn tile is the 3D look the app can render),
 * - Video (a short looping clip the user picks — muted, loops, capped at
 *   8 MB so the tile stays lightweight; never auto-plays sound).
 * The sheet title is 17sp SemiBold (redesign round 2 §H partial: sheet
 * titles were 22sp).
 *
 * Creating a bot (2026-11-20): [CreateBotSheet] is the Bots section's
 * "Create bot" entry — a custom bot with a name, a short description,
 * instructions/persona and a profile picture, saved through
 * [BotDefinitionStore.create] (+ setLogo for the photo) and opened in its
 * own chat like X Bot. X Bot stays the default orchestrator.
 */
@Composable
fun BotProfileSheet(
    botKey: String,
    currentName: String,
    currentLogo: String,
    onSave: (name: String, logo: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = P
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(currentName) }
    var logo by remember { mutableStateOf(currentLogo) }
    var kind by remember { mutableStateOf(kindOf(currentLogo)) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val ext = if (ctx.contentResolver.getType(uri) == "image/png") "png" else "jpg"
            val path = copyPicked(ctx, botKey, uri, ext, MAX_IMAGE_BYTES)
            if (path != null) {
                logo = BotLogo.custom(path)
                kind = BotIconKind.IMAGE
            } else {
                Toast.makeText(ctx, "Could not use that image — pick a photo under 10 MB", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val path = copyPicked(ctx, botKey, uri, "mp4", MAX_VIDEO_BYTES)
            if (path != null) {
                logo = BotLogo.video(path)
                kind = BotIconKind.VIDEO
            } else {
                Toast.makeText(ctx, "Video too big — pick a clip under 8 MB", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = p.text2, unfocusedBorderColor = p.border,
        focusedTextColor = p.text, unfocusedTextColor = p.text,
        cursorColor = p.text, focusedLabelColor = p.text2, unfocusedLabelColor = p.text3)
    AxionSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Edit profile", style = Type.title.copy(fontSize = 17.sp), color = p.text)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true,
                colors = colors, modifier = Modifier.fillMaxWidth().testTag("profileName"))
            Spacer(Modifier.height(14.dp))
            Text("Icon", fontSize = 12.sp, color = p.text3)
            Spacer(Modifier.height(6.dp))
            IconOption(BotIconKind.MONOGRAM, "Monogram", "Two initials on a muted tile", logo, name, selected = kind == BotIconKind.MONOGRAM) {
                logo = ""; kind = BotIconKind.MONOGRAM
            }
            IconOption(BotIconKind.IMAGE, "Static image", "Pick a photo from your gallery", logo, name, selected = kind == BotIconKind.IMAGE) {
                pickImage.launch("image/*")
            }
            IconOption(BotIconKind.THREE_D, "3D", "A 3D-style tile — pick a style below", logo, name, selected = kind == BotIconKind.THREE_D) {
                kind = BotIconKind.THREE_D
                if (BotLogo.parse(logo) !is BotLogo.ThreeD) logo = BotLogo.threeD("bronze")
            }
            if (kind == BotIconKind.THREE_D) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotLogo.THREE_D.forEach { style ->
                        val selected = (BotLogo.parse(logo) as? BotLogo.ThreeD)?.id == style.id
                        Box(Modifier.clip(RoundedCornerShape(8.dp))
                            .border(if (selected) 2.dp else 1.dp, if (selected) p.accent else p.border, RoundedCornerShape(8.dp))
                            .clickable { logo = BotLogo.threeD(style.id) }
                            .padding(2.dp)) {
                            BotLogoView(BotLogo.threeD(style.id), name, 40.dp)
                        }
                    }
                }
                Text("3D Bronze, Slate, Plum, Navy, Stone", style = Type.small, color = p.text3, modifier = Modifier.padding(top = 6.dp))
            }
            IconOption(BotIconKind.VIDEO, "Video", "A short clip that loops silently (under 8 MB)", logo, name, selected = kind == BotIconKind.VIDEO) {
                pickVideo.launch("video/*")
            }
            Text("Video avatars loop with no sound. Pick a short clip — long videos are refused.",
                style = Type.small, color = p.text3, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = p.text2) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSave(name.trim().ifBlank { currentName }, logo) },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Save") }
            }
        }
    }
}

/**
 * Create a custom bot (2026-11-20): the Bots section's "Create bot" entry.
 * Name + short description + instructions/persona + an optional profile
 * picture (picked from the device, copied into the app's own storage like
 * the profile editor's). [onCreate] receives the four values — the caller
 * saves them with [BotDefinitionStore.create] (+ setLogo for the photo) and
 * opens the new bot's own chat. The sheet never creates anything itself:
 * Cancel / dismiss leaves no bot behind, and a blank name cannot create.
 */
@Composable
fun CreateBotSheet(
    onCreate: (name: String, description: String, persona: String, logo: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = P
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var persona by remember { mutableStateOf("") }
    var logo by remember { mutableStateOf("") }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val ext = if (ctx.contentResolver.getType(uri) == "image/png") "png" else "jpg"
            val path = copyPicked(ctx, "new-bot", uri, ext, MAX_IMAGE_BYTES)
            if (path != null) {
                logo = BotLogo.custom(path)
            } else {
                Toast.makeText(ctx, "Could not use that image — pick a photo under 10 MB", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = p.text2, unfocusedBorderColor = p.border,
        focusedTextColor = p.text, unfocusedTextColor = p.text,
        cursorColor = p.text, focusedLabelColor = p.text2, unfocusedLabelColor = p.text3)
    AxionSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("New bot", style = Type.title.copy(fontSize = 17.sp), color = p.text)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true,
                colors = colors, modifier = Modifier.fillMaxWidth().testTag("createBotName"))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(description, { description = it }, label = { Text("What does it do?") }, singleLine = true,
                colors = colors, modifier = Modifier.fillMaxWidth().testTag("createBotDescription"))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(persona, { persona = it }, label = { Text("Instructions — how it should behave") },
                minLines = 4, maxLines = 8, colors = colors,
                modifier = Modifier.fillMaxWidth().testTag("createBotInstructions"))
            Spacer(Modifier.height(14.dp))
            // Profile picture: the picked photo, or the monogram until one is picked.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BotLogoView(logo, name.ifBlank { "New bot" }, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Profile picture", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                    Text("Pick a photo from your gallery, or leave the monogram.", fontSize = 12.sp, color = p.text2)
                }
                TextButton(onClick = { pickImage.launch("image/*") }, modifier = Modifier.testTag("createBotPhoto")) {
                    Text(if (logo.isBlank()) "Pick" else "Change", color = p.accent)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = p.text2) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onCreate(name.trim(), description.trim(), persona.trim(), logo) },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("createBotConfirm"),
                ) { Text("Create bot") }
            }
        }
    }
}

private fun kindOf(logo: String): BotIconKind = when (BotLogo.parse(logo)) {
    is BotLogo.Video -> BotIconKind.VIDEO
    is BotLogo.Custom -> BotIconKind.IMAGE
    is BotLogo.ThreeD -> BotIconKind.THREE_D
    else -> BotIconKind.MONOGRAM
}

/** One icon-kind row: a preview tile, label + description, and a check when selected. */
@Composable
private fun IconOption(
    kind: BotIconKind,
    title: String,
    subtitle: String,
    currentLogo: String,
    currentName: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val p = P
    // The preview shows what this kind would look like: the current logo when
    // this kind is selected, else the monogram default for the kind.
    val previewLogo = if (selected) currentLogo else ""
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
        .background(if (selected) p.accentSoft else p.chip)
        .border(1.dp, if (selected) p.accent else p.border, RoundedCornerShape(8.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        BotLogoView(previewLogo, currentName, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            Text(subtitle, fontSize = 12.sp, color = p.text2)
        }
        if (selected) GkIcon(Gk.check, "Selected", 18.dp, p.accent)
    }
    Spacer(Modifier.height(6.dp))
}

private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
private const val MAX_VIDEO_BYTES = 8L * 1024 * 1024

/**
 * Copy a picked image/video into the app's own storage (bot-logos/) so the
 * icon survives the gallery permission being revoked. Shared helper
 * ([copyPickedMedia]) streams with a byte cap and refuses a file that is
 * not a real image — over the cap, unreadable, or not an image, the copy
 * is deleted and null is returned (the caller toasts and keeps the old
 * picture). Same file for the create sheet and the profile editor.
 */
private fun copyPicked(ctx: Context, botKey: String, uri: Uri, ext: String, maxBytes: Long): String? {
    val isImage = ext.equals("jpg", ignoreCase = true) || ext.equals("png", ignoreCase = true)
    return copyPickedMedia(ctx, "bot-logos", botKey, uri, ext, maxBytes, validateImage = isImage)
}
