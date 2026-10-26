package com.lubrcapp.lubrcapp.shell

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Phase 25.7 — PDF sharing (§100 native sharing): writes PDF bytes
 * to the app cache and builds the ACTION_SEND intent through a
 * FileProvider URI. The cache file lives under cacheDir/shared/
 * (see res/xml/file_paths.xml); the provider authority is
 * "<package>.fileprovider" (see the manifest). No storage
 * permission is needed: FileProvider grants the receiving app
 * read access to exactly this URI. Download (MediaStore) and
 * system Print are later packages, not this helper.
 */
fun writeSharedPdf(context: Context, fileName: String, bytes: ByteArray): Uri {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, fileName)
    file.writeBytes(bytes)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

fun pdfShareIntent(uri: Uri): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
