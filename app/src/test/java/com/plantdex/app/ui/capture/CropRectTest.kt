package com.plantdex.app.ui.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropRectTest {
    private val eps = 1e-5f

    @Test
    fun moveStaysInsidePhoto() {
        val moved = CropRect(0.2f, 0.2f, 0.6f, 0.6f).moved(0.9f, -0.5f)
        assertEquals(0.6f, moved.left, eps)
        assertEquals(1f, moved.right, eps)
        assertEquals(0f, moved.top, eps)
        assertEquals(0.4f, moved.bottom, eps)
    }

    @Test
    fun resizeKeepsMinimumSize() {
        val r = CropRect(0.2f, 0.2f, 0.6f, 0.6f).resized(CropRect.Handle.TopLeft, 0.9f, 0.9f)
        assertEquals(0.6f - CropRect.MIN_SIZE, r.left, eps)
        assertEquals(0.6f - CropRect.MIN_SIZE, r.top, eps)
        assertEquals(0.6f, r.right, eps)
    }

    @Test
    fun resizeClampsToEdges() {
        val r = CropRect(0.2f, 0.2f, 0.6f, 0.6f).resized(CropRect.Handle.BottomRight, 2f, 2f)
        assertEquals(1f, r.right, eps)
        assertEquals(1f, r.bottom, eps)
    }

    @Test
    fun handleDetection() {
        val r = CropRect(0.2f, 0.2f, 0.6f, 0.6f)
        assertEquals(CropRect.Handle.TopLeft, r.handleAt(0.21f, 0.19f, 0.05f, 0.05f))
        assertEquals(CropRect.Handle.BottomRight, r.handleAt(0.62f, 0.58f, 0.05f, 0.05f))
        assertEquals(CropRect.Handle.Move, r.handleAt(0.4f, 0.4f, 0.05f, 0.05f))
        assertNull(r.handleAt(0.9f, 0.9f, 0.05f, 0.05f))
    }
}
