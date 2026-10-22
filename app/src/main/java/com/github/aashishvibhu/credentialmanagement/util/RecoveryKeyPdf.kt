package com.github.aashishvibhu.credentialmanagement.util

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.text.DateFormat
import java.util.Date

/** Writes a one-page A4 PDF with the recovery key and how to use it. */
fun writeRecoveryKeyPdf(context: Context, uri: Uri, recoveryKey: String, account: String?) {
    val document = PdfDocument()
    val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    val canvas = page.canvas

    val title = Paint().apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    val body  = Paint().apply { textSize = 12f; isAntiAlias = true }
    val key   = Paint().apply { textSize = 24f; typeface = Typeface.MONOSPACE; isAntiAlias = true }

    var y = 80f
    fun line(text: String, paint: Paint, gap: Float = 20f) { canvas.drawText(text, 56f, y, paint); y += gap }

    line("Vault recovery key", title, 34f)
    line("Created ${DateFormat.getDateInstance(DateFormat.LONG).format(Date())}", body)
    if (account != null) line("Google account: $account", body)
    y += 24f
    line(recoveryKey, key, 48f)
    line("Use this key if you forget your master password:", body)
    line("open the app, choose \"Use recovery key instead\", and type it in.", body, 32f)
    line("Keep this page somewhere safe and private, away from your phone.", body)
    line("Anyone with this key and your Google account can open your vault.", body)

    document.finishPage(page)
    try {
        context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
            ?: error("Couldn't open the file")
    } finally {
        document.close()
    }
}
