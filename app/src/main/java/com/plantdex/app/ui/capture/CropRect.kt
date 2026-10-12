package com.plantdex.app.ui.capture

/**
 * 사진 자르기 영역. 좌표는 사진 크기에 대한 0..1 비율입니다.
 * 화면과 무관한 순수 계산이라 JVM 단위 테스트로 확인합니다.
 */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isFull: Boolean get() = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f

    enum class Handle { TopLeft, TopRight, BottomLeft, BottomRight, Move }

    /** 영역 전체를 옮깁니다. 사진 밖으로 나가지 않습니다. */
    fun moved(dx: Float, dy: Float): CropRect {
        val x = (left + dx).coerceIn(0f, 1f - width)
        val y = (top + dy).coerceIn(0f, 1f - height)
        return CropRect(x, y, x + width, y + height)
    }

    /** 모서리를 끌어 크기를 바꿉니다. 최소 크기 [minSize] 를 지킵니다. */
    fun resized(handle: Handle, dx: Float, dy: Float, minSize: Float = MIN_SIZE): CropRect = when (handle) {
        Handle.Move -> moved(dx, dy)
        Handle.TopLeft -> copy(
            left = (left + dx).coerceIn(0f, right - minSize),
            top = (top + dy).coerceIn(0f, bottom - minSize),
        )
        Handle.TopRight -> copy(
            right = (right + dx).coerceIn(left + minSize, 1f),
            top = (top + dy).coerceIn(0f, bottom - minSize),
        )
        Handle.BottomLeft -> copy(
            left = (left + dx).coerceIn(0f, right - minSize),
            bottom = (bottom + dy).coerceIn(top + minSize, 1f),
        )
        Handle.BottomRight -> copy(
            right = (right + dx).coerceIn(left + minSize, 1f),
            bottom = (bottom + dy).coerceIn(top + minSize, 1f),
        )
    }

    /**
     * 터치 위치 ([x], [y] 비율) 에서 잡은 손잡이. 모서리에서 [toleranceX]/[toleranceY] 안이면 모서리,
     * 영역 안쪽이면 이동, 그 밖이면 null.
     */
    fun handleAt(x: Float, y: Float, toleranceX: Float, toleranceY: Float): Handle? {
        fun near(cx: Float, cy: Float) = kotlin.math.abs(x - cx) <= toleranceX && kotlin.math.abs(y - cy) <= toleranceY
        return when {
            near(left, top) -> Handle.TopLeft
            near(right, top) -> Handle.TopRight
            near(left, bottom) -> Handle.BottomLeft
            near(right, bottom) -> Handle.BottomRight
            x in left..right && y in top..bottom -> Handle.Move
            else -> null
        }
    }

    companion object {
        const val MIN_SIZE = 0.15f
        val Full = CropRect(0f, 0f, 1f, 1f)
        val Initial = CropRect(0.1f, 0.1f, 0.9f, 0.9f)
    }
}
