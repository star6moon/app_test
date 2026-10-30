package com.plantdex.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.plantdex.app.data.art.CircleOp
import com.plantdex.app.data.art.PathCmd
import com.plantdex.app.data.art.PathOp
import com.plantdex.app.data.art.PlantArt
import com.plantdex.app.data.art.PlantArtRenderer

/**
 * [PlantArt] 사양으로 그린 식물 그림. 크기는 modifier 로 정합니다 (정사각형 권장).
 * 이름이 옆에 없는 자리에서는 [contentDescription] 을 주어 화면 읽기(TalkBack)가 식물을 알 수 있게 합니다.
 */
@Composable
fun PlantArtImage(art: PlantArt, modifier: Modifier = Modifier, contentDescription: String? = null, small: Boolean = false) {
    // 지도 마커처럼 작은 그림은 선을 굵게 그린 판을 씁니다.
    val ops = remember(art, small) { PlantArtRenderer.render(art, small) }
    val described = if (contentDescription != null) {
        modifier.semantics { role = Role.Image; this.contentDescription = contentDescription }
    } else {
        modifier
    }
    Canvas(described) {
        val scale = size.minDimension / 100f
        val dx = (size.width - 100f * scale) / 2f
        val dy = (size.height - 100f * scale) / 2f
        fun x(v: Float) = dx + v * scale
        fun y(v: Float) = dy + v * scale

        for (op in ops) {
            when (op) {
                is CircleOp -> {
                    val center = Offset(x(op.cx), y(op.cy))
                    op.fill?.let { drawCircle(Color(it), op.r * scale, center) }
                    op.stroke?.let { drawCircle(Color(it), op.r * scale, center, style = Stroke(op.strokeWidth * scale)) }
                }
                is PathOp -> {
                    val path = op.toPath(::x, ::y)
                    op.fill?.let { drawPath(path, Color(it)) }
                    op.stroke?.let {
                        drawPath(
                            path,
                            Color(it),
                            style = Stroke(op.strokeWidth * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                }
            }
        }
    }
}

private fun PathOp.toPath(x: (Float) -> Float, y: (Float) -> Float): Path = Path().apply {
    for (cmd in commands) {
        when (cmd) {
            is PathCmd.MoveTo -> moveTo(x(cmd.x), y(cmd.y))
            is PathCmd.LineTo -> lineTo(x(cmd.x), y(cmd.y))
            is PathCmd.QuadTo -> quadraticTo(x(cmd.x1), y(cmd.y1), x(cmd.x), y(cmd.y))
            is PathCmd.CubicTo -> cubicTo(x(cmd.x1), y(cmd.y1), x(cmd.x2), y(cmd.y2), x(cmd.x), y(cmd.y))
            PathCmd.Close -> close()
        }
    }
}
