package com.mazharansari.x.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazharansari.x.data.MyServer
import com.mazharansari.x.local.*
import com.mazharansari.x.ui.ChatViewModel
import com.mazharansari.x.ui.Route
import com.mazharansari.x.ui.components.*
import com.mazharansari.x.ui.theme.P
import com.mazharansari.x.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val OAmber = Color(0xFFF5A524)
private val ORed = Color(0xFFF04438)

@Composable
private fun HonestBanner(text: String) {
    val p = P
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (p.isDark) Color(0xFF2A2210) else Color(0xFFFFF4DC)).padding(14.dp)) {
        GkIcon(Gk.info, null, 18.dp, OAmber); Spacer(Modifier.width(10.dp))
        Text(text, style = Type.small.copy(fontSize = 13.sp), color = p.text)
    }
}

/** Addendum 6: on-device models. Nothing is bundled; each model downloads only on tap, with its size and a RAM check. */
@Composable
fun LocalModelsScreen(vm: ChatViewModel, back: () -> Unit) {
    val p = P; val g = vm.g; val store = g.localModels
    val states by store.state.collectAsState()
    val conns by g.connectors.list.collectAsState()
    val active = conns.firstOrNull { it.providerId == "local" }?.localModel
    val settings by g.settings.state.collectAsState()
    val ram by produceState(0.0 to 0.0) { value = withContext(Dispatchers.IO) { runCatching { store.ram() }.getOrDefault(0.0 to 0.0) } }
    val free by produceState(0L) { value = withContext(Dispatchers.IO) { store.freeBytes() } }
    val totalStorage by produceState(0L) { value = withContext(Dispatchers.IO) { totalStorageBytes(store.dir) } }
    val phoneTier = if (ram.first > 0 && totalStorage > 0) ModelTier.tierFor(ram.first, totalStorage / 1073741824.0) else null
    var confirm by remember { mutableStateOf<LocalModel?>(null) }
    Column(Modifier.fillMaxSize().background(p.page2)) {
        ScreenHeader("Local model", back)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            HonestBanner("Runs on your phone with no internet. Honest: these small models are much slower and weaker than API models (Gemini, Groq…). Good for quick offline answers and small code snippets, not big projects. The phone gets warm. In Local mode they also run basic file and shell tools in chat; web search, skills and multi-step agent runs still need an API model.")
            Note("Languages: Hindi works well on LFM3; Urdu is limited on all of these models — none covers Urdu properly yet.", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                @Composable fun box(label: String, value: String, m: Modifier) = Column(m.clip(RoundedCornerShape(10.dp)).background(p.card).padding(12.dp)) {
                    Text(value, style = Type.row.copy(fontSize = 16.sp), color = p.text); Text(label, style = Type.tiny, color = p.text2)
                }
                box("Phone RAM", if (ram.first > 0) "%.1f GB".format(ram.first) else "—", Modifier.weight(1f))
                box("RAM free now", if (ram.second > 0) "%.1f GB".format(ram.second) else "—", Modifier.weight(1f))
                box("Storage free", mb(free / 1024), Modifier.weight(1f))
            }
            // Tiered catalog: the phone's own tier first ("Your phone — … — use
            // these"), then the other tiers in size order. A model that needs
            // more RAM than this phone has is shown with its honest reason and
            // its download blocked — never a silent failure.
            val tiers = phoneTier?.let { ModelTier.orderedForPhone(it) } ?: ModelTier.ALL
            tiers.forEach { tier ->
                SectionLabel(tier.headline)
                if (tier == phoneTier) Note("Your phone — ${tier.label} — use these.", Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
                Note(tier.note, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                tier.models.forEach { m ->
                    LocalModelCard(m, states[m.id] ?: DlState.None, active, ram, free, store, vm) { confirm = it }
                }
            }
            if (active != null) {
                SectionLabel("Use it")
                Note(if (active == LocalModel.LFM3_1_7B.id) "LFM3-1.7B is the default: Ask answers with it first, offline — no pick needed. Your API keys are the backup, then Free (slow)." else "Ask's model menu has \"Local model\" for this model — pick it to answer offline first; otherwise it's the backup after your API keys and before Free (slow). LFM3-1.7B is the default when downloaded: it answers first, before this one.", Modifier.padding(horizontal = 20.dp))
                ToggleRow(Gk.bolt, "Answer with the local model first", "Same as picking it in the model menu", settings.mode == "LOCAL") { on -> g.settings.update { it.copy(mode = if (on) "LOCAL" else "AUTO") } }
            }
            // LFM3 default (2026-11-20): the default model for Ask, not just the
            // first local row. Honest state: when it is downloaded it answers
            // first (offline); until then Ask uses the API keys — never allam
            // first — and the download is offered right here.
            SectionLabel("Default model")
            val lfm3State = states[LocalModel.LFM3_1_7B.id] ?: DlState.None
            val lfm3Reason = if (ram.first > 0) blockReason(LocalModel.LFM3_1_7B, ram.first, ram.second, free) else null
            when (lfm3State) {
                DlState.Ready -> Note("LFM3-1.7B is the default. Ask answers with it first — offline, on this phone — and your API keys are the backup if it can't answer.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                is DlState.Downloading -> Note("LFM3-1.7B (the default) is downloading — ${mb(lfm3State.done / 1024)} of ${mb(lfm3State.total / 1024)}. Ask uses your API keys until it's ready.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                DlState.Verifying -> Note("LFM3-1.7B (the default) is being checked… Ask uses your API keys until it's ready.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                is DlState.Failed -> {
                    Note("LFM3-1.7B is the default, but its download failed: ${lfm3State.message} Ask uses your API keys until it's downloaded.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { SmallButton("Try the download again", filled = true) { if (lfm3Reason != null) confirm = LocalModel.LFM3_1_7B else store.download(LocalModel.LFM3_1_7B) } }
                }
                DlState.None -> {
                    Note("LFM3-1.7B is the default model, but it isn't downloaded yet. Download it to answer offline first — until then Ask uses your API keys.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { SmallButton("Download LFM3-1.7B · ${mb(LocalModel.LFM3_1_7B.bytes / 1024)}", filled = true) { if (lfm3Reason != null) confirm = LocalModel.LFM3_1_7B else store.download(LocalModel.LFM3_1_7B) } }
                }
            }
            Note("Models come from Hugging Face (Liquid AI, Qwen and Meta, GGUF Q4_K_M), grouped by phone RAM and storage so you only see models that fit your phone, and are checked with their SHA-256 after download.", Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
            Spacer(Modifier.height(30.dp))
        }
    }
    confirm?.let { m ->
        ConfirmDialog("Download anyway?", blockReason(m, ram.first, ram.second, free).orEmpty(), "Download", onDismiss = { confirm = null }) { store.download(m); confirm = null }
    }
}

/** One model card inside a tier section. Download still goes through the existing [LocalModelStore.download] path (shared resumable/parallel downloader); a model the phone's total RAM cannot run shows its honest reason and a disabled button instead of a download. */
@Composable
private fun LocalModelCard(m: LocalModel, st: DlState, active: String?, ram: Pair<Double, Double>, free: Long, store: LocalModelStore, vm: ChatViewModel, onConfirm: (LocalModel) -> Unit) {
    val p = P; val g = vm.g
    val reason = if (ram.first > 0) blockReason(m, ram.first, ram.second, free) else null
    val hardBlocked = if (ram.first > 0) isHardRamBlock(m, ram.first) else false
    Column(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.card)
        .then(if (active == m.id) Modifier.border(1.5.dp, p.text, RoundedCornerShape(10.dp)) else Modifier).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val initials = when (m.family) { "Qwen" -> "Qw"; "Meta" -> "Ll"; else -> "Lf" }
            val chipColor = when (m.family) { "Qwen" -> Color(0xFF3B2A6B); "Meta" -> Color(0xFF2E4A62); else -> Color(0xFF6B4A1F) }
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(chipColor), contentAlignment = Alignment.Center) {
                Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, style = Type.row, color = p.text)
                Text("${m.params} · ${mb(m.bytes / 1024)} · needs ${m.minRamGb} GB+ RAM", style = Type.tiny, color = p.text2)
            }
            when {
                active == m.id -> Badge("In use", p.accent)
                st == DlState.Ready -> Badge("Downloaded", p.accent)
            }
            // LFM3 default (2026-11-20): the card says it, matching the enum
            // blurb ("Chat · Agent · Hindi — default") and the Default model
            // section above — never just "Downloaded" with no default mark.
            if (m.id == LocalModel.LFM3_1_7B.id) { Spacer(Modifier.width(6.dp)); Badge("Default", p.accent) }
        }
        Text(m.purpose.uppercase(), style = Type.eyebrow, color = p.accent, modifier = Modifier.padding(top = 8.dp))
        Text(m.blurb, style = Type.small.copy(fontSize = 13.sp), color = p.text2, modifier = Modifier.padding(top = 4.dp))
        if (reason != null && st != DlState.Ready) Text(reason, style = Type.small.copy(fontSize = 13.sp), color = if (hardBlocked) ORed else OAmber, modifier = Modifier.padding(top = 6.dp))
        when (st) {
            is DlState.Downloading -> {
                QuotaBar(st.done.toDouble() / st.total.coerceAtLeast(1), Modifier.padding(top = 10.dp), 5.dp)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${mb(st.done / 1024)} of ${mb(st.total / 1024)}", style = Type.tiny, color = p.text2, modifier = Modifier.weight(1f))
                    SmallButton("Cancel") { store.cancel(m) }
                }
            }
            DlState.Verifying -> Text("Checking the download…", style = Type.tiny, color = p.text2, modifier = Modifier.padding(top = 8.dp))
            is DlState.Failed -> Text(st.message, style = Type.small, color = ORed, modifier = Modifier.padding(top = 8.dp))
            else -> {}
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (st) {
                DlState.Ready -> {
                    if (active != m.id) SmallButton("Use in Ask", filled = true) { g.connectors.setLocal(m.id, m.title) }
                    else SmallButton("Stop using") { g.connectors.setLocal(null) }
                    SmallButton("Delete") { if (active == m.id) g.connectors.setLocal(null); store.delete(m) }
                }
                is DlState.Downloading, DlState.Verifying -> {}
                else -> if (m.url.isBlank()) Text("Download link not confirmed yet — the exact file is being checked.", style = Type.tiny, color = p.text2)
                else if (hardBlocked) SmallButton("Needs ${m.minRamGb} GB RAM", enabled = false) {}
                else SmallButton("Download ${mb(m.bytes / 1024)}", filled = true) { if (reason != null) onConfirm(m) else store.download(m) }
            }
        }
    }
}

/** Addendum 6: "My server" — an OpenAI-compatible URL (Ollama on an Oracle Always Free ARM box, llama.cpp server…). */
@Composable
fun MyServerScreen(vm: ChatViewModel, back: () -> Unit, nav: (Route) -> Unit) {
    val p = P; val g = vm.g; val scope = rememberCoroutineScope()
    val existing = remember { g.connectors.server() }
    var url by remember { mutableStateOf(existing?.baseUrl.orEmpty()) }
    var key by remember { mutableStateOf(existing?.key().orEmpty()) }
    var models by remember { mutableStateOf(existing?.models.orEmpty()) }
    var model by remember { mutableStateOf(existing?.pinnedModel ?: existing?.models?.firstOrNull()) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<MyServer.Test?>(null) }
    val settings by g.settings.state.collectAsState()
    Column(Modifier.fillMaxSize().background(p.page2)) {
        ScreenHeader("My server", back)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()) {
            HonestBanner("Your own model server, running 24 hours even when your PC is off. Honest: without a GPU a 7B model takes about 20–60 seconds per answer and is weaker than API models.")
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Server address", style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = p.text2, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                Field(url, { url = it; result = null }, "https://1-2-3-4.sslip.io", mono = true)
                Spacer(Modifier.height(12.dp))
                Text("API key (optional)", style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = p.text2, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                Field(key, { key = it; result = null }, "Only if your server asks for one", password = true, mono = true)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SmallButton(if (testing) "Testing…" else "Test connection", filled = true, enabled = url.isNotBlank() && !testing) {
                        testing = true; result = null
                        scope.launch { val r = withContext(Dispatchers.IO) { MyServer.test(url, key, model) }; result = r; if (r.models.isNotEmpty()) { models = r.models; if (model !in r.models) model = r.models.first() }; testing = false }
                    }
                    if (testing) CircularProgressIndicator(Modifier.size(18.dp), color = p.text2, strokeWidth = 2.dp)
                }
                result?.let { r -> Text(r.message, style = Type.small, color = if (r.ok) p.accent else ORed, modifier = Modifier.padding(top = 10.dp)) }
            }
            if (models.isNotEmpty()) {
                SectionLabel("Model")
                SettingsGroup {
                    models.take(20).forEachIndexed { i, m ->
                        SettingsRow(Gk.brain, m, chevron = false, trailing = { if (model == m) GkIcon(Gk.check, null, 20.dp, p.text) }, onClick = { model = m })
                    }
                }
            }
            Column(Modifier.padding(16.dp)) {
                BigButton(if (existing != null) "Save" else "Add to Ask", enabled = url.isNotBlank() && (result?.ok == true || existing != null)) {
                    g.connectors.setServer(url, key, models, model); vm.toast = "My server saved"; back()
                }
                if (existing != null) Text("Remove server", style = Type.small, color = ORed, modifier = Modifier.padding(top = 14.dp).clickable { g.connectors.setServer("", "", emptyList(), null); g.connectors.remove(g.connectors.server()?.id ?: ""); back() })
            }
            if (existing != null) ToggleRow(Gk.bolt, "Answer with my server first", "Same as picking it in the model menu", settings.mode == "SERVER") { on -> g.settings.update { it.copy(mode = if (on) "SERVER" else "AUTO") } }
            SectionLabel("Don't have a server yet?")
            SettingsGroup { SettingsRow(Gk.help, "Set up a free server (Oracle + Ollama)", "Step by step · about 30 minutes", onClick = { nav(Route.ServerGuide) }) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** In-app guide: Oracle Cloud Always Free ARM (4 OCPU / 24 GB) + Ollama + qwen2.5-coder, systemd, firewall, HTTPS with Caddy + sslip.io. */
@Composable
fun ServerGuideScreen(back: () -> Unit) {
    val p = P
    Column(Modifier.fillMaxSize().background(p.page2)) {
        ScreenHeader("Free model server", back)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            HonestBanner("Your current Oracle server (1 GB, x86) is too small for a model. Oracle's Always Free ARM server (4 CPUs, 24 GB RAM) runs qwen2.5-coder 7B well and 14B slowly. CPU only: expect 20–60 seconds per answer.")
            GuideStep(1, "Create the ARM server", "Oracle Cloud → Compute → Instances → Create. Image: Ubuntu 24.04. Shape: Ampere VM.Standard.A1.Flex, 4 OCPUs, 24 GB memory (all Always Free). Add your SSH key, create. If it says \"out of capacity\", try another availability domain or try again later.")
            GuideStep(2, "Open port 443 in Oracle's firewall", "Instance → Subnet → Default security list → Add ingress rule: source 0.0.0.0/0, TCP, destination port 443 (and 80 for the certificate).")
            GuideStep(3, "Connect and open the server's own firewall", null,
                "ssh ubuntu@YOUR_IP\nsudo iptables -I INPUT 6 -p tcp --dport 80 -j ACCEPT\nsudo iptables -I INPUT 6 -p tcp --dport 443 -j ACCEPT\nsudo netfilter-persistent save")
            GuideStep(4, "Install Ollama and a coding model", "7B ≈ 4.7 GB (recommended). 14B ≈ 9 GB (better, about half the speed).",
                "curl -fsSL https://ollama.com/install.sh | sh\nollama pull qwen2.5-coder:7b\n# optional, stronger and slower:\nollama pull qwen2.5-coder:14b")
            GuideStep(5, "Keep it running 24/7", "Ollama installs itself as a systemd service, so it restarts on reboot. Keep it listening only on the server itself:",
                "sudo systemctl enable --now ollama\nsystemctl status ollama --no-pager")
            GuideStep(6, "HTTPS with a password (Caddy + sslip.io)", "sslip.io gives your IP a free name, Caddy gets a free certificate. Replace 1-2-3-4 with your IP (dashes) and pick your own key:",
                "sudo apt install -y caddy\nsudo tee /etc/caddy/Caddyfile <<'EOF'\n1-2-3-4.sslip.io {\n  @nokey not header Authorization \"Bearer MY-SECRET-KEY\"\n  respond @nokey 401\n  reverse_proxy 127.0.0.1:11434\n}\nEOF\nsudo systemctl restart caddy")
            GuideStep(7, "Connect X", "Connectors → My server. Address: https://1-2-3-4.sslip.io · API key: MY-SECRET-KEY · tap Test connection, pick qwen2.5-coder:7b, then Add to Ask.")
            Note("Oracle may reclaim Always Free servers that sit idle for a long time; using it regularly keeps it active.", Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun GuideStep(n: Int, title: String, body: String?, code: String? = null) {
    val p = P; val clip = LocalClipboardManager.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(p.chip), contentAlignment = Alignment.Center) { Text("$n", style = Type.small.copy(fontWeight = FontWeight.Bold), color = p.text) }
            Spacer(Modifier.width(10.dp))
            Text(title, style = Type.row, color = p.text)
        }
        body?.let { Text(it, style = Type.small.copy(fontSize = 13.5.sp, lineHeight = 19.sp), color = p.text2, modifier = Modifier.padding(top = 8.dp)) }
        code?.let { c ->
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.code)) {
                // Code boxes stay dark in both themes (Dots code surface), so the text is light.
                Text(c, style = Type.mono.copy(fontSize = 12.sp, lineHeight = 18.sp), color = Color(0xFFF2F1EE), softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 44.dp, top = 10.dp, bottom = 10.dp))
                Box(Modifier.align(Alignment.TopEnd)) { IconTap(Gk.copy, "Copy", 16.dp, Color(0xFFA9AAB2)) { clip.setText(AnnotatedString(c.lines().filterNot { it.startsWith("#") }.joinToString("\n"))) } }
            }
        }
    }
}
