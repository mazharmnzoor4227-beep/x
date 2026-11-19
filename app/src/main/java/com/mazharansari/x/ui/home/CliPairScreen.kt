package com.mazharansari.x.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazharansari.x.antigravity.AgyLoginState
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.Route
import com.mazharansari.x.ui.components.Gk
import com.mazharansari.x.ui.components.GkIcon
import com.mazharansari.x.ui.components.ScreenHeader
import com.mazharansari.x.ui.components.SectionLabel
import com.mazharansari.x.ui.components.rememberTap
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * CLI pair (new series): the Antigravity (`agy`) pair screen, as a full screen
 * over the existing login flow — NOT a new flow. The state machine is
 * [AgyLoginState] from antigravity/AgyLogin.kt: begin() starts the CLI in a
 * real terminal, AwaitingCode carries the Google sign-in URL, and the code
 * the user pastes goes straight into [AgyLogin.submitCode]. X never sees
 * the password or the token. The phone does not run the CLI natively — it
 * runs inside Linux on the phone, so Linux + agy must be installed first.
 */
@Composable
fun CliPairScreen(vm: ChatViewModel, back: () -> Unit, navigate: (Route) -> Unit = {}) {
    val p = P
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val g = vm.g
    val st by g.agyLogin.state.collectAsState()
    val scope = rememberCoroutineScope()
    // Snapshot of the paired accounts; re-read whenever the login state moves.
    val accounts = remember(st) { g.agy.accounts() }
    val agyInstalled = g.devtools.isInstalled("agy")
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        ScreenHeader("CLI Pair", back)
        LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text("Pair Antigravity — the phone does not run the CLI. It runs inside Linux on this phone; you sign in with Google and paste the one-time code Google shows.",
                    style = Type.small.copy(fontSize = 14.sp, lineHeight = 20.sp), color = p.text2,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
            item { SectionLabel("Paired accounts") }
            if (accounts.isEmpty()) {
                item {
                    Text("Not paired. No Antigravity account is signed in on this phone yet.",
                        style = Type.small, color = p.text2, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
            }
            items(accounts, key = { it.email }) { a ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(p.card).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(p.surface), contentAlignment = Alignment.Center) {
                        GkIcon(Gk.agent, null, 20.dp, p.text)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.email, style = Type.row.copy(fontSize = 16.sp), color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (a.enabled) "Signed in" else "Paused", style = Type.small, color = p.text2)
                    }
                    if (g.agy.hasToken(a)) GkIcon(Gk.check, "Has token", 20.dp, p.accent)
                }
            }
            item { SectionLabel("Pair a new account") }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(10.dp)).background(p.card).padding(16.dp)) {
                    when (st) {
                        is AgyLoginState.Idle -> {
                            if (!agyInstalled) {
                                Text("Antigravity is not installed yet. Install Linux and the agy CLI in Developer tools first.",
                                    style = Type.small, color = p.text2)
                                Spacer(Modifier.height(12.dp))
                                PairButton("Open Developer tools") { navigate(Route.DevTools) }
                            } else {
                                PairButton("Start sign-in") { scope.launch { g.agyLogin.begin() } }
                            }
                        }
                        is AgyLoginState.Starting -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), color = p.text, strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text((st as AgyLoginState.Starting).message, style = Type.small, color = p.text)
                            }
                        }
                        is AgyLoginState.AwaitingCode -> {
                            val url = (st as AgyLoginState.AwaitingCode).url
                            Text("1. Open Google sign-in", style = Type.row.copy(fontSize = 16.sp), color = p.text)
                            Spacer(Modifier.height(8.dp))
                            Text(url.take(64) + "…", style = Type.mono.copy(fontSize = 11.5.sp), color = p.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PairButton("Open in browser", filled = false) {
                                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                }
                                PairButton("Copy link", filled = false) { clip.setText(AnnotatedString(url)); vm.toast = "Link copied" }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("2. Paste the code Google shows", style = Type.row.copy(fontSize = 16.sp), color = p.text)
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                                BasicTextField(code, { code = it; err = null }, singleLine = true, textStyle = Type.mono.copy(color = p.text), cursorBrush = SolidColor(p.text),
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { inner -> if (code.isEmpty()) Text("4/0Ab…", style = Type.mono, color = p.text3); inner() })
                            }
                            err?.let { Text(it, style = Type.small, color = p.danger, modifier = Modifier.padding(top = 6.dp)) }
                            Spacer(Modifier.height(10.dp))
                            val submit = rememberTap { runCatching { g.agyLogin.submitCode(code) }.onFailure { err = it.message } }
                            Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp)).background(if (code.isBlank()) p.surface2 else p.text).clickable(enabled = code.isNotBlank(), onClick = submit), contentAlignment = Alignment.Center) {
                                Text("Continue", style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = if (code.isBlank()) p.text3 else p.onAccent)
                            }
                            Spacer(Modifier.height(10.dp))
                            PairButton("Cancel sign-in", filled = false) { g.agyLogin.cancel() }
                        }
                        is AgyLoginState.Completing -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), color = p.text, strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text((st as AgyLoginState.Completing).message, style = Type.small, color = p.text)
                            }
                        }
                        is AgyLoginState.SignedIn -> {
                            val email = (st as AgyLoginState.SignedIn).email
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GkIcon(Gk.circleCheck, null, 22.dp, p.accent)
                                Spacer(Modifier.width(10.dp))
                                Text("Connected" + (email?.let { " as $it" } ?: ""), style = Type.row.copy(fontSize = 16.sp), color = p.text)
                            }
                            Spacer(Modifier.height(12.dp))
                            PairButton("Done") { back() }
                        }
                        is AgyLoginState.Error -> {
                            Text((st as AgyLoginState.Error).message, style = Type.small, color = p.danger)
                            Spacer(Modifier.height(12.dp))
                            PairButton("Try again") { scope.launch { g.agyLogin.begin() } }
                        }
                    }
                }
            }
            item {
                Text("Sign-in times out after 15 minutes — start again if it does. A failed code exchange means: paste a fresh code.",
                    style = Type.small.copy(fontSize = 13.sp, lineHeight = 18.sp), color = p.text3,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
            }
        }
    }
}

@Composable
private fun PairButton(label: String, filled: Boolean = true, onClick: () -> Unit) {
    val p = P
    val tap = rememberTap(onClick)
    Box(Modifier.height(44.dp).clip(RoundedCornerShape(8.dp))
        .background(if (filled) p.text else p.surface)
        .border(1.dp, p.border, RoundedCornerShape(8.dp))
        .clickable(onClick = tap).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Text(label, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = if (filled) p.onAccent else p.text)
    }
}
