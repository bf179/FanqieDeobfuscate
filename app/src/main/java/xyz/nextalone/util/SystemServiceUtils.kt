package xyz.nextalone.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object SystemServiceUtils {
    fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("label", text))
    }
}
