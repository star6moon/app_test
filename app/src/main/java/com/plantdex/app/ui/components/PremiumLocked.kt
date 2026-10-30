package com.plantdex.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.plantdex.app.R

/** 유료 기능이 잠겨 있을 때 보여주는 안내 (지금은 모든 기능이 열려 있어 쓰이지 않습니다). */
@Composable
fun PremiumLockedBox(featureName: String, modifier: Modifier = Modifier) {
    MessageBox(
        stringResource(R.string.premium_locked_title, featureName),
        modifier,
        body = stringResource(R.string.premium_locked_body),
        emoji = "🔒",
    )
}

/** 유료 기능 표시용 작은 꼬리표 */
@Composable
fun PremiumPill(modifier: Modifier = Modifier) {
    InfoPill(
        stringResource(R.string.premium_pill),
        modifier,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}
