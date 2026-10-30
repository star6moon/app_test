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

        // 종이 카드
        val card = RectF(MARGIN, MARGIN, WIDTH - MARGIN, HEIGHT - MARGIN)
        canvas.drawRoundRect(card, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD })
        canvas.drawRoundRect(card, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = OUTLINE; style = Paint.Style.STROKE; strokeWidth = 3f })

        // 사진: 정사각형으로 가운데를 잘라 둥근 모서리로
        val photoRect = RectF(MARGIN + INSET, MARGIN + INSET, WIDTH - MARGIN - INSET, MARGIN + INSET + PHOTO_SIZE)
        val side = minOf(photo.width, photo.height)
        val scale = PHOTO_SIZE / side
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(photoRect.left - (photo.width * scale - PHOTO_SIZE) / 2, photoRect.top - (photo.height * scale - PHOTO_SIZE) / 2)
        }
        val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
        canvas.drawRoundRect(photoRect, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })

        // 글
        var y = photoRect.bottom + 84f
        val maxWidth = WIDTH - 2 * (MARGIN + INSET)
        y = line(canvas, text.korean, y, 64f, INK, Typeface.DEFAULT_BOLD, maxWidth)
        text.english?.let { y = line(canvas, it, y + 6f, 36f, MUTED, Typeface.DEFAULT, maxWidth) }
        y = line(canvas, text.scientificName, y + 6f, 36f, BARK, Typeface.create(Typeface.DEFAULT, Typeface.ITALIC), maxWidth)
        y = line(canvas, text.whenWhere, y + 26f, 34f, MUTED, Typeface.DEFAULT, maxWidth)
        text.memo?.let { y = line(canvas, "“$it”", y + 18f, 34f, INK, Typeface.DEFAULT, maxWidth) }

        // 아래 꼬리표
        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = LEAF; textSize = 32f; typeface = Typeface.DEFAULT_BOLD }
        canvas.drawText("PlantDex 🌿", MARGIN + INSET, HEIGHT - MARGIN - INSET, footer)
        val tag = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 28f; textAlign = Paint.Align.RIGHT }
        canvas.drawText("#PlantDex", WIDTH - MARGIN - INSET, HEIGHT - MARGIN - INSET, tag)
        return bitmap
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
        const val HEIGHT = 1440
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
