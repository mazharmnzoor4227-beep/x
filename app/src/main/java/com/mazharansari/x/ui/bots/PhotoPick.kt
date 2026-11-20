package com.mazharansari.x.ui.bots

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Shared photo/media pick helper (2026-11-20): the bot profile editor
 * ([BotProfileSheet]) and the user's own profile ([CustomizeScreen]) both
 * pick a photo from the device and keep it the same way — copied into the
 * app's own storage, so the picture survives the gallery permission being
 * revoked, and a missing/unreadable/non-image file never crashes anything:
 * a failed copy is deleted and null is returned, and the caller shows a
 * toast and keeps the old picture.
 *
 * - [dirName] is the app-private folder ("bot-logos" for bots,
 *   "profile-photos" for the user).
 * - [maxBytes] caps the stream — over the cap the partial file is deleted.
 * - [validateImage] (image picks only) decodes just the bounds: a file
 *   that is not a real image is refused instead of being stored and then
 *   failing to render. Video picks skip this (they are not images).
 */
fun copyPickedMedia(
    ctx: Context,
    dirName: String,
    key: String,
    uri: Uri,
    ext: String,
    maxBytes: Long,
    validateImage: Boolean = false,
): String? {
    val dir = File(ctx.filesDir, dirName).apply { mkdirs() }
    val safeKey = key.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
    val out = File(dir, "$safeKey-${System.currentTimeMillis()}.$ext")
    return runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            var total = 0L
            out.outputStream().use { o ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) throw IllegalStateException("picked file over the size cap")
                    o.write(buf, 0, n)
                }
            }
        } ?: throw IllegalStateException("cannot open picked file")
        if (validateImage) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(out.absolutePath, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) throw IllegalStateException("picked file is not an image")
        }
        out.absolutePath
    }.onFailure { out.delete() }.getOrNull()
}
