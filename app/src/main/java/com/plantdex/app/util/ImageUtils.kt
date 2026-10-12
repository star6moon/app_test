package com.plantdex.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ImageUtils {

    /**
     * 카메라 원본을 업로드용 JPEG 로 변환합니다.
     * - 긴 변을 [maxSize] 이하로 축소 (Pl@ntNet 권장 해상도 및 저장 용량 절약)
     * - EXIF 회전 정보를 픽셀에 반영 (EXIF 가 제거돼도 올바른 방향으로 보이도록)
     */
    suspend fun prepareForUpload(
        source: File,
        target: File,
        maxSize: Int = 1600,
        quality: Int = 85,
    ): File = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxSize || bounds.outHeight / (sampleSize * 2) >= maxSize) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: error("이미지를 읽을 수 없습니다: ${source.name}")

        val scale = maxSize.toFloat() / maxOf(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            postRotate(rotationDegrees(source).toFloat())
        }
        val output = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        target.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        if (output !== decoded) output.recycle()
        decoded.recycle()
        target
    }

    /** 목록·지도용 작은 사진 (긴 변 [maxSize]px). [source] 는 이미 회전 보정된 업로드용 JPEG 입니다. */
    suspend fun makeThumbnail(source: File, target: File, maxSize: Int = 400): File = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxSize && bounds.outHeight / (sampleSize * 2) >= maxSize) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: error("이미지를 읽을 수 없습니다: ${source.name}")
        val scale = (maxSize.toFloat() / maxOf(decoded.width, decoded.height)).coerceAtMost(1f)
        val output = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }
        target.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        if (output !== decoded) output.recycle()
        decoded.recycle()
        target
    }

    /** 이미지 크기 (가로, 세로). 파일을 전부 읽지 않습니다. */
    fun imageSize(file: File): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.outWidth to bounds.outHeight
    }

    /**
     * [source] 의 일부를 잘라 [target] 에 저장합니다.
     * 좌표는 0..1 비율이며, [source] 는 이미 회전 보정된 업로드용 JPEG 입니다.
     */
    suspend fun crop(
        source: File,
        target: File,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        quality: Int = 90,
    ): File = withContext(Dispatchers.IO) {
        val decoded = BitmapFactory.decodeFile(source.absolutePath)
            ?: error("이미지를 읽을 수 없습니다: ${source.name}")
        val x = (left.coerceIn(0f, 1f) * decoded.width).toInt().coerceIn(0, decoded.width - 1)
        val y = (top.coerceIn(0f, 1f) * decoded.height).toInt().coerceIn(0, decoded.height - 1)
        val w = ((right.coerceIn(0f, 1f) - left) * decoded.width).toInt().coerceIn(1, decoded.width - x)
        val h = ((bottom.coerceIn(0f, 1f) - top) * decoded.height).toInt().coerceIn(1, decoded.height - y)
        val output = Bitmap.createBitmap(decoded, x, y, w, h)
        target.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        if (output !== decoded) output.recycle()
        decoded.recycle()
        target
    }

    private fun rotationDegrees(file: File): Int =
        when (ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
}
