package com.bar.sga.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bar.sga.R
import com.bar.sga.data.BodyGroup

@get:StringRes
val BodyGroup.labelRes: Int get() = when (this) {
    BodyGroup.CHEST -> R.string.body_chest
    BodyGroup.BACK -> R.string.body_back
    BodyGroup.SHOULDERS -> R.string.body_shoulders
    BodyGroup.REAR_DELTS -> R.string.body_rear_delts
    BodyGroup.BICEPS -> R.string.body_biceps
    BodyGroup.TRICEPS -> R.string.body_triceps
    BodyGroup.LEGS -> R.string.body_legs
    BodyGroup.CORE -> R.string.body_core
    BodyGroup.OTHER -> R.string.other
}

@get:DrawableRes
val BodyGroup.iconRes: Int get() = when (this) {
    BodyGroup.CHEST -> R.drawable.ic_body_chest
    BodyGroup.BACK -> R.drawable.ic_body_back
    BodyGroup.SHOULDERS -> R.drawable.ic_body_shoulders
    BodyGroup.REAR_DELTS -> R.drawable.ic_body_rear_delts
    BodyGroup.BICEPS -> R.drawable.ic_body_biceps
    BodyGroup.TRICEPS -> R.drawable.ic_body_triceps
    BodyGroup.LEGS -> R.drawable.ic_body_legs
    BodyGroup.CORE -> R.drawable.ic_body_core
    BodyGroup.OTHER -> R.drawable.ic_body_other
}

/** Not mirrored in right-to-left: the drawables are not auto-mirrored. Pass a null description where the name is shown beside it. */
@Composable
fun BodyGroupIcon(
    group: BodyGroup,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = stringResource(group.labelRes),
) {
    Icon(painterResource(group.iconRes), contentDescription, modifier.size(size), tint)
}

@Composable
fun BodyGroupIcons(groups: List<BodyGroup>, size: Dp, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        groups.forEach { BodyGroupIcon(it, size = size) }
    }
}
