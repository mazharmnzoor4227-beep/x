package com.mazharansari.x.ui

/**
 * The engines behind the app's sections (v0.4, mazhar 1 Nov 2:28 PM — kept fully separate):
 *  - ASK: API-key providers (Groq, Gemini, OpenRouter, …) + "Free (slow)". Never Antigravity.
 *
 * 2026-11-19: there is NO Build mode any more (docs/X-NEW-UI-SPEC.md §0).
 * [BUILD] stays ONLY as a legacy persisted value: old saved conversations
 * carry section "build", and SectionSeparationTest asserts Section.of("build")
 * == BUILD. No runtime code assigns it any more — ChatViewModel always opens
 * chats as ASK, and a project chat runs its build turns because it has a
 * projectId, not because it is in a Build section. Do not add UI that
 * branches on BUILD, and do not reintroduce a Build mode.
 */
enum class Section(val id: String) {
    ASK("ask"),
    BUILD("build");

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: ASK }
}
