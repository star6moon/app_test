package com.plantdex.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * 공유용 도감 카드 이미지: 크림색 종이 위에 사진을 표본처럼 끼우고, 아래에 이름·학명·날짜·장소를 적습니다.
 * 인스타그램·카카오톡 같은 앱으로 바로 보낼 수 있게 JPEG 파일로 만들어 FileProvider 주소를 돌려줍니다.
 */
class ShareCard(private val context: Context, private val client: OkHttpClient) {

    /** 사진을 내려받아 카드를 그리고 공유 가능한 Uri 를 돌려줍니다. 사진을 못 받으면 IOException. */
    suspend fun render(photoUrl: String, text: ShareCardText): Uri = withContext(Dispatchers.IO) {
        val photo = download(photoUrl)
        val card = draw(photo, text)
        photo.recycle()
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "plantdex-card.jpg")
        file.outputStream().use { card.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        card.recycle()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun download(url: String): Bitmap {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val bytes = response.body?.bytes() ?: throw IOException("empty body")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= PHOTO_SIZE && bounds.outHeight / (sample * 2) >= PHOTO_SIZE) sample *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw IOException("decode failed")
        }
    }

    private fun draw(photo: Bitmap, text: ShareCardText): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PAPER)

        // 종이 카드 + 표본 라벨처럼 가는 이중 테두리
        val card = RectF(MARGIN, MARGIN, WIDTH - MARGIN, HEIGHT - MARGIN)
        canvas.drawRoundRect(card, 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD })
        canvas.drawRoundRect(card, 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = OUTLINE; style = Paint.Style.STROKE; strokeWidth = 3f })
        val inner = RectF(card).apply { inset(14f, 14f) }
        canvas.drawRoundRect(inner, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = OUTLINE; style = Paint.Style.STROKE; strokeWidth = 1.5f })

        // 사진: 정사각형으로 가운데를 잘라 둥근 모서리로, 얇은 테두리
        val left = MARGIN + INSET
        val photoRect = RectF(left, MARGIN + INSET, WIDTH - MARGIN - INSET, MARGIN + INSET + PHOTO_SIZE)
        val side = minOf(photo.width, photo.height)
        val scale = PHOTO_SIZE / side
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(photoRect.left - (photo.width * scale - PHOTO_SIZE) / 2, photoRect.top - (photo.height * scale - PHOTO_SIZE) / 2)
        }
        val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
        canvas.drawRoundRect(photoRect, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        canvas.drawRoundRect(photoRect, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = OUTLINE; style = Paint.Style.STROKE; strokeWidth = 2f })

        // 사진 왼쪽 위의 작은 라벨 (도감 기록)
        label(canvas, "PlantDex 도감 기록", photoRect.left + 20f, photoRect.top + 20f)

        // 글: 이름 크게, 영어·학명 작게, 가는 구분선, 날짜·장소
        val maxWidth = WIDTH - 2 * (MARGIN + INSET)
        var y = photoRect.bottom + 56f
        y = line(canvas, text.korean, y, 72f, INK, Typeface.DEFAULT_BOLD, maxWidth)
        text.english?.let { y = line(canvas, it, y + 8f, 36f, MUTED, Typeface.DEFAULT, maxWidth) }
        y = line(canvas, text.scientificName, y + 6f, 36f, BARK, Typeface.create(Typeface.DEFAULT, Typeface.ITALIC), maxWidth)
        y += 26f
        canvas.drawLine(left, y, WIDTH - MARGIN - INSET, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = OUTLINE; strokeWidth = 2f })
        y = line(canvas, "📅 " + text.whenWhere, y + 22f, 34f, MUTED, Typeface.DEFAULT, maxWidth)
        text.memo?.let { y = line(canvas, "\u201C$it\u201D", y + 16f, 34f, INK, Typeface.DEFAULT, maxWidth) }

        // 아래 꼬리표
        val footerY = HEIGHT - MARGIN - INSET - 6f
        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = LEAF; textSize = 34f; typeface = Typeface.DEFAULT_BOLD }
        canvas.drawText("PlantDex 🌿", left, footerY, footer)
        val tag = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 28f; textAlign = Paint.Align.RIGHT }
        canvas.drawText("나만의 식물 도감 · #PlantDex", WIDTH - MARGIN - INSET, footerY, tag)
        return bitmap
    }

    /** 사진 위에 얹는 작은 흰 알약 라벨 */
    private fun label(canvas: Canvas, text: String, x: Float, y: Float) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = LEAF; textSize = 26f; typeface = Typeface.DEFAULT_BOLD }
        val w = paint.measureText(text)
        val h = 44f
        val rect = RectF(x, y, x + w + 32f, y + h)
        canvas.drawRoundRect(rect, h / 2, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 255, 253, 248) })
        canvas.drawText(text, x + 16f, y + h / 2 - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2, paint)
    }

    /** 한 줄 쓰기 (넘치면 … 으로 줄임). 다음 줄의 y 를 돌려줍니다. */
    private fun line(canvas: Canvas, text: String, top: Float, size: Float, color: Int, typeface: Typeface, maxWidth: Float): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; this.typeface = typeface }
        val shown = TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
        val baseline = top - paint.fontMetrics.ascent
        canvas.drawText(shown, MARGIN + INSET, baseline, paint)
        return baseline + paint.fontMetrics.descent
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1520
        const val MARGIN = 40f
        const val INSET = 36f
        const val PHOTO_SIZE = 1080f - 2 * (40f + 36f)
        // ui/theme/Color.kt 의 밝은 팔레트와 같은 색
        val PAPER = Color.parseColor("#F5F0E6")
        val CARD = Color.parseColor("#FFFDF8")
        val OUTLINE = Color.parseColor("#D9CFBF")
        val INK = Color.parseColor("#23201A")
        val MUTED = Color.parseColor("#6B6458")
        val BARK = Color.parseColor("#7A5B3A")
        val LEAF = Color.parseColor("#2F5D3A")
    }
}
