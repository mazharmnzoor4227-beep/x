package com.mazharansari.x.local

import android.os.StatFs
import java.io.File

/**
 * Tiered local-model catalog (2026-11-20): models offered category-wise by
 * phone RAM / storage, so every phone gets a model that actually runs on it.
 * LFM3 stays the 4 GB default and the chat default; the tiers only decide
 * what is OFFERED, never the default.
 *
 *  - 2 GB RAM: Qwen2.5-0.5B-Instruct only (0.46 GB file, ~1.1 GB RAM at
 *    runtime). Nothing bigger fits.
 *  - 4 GB RAM, 64 GB storage: LFM3 only — a 64 GB phone can hold about
 *    one ~1 GB model comfortably; the other 4 GB models live on the 4/128
 *    tier and are blocked here only by storage, not by RAM.
 *  - 4 GB RAM, 128 GB storage: all four 4 GB models (LFM3, Coder,
 *    Qwen2.5-1.5B-Instruct, Qwen3-1.7B base).
 *  - 6 GB RAM: the two 3B instruct models. A 3B file needs ~2.6 GB free
 *    RAM (file + ~0.6 GB context/runtime); a 4 GB phone rarely has that
 *    free, so 6 GB is the honest minimum.
 *  - 8 GB RAM: the two 7–8B instruct models (single-file GGUFs only —
 *    the official Qwen2.5-7B-Instruct Q4_K_M is split into two files and
 *    the downloader handles one file, so the single-file bartowski build
 *    is wired instead). A 7–8B file needs ~5 GB free RAM + ~5 GB storage.
 *
 * Every wired model was verified against live Hugging Face on 2026-11-20
 * (API file-list size + resolve HEAD X-Linked-Size / X-Linked-ETag, the
 * file's SHA-256 — see LocalModels.kt). No tier lists a model whose
 * [LocalModel.minRamGb] is above the tier's RAM: [fitsTier] is the
 * invariant the tests enforce.
 */
enum class ModelTier(
    val id: String,
    val label: String,
    val headline: String,
    val minRamGb: Int,
    val models: List<LocalModel>,
    val note: String,
) {
    TIER_2GB(
        "2gb", "2 GB RAM", "2 GB RAM — use this model", 2,
        listOf(LocalModel.QWEN_0_5B),
        "Only a small model fits a 2 GB phone. Short answers only — for anything bigger, use an API model.",
    ),
    TIER_4GB_64(
        "4gb-64", "4 GB RAM · 64 GB storage", "4/64 — use this model", 4,
        listOf(LocalModel.LFM3_1_7B),
        "Your phone has room for about one model — LFM3 (the default) is the pick. The other 4 GB models need more free storage; free space or pick a 128 GB phone's list below.",
    ),
    TIER_4GB_128(
        "4gb-128", "4 GB RAM · 128 GB storage", "4/128 — use this model", 4,
        listOf(LocalModel.LFM3_1_7B, LocalModel.QWEN_CODER_1_5B, LocalModel.QWEN_1_5B, LocalModel.QWEN3_1_7B),
        "Room for 2–3 models. LFM3 is the default; add the coder for code, or the 1.5B instruct for general chat.",
    ),
    TIER_6GB(
        "6gb", "6 GB RAM", "6 GB — use this model", 6,
        listOf(LocalModel.QWEN_3B, LocalModel.LLAMA_3B),
        "Stronger chat than the 4 GB models. Your phone can also run the smaller 4 GB models in the sections below.",
    ),
    TIER_8GB(
        "8gb", "8 GB RAM", "8 GB — use this model", 8,
        listOf(LocalModel.QWEN_7B, LocalModel.LLAMA_8B),
        "The strongest on-device models. Big downloads (~5 GB each) and slow on CPU — keep one at a time.",
    );

    companion object {
        val ALL: List<ModelTier> = entries

        /**
         * Which tier a phone belongs to. RAM boundaries are the named sizes
         * themselves: exactly 2 GB -> 2 GB tier, exactly 4 GB -> 4 GB,
         * exactly 6 GB -> 6 GB, exactly 8 GB -> 8 GB. (A 3 GB phone lands
         * in the 2 GB tier on purpose — 1.7B models need ~1.7 GB free RAM
         * and a 3 GB phone rarely has it.) Storage splits the 4 GB tier at
         * 96 GB: a 64 GB phone reports ~59 GB total, a 128 GB phone ~119 GB.
         */
        fun tierFor(totalRamGb: Double, totalStorageGb: Double): ModelTier = when {
            totalRamGb <= 3.0 -> TIER_2GB
            totalRamGb < 5.0 -> if (totalStorageGb < 96.0) TIER_4GB_64 else TIER_4GB_128
            totalRamGb < 7.0 -> TIER_6GB
            else -> TIER_8GB
        }

        /** Phone tier first, then the rest in size order. */
        fun orderedForPhone(phoneTier: ModelTier): List<ModelTier> =
            listOf(phoneTier) + ALL.filter { it != phoneTier }
    }
}

/** Total storage of the volume holding [dir], in bytes (0 when unreadable). */
fun totalStorageBytes(dir: File): Long =
    runCatching { StatFs(dir.absolutePath).totalBytes }.getOrDefault(0L)

/** True when the model can run on this tier at all (RAM need fits the tier). */
fun fitsTier(model: LocalModel, tier: ModelTier): Boolean = model.minRamGb <= tier.minRamGb

/** Hard block: the phone's TOTAL RAM is below what the model needs — downloading would be pointless. */
fun isHardRamBlock(model: LocalModel, totalRamGb: Double): Boolean =
    totalRamGb > 0 && totalRamGb + 0.3 < model.minRamGb

/**
 * Why this model must not be downloaded on this phone right now, or null
 * when it fits. Hard RAM blocks name the numbers ("Needs 8 GB RAM — your
 * phone has 4 GB"); low free RAM right now and low storage are soft
 * reasons the screen shows with a confirm-before-download instead.
 * Storage needs the file plus a 200 MB margin, matching LocalModelStore.
 */
fun blockReason(model: LocalModel, totalRamGb: Double, availRamGb: Double, freeBytes: Long): String? {
    if (isHardRamBlock(model, totalRamGb))
        return "Needs ${model.minRamGb} GB RAM — your phone has ${"%.0f".format(totalRamGb)} GB. It will likely crash or be very slow, so download is blocked."
    LocalModelStore.ramVerdict(model, totalRamGb, availRamGb)?.let { return it }
    val need = model.bytes + 200L * 1024 * 1024
    if (freeBytes > 0 && freeBytes < need)
        return "Not enough storage: needs ${need / 1048576} MB free."
    return null
}
