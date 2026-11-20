package com.mazharansari.x.data

/**
 * Phase 2 — a bot's logo, chosen by the user.
 *
 * Stored as one string on [BotDefinition.logo]:
 * - "builtin:<id>" — one of [BotLogo.BUILTINS] (the UI maps the id to its glyph).
 * - "file:<absolute path>" — a static image the user picked, copied into the
 *   app's own storage so it survives the gallery permission being revoked.
 * - "video:<absolute path>" — a short looping video clip the user picked as
 *   the bot's animated avatar (muted, loops, lightweight — see
 *   [com.mazharansari.x.ui.bots.BotLogoView]).
 * - "3d:<id>" — one of [BotLogo.THREE_D]: a 3D-style drawn tile (layered
 *   shading, no .glb renderer is wired in-tree — the tile is the honest
 *   in-app 3D look until a real 3D avatar engine lands).
 * - "" — no logo yet; the UI shows the bot's monogram instead (the default,
 *   always professional).
 * Original X code.
 */
sealed interface BotLogo {
    data class Builtin(val id: String) : BotLogo
    data class Custom(val path: String) : BotLogo
    data class Video(val path: String) : BotLogo
    data class ThreeD(val id: String) : BotLogo
    data object None : BotLogo

    companion object {
        const val PREFIX_BUILTIN = "builtin:"
        const val PREFIX_CUSTOM = "file:"
        const val PREFIX_VIDEO = "video:"
        const val PREFIX_3D = "3d:"

        fun parse(raw: String): BotLogo = when {
            raw.startsWith(PREFIX_VIDEO) -> {
                val path = raw.removePrefix(PREFIX_VIDEO).trim()
                if (path.isEmpty()) None else Video(path)
            }
            raw.startsWith(PREFIX_3D) -> {
                val id = raw.removePrefix(PREFIX_3D).trim()
                if (id.isEmpty() || !isKnownThreeD(id)) None else ThreeD(id)
            }
            raw.startsWith(PREFIX_BUILTIN) -> {
                val id = raw.removePrefix(PREFIX_BUILTIN).trim()
                if (id.isEmpty()) None else Builtin(id)
            }
            raw.startsWith(PREFIX_CUSTOM) -> {
                val path = raw.removePrefix(PREFIX_CUSTOM).trim()
                if (path.isEmpty()) None else Custom(path)
            }
            raw.isBlank() -> None
            else -> Builtin(raw.trim()) // legacy: a bare builtin id
        }

        fun builtin(id: String): String = "$PREFIX_BUILTIN$id"
        fun custom(path: String): String = "$PREFIX_CUSTOM$path"
        fun video(path: String): String = "$PREFIX_VIDEO$path"
        fun threeD(id: String): String = "$PREFIX_3D$id"

        /** Built-in logo ids — the UI maps each id to its glyph. */
        val BUILTINS: List<BuiltinDef> = listOf(
            BuiltinDef("brain", "Brain"),
            BuiltinDef("agent", "Agent"),
            BuiltinDef("rocket", "Rocket"),
            BuiltinDef("sparkles", "Sparkles"),
            BuiltinDef("bolt", "Bolt"),
            BuiltinDef("hammer", "Hammer"),
            BuiltinDef("globe", "Globe"),
            BuiltinDef("person", "Person"),
            BuiltinDef("gem", "Gem"),
            BuiltinDef("shield", "Shield"),
            BuiltinDef("wand", "Wand"),
            BuiltinDef("cube", "Cube"),
        )

        fun isKnownBuiltin(id: String): Boolean = BUILTINS.any { it.id == id }

        /**
         * 3D-style icon ids — drawn tiles with layered shading (see BotLogoView).
         * No .glb / 3D-model renderer is wired in-tree, so these are the 3D
         * look the app can actually render today; a picked video ("video:")
         * is the animated alternative.
         */
        val THREE_D: List<BuiltinDef> = listOf(
            BuiltinDef("bronze", "3D Bronze"),
            BuiltinDef("slate", "3D Slate"),
            BuiltinDef("plum", "3D Plum"),
            BuiltinDef("navy", "3D Navy"),
            BuiltinDef("stone", "3D Stone"),
        )

        fun isKnownThreeD(id: String): Boolean = THREE_D.any { it.id == id }
    }
}

data class BuiltinDef(val id: String, val label: String)

/** The icon kinds the profile editor offers, in the order it shows them. */
enum class BotIconKind(val label: String) {
    MONOGRAM("Monogram"),
    IMAGE("Static image"),
    THREE_D("3D"),
    VIDEO("Video"),
}
