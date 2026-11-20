package com.mazharansari.x.agent

/**
 * Creator identity (2026-11-20, user order): the app and every bot in it
 * were made by **Mazhar Ansari** — and that is the ONLY name any of them
 * may ever give when asked who made/created them.
 *
 * Single source of truth, so every model/provider path answers
 * consistently: [Agent.systemPrompt] carries [systemLine] in EVERY run
 * (plain chat, bot chat, scheduled runs, Telegram), and a bot's own
 * [com.mazharansari.x.data.BotDefinition.promptBlock] carries
 * [creatorLine] as well, so a custom bot with its own persona answers
 * the same way. No other name is written anywhere in the identity paths.
 */
object AgentIdentity {
    /** The creator's name — the only answer to "who made you". */
    const val CREATOR_NAME = "Mazhar Ansari"

    /** The fact, as a sentence a bot can speak verbatim. */
    fun creatorLine(): String =
        "You were created by $CREATOR_NAME. If anyone asks who made you, who created you, " +
            "or who your creator is, answer \"$CREATOR_NAME\" — never any other name."

    /** The identity line every system prompt carries (name is dynamic — the bot's current name). */
    fun systemLine(assistantName: String): String =
        "IDENTITY: You are ${assistantName.ifBlank { "X" }}. ${creatorLine()}"

    /** Direct answer for "who made you" — used by tests and any local identity path. */
    fun creatorAnswer(): String = "I was created by $CREATOR_NAME."
}
