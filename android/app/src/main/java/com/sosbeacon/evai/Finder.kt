package com.sosbeacon.evai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FoundFile(val uri: Uri, val name: String, val mime: String, val size: Long, val modified: Long, val folder: String)

/** AI File Finder: searches file names (media, documents and downloads Android lets the app see). */
object Finder {
    var last: List<FoundFile> = emptyList()

    private val TYPE_WORDS = mapOf(
        "image" to "image/%", "images" to "image/%", "photo" to "image/%", "photos" to "image/%", "picture" to "image/%", "pictures" to "image/%",
        "video" to "video/%", "videos" to "video/%",
        "music" to "audio/%", "audio" to "audio/%", "song" to "audio/%", "songs" to "audio/%",
        "pdf" to "application/pdf", "pdfs" to "application/pdf",
    )
    private val STOP = setOf("my", "the", "a", "an", "in", "on", "of", "files", "file", "find", "search", "for", "named", "called", "folder", "from", "all", "me", "show")

    fun search(c: Context, query: String): List<FoundFile> {
        val words = query.lowercase().split(Regex("[^a-z0-9._]+")).filter { it.isNotEmpty() }
        val mimes = words.mapNotNull { TYPE_WORDS[it] }.distinct()
        val terms = words.filter { it !in STOP && it !in TYPE_WORDS }
        val sel = StringBuilder("1=1"); val args = mutableListOf<String>()
        val useFolder = android.os.Build.VERSION.SDK_INT >= 29
        terms.forEach {
            if (useFolder) {
                sel.append(" AND (${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?)")
                args.add("%$it%"); args.add("%$it%")
            } else {
                sel.append(" AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?")
                args.add("%$it%")
            }
        }
        if (mimes.isNotEmpty()) {
            sel.append(" AND (" + mimes.joinToString(" OR ") { "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?" } + ")")
            args.addAll(mimes)
        }
        sel.append(" AND ${MediaStore.Files.FileColumns.SIZE} > 0")
        val out = mutableListOf<FoundFile>()
        val base = MediaStore.Files.getContentUri("external")
        val cols = arrayOf(
            MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.DATE_MODIFIED,
            if (useFolder) MediaStore.Files.FileColumns.RELATIVE_PATH else MediaStore.Files.FileColumns.DISPLAY_NAME,
        )
        try {
            c.contentResolver.query(base, cols, sel.toString(), args.toTypedArray(), "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { cur ->
                while (cur.moveToNext() && out.size < 15) {
                    val id = cur.getLong(0)
                    out.add(FoundFile(Uri.withAppendedPath(base, id.toString()), cur.getString(1) ?: "file", cur.getString(2) ?: "*/*",
                        cur.getLong(3), cur.getLong(4) * 1000, cur.getString(5) ?: ""))
                }
            }
        } catch (e: Exception) { }
        last = out
        return out
    }

    fun describe(f: FoundFile): String {
        val kb = f.size / 1024
        val size = if (kb > 1024) "${kb / 1024} MB" else "$kb KB"
        val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(f.modified))
        return "${f.name}  ($size, $date, ${f.folder})"
    }

    fun open(c: Context, f: FoundFile): Boolean = try {
        c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(f.uri, f.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) { false }
}
