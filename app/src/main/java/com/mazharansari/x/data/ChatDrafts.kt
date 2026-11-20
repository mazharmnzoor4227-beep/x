package com.mazharansari.x.data

/**
 * Phase 2 — one input draft per conversation.
 *
 * The composer draft used to live in a single view-model field, so switching
 * chats carried the half-typed text into the wrong chat (and New chat wiped
 * it). Drafts are now kept per conversation id: leaving a chat saves what was
 * typed, opening it again restores it. Pure logic, no Android dependency.
 * Original X code.
 */
class ConversationDrafts {
    // Synchronized: ChatViewModel.open() restores a draft from its IO load
    // coroutine while save() runs on Main (new chat / leaving a chat), so the
    // map is now touched from two threads.
    private val drafts = HashMap<String, String>()

    /** Save the draft for [id]; a blank draft clears the saved one. */
    @Synchronized fun save(id: String?, text: String) {
        if (id == null) return
        if (text.isBlank()) drafts.remove(id) else drafts[id] = text
    }

    /** The saved draft for [id], or "" when there is none. */
    @Synchronized fun restore(id: String?): String = if (id == null) "" else drafts[id].orEmpty()

    /** Drop the draft for [id] (after it was sent). */
    @Synchronized fun clear(id: String?) {
        if (id != null) drafts.remove(id)
    }

    @Synchronized fun count(): Int = drafts.size
}
