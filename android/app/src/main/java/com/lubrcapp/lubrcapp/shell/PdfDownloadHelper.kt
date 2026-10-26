package com.lubrcapp.lubrcapp.shell

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/**
 * Phase 25.8 — Download PDF (§99): writes PDF bytes into the
 * user's Downloads collection and returns the resulting Uri, or
 * null when the write fails for any reason (the caller reports
 * the failure; this helper never throws to composition). On
 * Android 10+ the MediaStore Downloads collection needs no
 * permission: the row is inserted pending, filled, then
 * published. Below Android 10 there is no Downloads
 * collection, so the legacy public Downloads directory is
 * used — that branch needs WRITE_EXTERNAL_STORAGE, which the
 * caller requests first (manifest caps the permission at API
 * 28). CI's emulator runs API 35, so only the MediaStore
 * branch is exercised there; the legacy branch is the
 * documented untested path, same class as 12C-09.
 */
fun downloadPdf(context: Context, fileName: String, bytes: ByteArray): Uri? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            downloadViaMediaStore(context, fileName, bytes)
        } else {
            downloadViaLegacyDirectory(fileName, bytes)
        }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}

private fun downloadViaMediaStore(context: Context, fileName: String, bytes: ByteArray): Uri? {
    val resolver = context.contentResolver
    val pending = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending) ?: return null
    try {
        resolver.openOutputStream(uri)?.use { out -> out.write(bytes) } ?: run {
            resolver.delete(uri, null, null)
            return null
        }
    } catch (e: IOException) {
        resolver.delete(uri, null, null)
        return null
    }
    val published = ContentValues().apply {
        put(MediaStore.Downloads.IS_PENDING, 0)
    }
    resolver.update(uri, published, null, null)
    return uri
}

@Suppress("DEPRECATION")
private fun downloadViaLegacyDirectory(fileName: String, bytes: ByteArray): Uri? {
    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    dir.mkdirs()
    val file = File(dir, fileName)
    file.writeBytes(bytes)
    return Uri.fromFile(file)
}
