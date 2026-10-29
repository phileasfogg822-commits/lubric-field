package com.lubrcapp.lubrcapp.feature.parties

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.InputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 32.2 — the khata row avatar. A photo renders when [photoUri]
 * loads; anything else (blank URI, unparseable image, no permission,
 * no network) falls back to the name initials, so a row never shows a
 * broken image. No image-loading library: the bitmap is decoded on IO
 * with a downsample cap, and every failure returns null.
 */
fun initialsFor(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"
    val first = words[0].first().uppercaseChar()
    val second = words.getOrNull(1)?.first()?.uppercaseChar()
    return if (second == null) first.toString() else "$first$second"
}

@Composable
fun PartyAvatar(
    photoUri: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, photoUri) {
        value = if (photoUri.isNullOrBlank()) null else loadPhotoBitmap(context, photoUri)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                initialsFor(name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private suspend fun loadPhotoBitmap(context: Context, uri: String) = withContext(Dispatchers.IO) {
    try {
        val parsed = Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openPhotoStream(context, parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        val sample = maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / 192)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = openPhotoStream(context, parsed)?.use { BitmapFactory.decodeStream(it, null, options) }
        decoded?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

private fun openPhotoStream(context: Context, uri: Uri): InputStream? =
    when (uri.scheme?.lowercase()) {
        "http", "https" -> URL(uri.toString()).openStream()
        else -> context.contentResolver.openInputStream(uri)
    }
