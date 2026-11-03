package com.example.parkmatee.ui.stats

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/**
 * Heat map disegnata a piastrelle (tile) sopra la GoogleMap, senza librerie esterne e senza rete:
 * ogni punto e un gradiente radiale; dove i punti si sovrappongono l'opacita si accumula e la
 * scala di colore passa da blu a verde, giallo, rosso. Il raggio e in pixel, quindi cambia di
 * estensione geografica con lo zoom (come nelle heat map standard).
 * La classe e immutabile: getTile puo essere chiamato da piu thread.
 */
class HeatmapTileProvider(
    private val points: List<HeatPoint>,
    private val radiusPx: Int = 44
) : TileProvider {

    private val maxWeight = points.maxOfOrNull { it.weight }?.takeIf { it > 0.0 } ?: 1.0

    override fun getTile(x: Int, y: Int, zoom: Int): Tile {
        val worldSize = TILE_SIZE * 2.0.pow(zoom)
        val bitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = radiusPx.toFloat()
        var drawn = false

        for (p in points) {
            val px = (p.longitude + 180.0) / 360.0 * worldSize - x * TILE_SIZE
            val sinLat = sin(Math.toRadians(p.latitude)).coerceIn(-0.9999, 0.9999)
            val py = (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * worldSize - y * TILE_SIZE
            if (px < -r || px > TILE_SIZE + r || py < -r || py > TILE_SIZE + r) continue

            val strength = (0.35 + 0.5 * (p.weight / maxWeight)).coerceAtMost(1.0)
            val alpha = (strength * 255).toInt()
            paint.shader = RadialGradient(
                px.toFloat(), py.toFloat(), r,
                intArrayOf(Color.argb(alpha, 0, 0, 0), Color.argb(0, 0, 0, 0)),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(px.toFloat(), py.toFloat(), r, paint)
            drawn = true
        }

        if (!drawn) {
            bitmap.recycle()
            return TileProvider.NO_TILE
        }

        val pixels = IntArray(TILE_SIZE * TILE_SIZE)
        bitmap.getPixels(pixels, 0, TILE_SIZE, 0, 0, TILE_SIZE, TILE_SIZE)
        for (i in pixels.indices) {
            val a = pixels[i] ushr 24
            pixels[i] = if (a == 0) 0 else PALETTE[a]
        }
        bitmap.setPixels(pixels, 0, TILE_SIZE, 0, 0, TILE_SIZE, TILE_SIZE)

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return Tile(TILE_SIZE, TILE_SIZE, out.toByteArray())
    }

    companion object {
        private const val TILE_SIZE = 256

        /** Colori della scala: poco -> blu, molto -> rosso. Usati anche dalla legenda. */
        val STOPS = intArrayOf(
            0xFF2B6CFF.toInt(),
            0xFF2ECC71.toInt(),
            0xFFFFD60A.toInt(),
            0xFFE53935.toInt()
        )

        private fun colorAt(t: Float): Int {
            val scaled = t.coerceIn(0f, 1f) * (STOPS.size - 1)
            val index = scaled.toInt().coerceAtMost(STOPS.size - 2)
            return ColorUtils.blendARGB(STOPS[index], STOPS[index + 1], scaled - index)
        }

        // Tabella opacita -> colore, calcolata una volta sola
        private val PALETTE = IntArray(256) { a ->
            val rgb = colorAt(a / 255f)
            ColorUtils.setAlphaComponent(rgb, (a * 0.85f).toInt().coerceIn(0, 255))
        }
    }
}
