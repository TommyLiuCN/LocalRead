package com.example.localread.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/** 无封面书籍的默认封面:按书名取色的渐变底 + 书名/作者 */
object CoverGenerator {

    private const val WIDTH = 480
    private const val HEIGHT = 640

    // 注意:必须用 Int ARGB。API 26 的 Long 色值高位是色彩空间 ID,直接传 0xFF...L 会抛 Invalid ID
    private val PALETTES = listOf(
        0xFF3D6DE5L.toInt() to 0xFF2B4CB8L.toInt(),
        0xFF2E8B6B.toInt() to 0xFF1E5C46.toInt(),
        0xFFB25A38.toInt() to 0xFF7E3E24.toInt(),
        0xFF6B4FA1.toInt() to 0xFF47346E.toInt(),
        0xFF2E7DA8.toInt() to 0xFF1D5675.toInt(),
    )

    fun generate(context: Context, title: String, author: String?, key: String): String {
        val (topColor, bottomColor) = PALETTES[abs(key.hashCode()) % PALETTES.size]
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, WIDTH * 0.35f, HEIGHT.toFloat(),
            topColor, bottomColor, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 52f
            isFakeBoldText = true
        }
        val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textSize = 28f
        }

        val titleText = title.ifBlank { "未命名" }
        val titleLayout = StaticLayout.Builder
            .obtain(titleText, 0, titleText.length, titlePaint, WIDTH - 112)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(8f, 1f)
            .build()

        canvas.save()
        canvas.translate(56f, (HEIGHT - titleLayout.height) * 0.42f)
        titleLayout.draw(canvas)
        canvas.restore()

        author?.takeIf { it.isNotBlank() }?.let {
            val aw = authorPaint.measureText(it)
            canvas.drawText(it, (WIDTH - aw) / 2f, HEIGHT * 0.72f, authorPaint)
        }

        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val file = File(dir, "$key.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        bitmap.recycle()
        return file.absolutePath
    }
}
