package com.example.parkmatee.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File

/**
 * Decodifica ridimensionata (lato massimo [maxSide]) e ruotata secondo l'EXIF.
 * Una foto a piena risoluzione occuperebbe decine di MB di memoria.
 */
fun decodePhoto(path: String, maxSide: Int = 1024): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)

        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) {
            sample *= 2
        }

        val bitmap = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        val rotation = when (
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        if (rotation == 0f) {
            bitmap
        } else {
            Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height,
                Matrix().apply { postRotate(rotation) },
                true
            )
        }
    } catch (e: Exception) {
        null
    }
}

/** Cancella un file di foto senza mai lanciare eccezioni. */
fun deletePhotoQuietly(path: String?) {
    if (path.isNullOrBlank()) return
    try {
        File(path).delete()
    } catch (_: Exception) {
    }
}
