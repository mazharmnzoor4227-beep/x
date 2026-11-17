package com.mazharansari.x.ui.home

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.mazharansari.x.data.Artifact
import java.io.File

object FileActions {
    /** Copies a produced file into the phone's public Downloads folder. Returns a message for the toast. */
    fun download(ctx: Context, a: Artifact): String = runCatching {
        val src = File(a.path)
        com.mazharansari.x.security.SecretScanner.leaveCheck(src)?.let { return it }
        // axion-p1.6: videos / audio / pictures go where Gallery and music apps look
        com.mazharansari.x.agent.GallerySaver.save(ctx, src, a.mime, a.name)?.let { return "Saved to $it" }
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, a.name)
                put(MediaStore.Downloads.MIME_TYPE, a.mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Axion")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
            ctx.contentResolver.openOutputStream(uri)!!.use { o -> src.inputStream().use { it.copyTo(o) } }
            "Saved to Downloads/Axion/${a.name}"
        } else {
            val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("no storage")
            src.copyTo(File(dir, a.name), overwrite = true)
            "Saved to ${dir.absolutePath}/${a.name}"
        }
    }.getOrElse { "Couldn't save: ${it.message}" }
}

/** Share a produced file with another app (moved here from HomeScreen — the chat screen and Settings both use it). */
fun shareFile(ctx: Context, a: Artifact) {
    com.mazharansari.x.security.SecretScanner.leaveCheck(File(a.path))?.let { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_LONG).show(); return }
    runCatching {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(a.path))
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(a.mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), a.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Open a produced file in another app (moved here from HomeScreen). */
fun openFile(ctx: Context, a: Artifact) {
    com.mazharansari.x.security.SecretScanner.leaveCheck(File(a.path))?.let { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_LONG).show(); return }
    runCatching {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(a.path))
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        android.widget.Toast.makeText(ctx, "No app to open ${a.name}", android.widget.Toast.LENGTH_SHORT).show()
    }
}
