package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CreatorPaisaDarkColorScheme = darkColorScheme(
    primary = SaffronPrimary,
    onPrimary = Color.White,
    primaryContainer = SaffronContainer,
    onPrimaryContainer = OnSaffronContainer,
    secondary = PaisaGoldSecondary,
    onSecondary = Color(0xFF1F1600),
    secondaryContainer = PaisaGoldContainer,
    onSecondaryContainer = OnPaisaGoldContainer,
    tertiary = CreatorMintTertiary,
    onTertiary = Color(0xFF00241A),
    tertiaryContainer = CreatorMintContainer,
    onTertiaryContainer = OnCreatorMintContainer,
    background = ObsidianBackground,
    onBackground = IvoryText,
    surface = ObsidianSurface,
    onSurface = IvoryText,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = SlateTextMuted,
    outline = ObsidianOutline,
    error = StudioError,
    onError = Color.White,
    errorContainer = StudioErrorContainer,
    onErrorContainer = Color(0xFFFFD9DF)
)

private val CreatorPaisaLightColorScheme = lightColorScheme(
    primary = SaffronPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = SaffronContainerLight,
    onPrimaryContainer = OnSaffronContainerLight,
    secondary = PaisaGoldLight,
    onSecondary = Color.White,
    secondaryContainer = PaisaGoldContainerLight,
    onSecondaryContainer = OnPaisaGoldContainerLight,
    tertiary = CreatorMintLight,
    onTertiary = Color.White,
    tertiaryContainer = CreatorMintContainerLight,
    onTertiaryContainer = OnCreatorMintContainerLight,
    background = WarmIvoryBackground,
    onBackground = CharcoalText,
    surface = PureWhiteSurface,
    onSurface = CharcoalText,
    surfaceVariant = SoftStoneVariant,
    onSurfaceVariant = MutedInk,
    outline = SoftStoneOutline,
    error = Color(0xFFD62848),
    onError = Color.White,
    errorContainer = Color(0xFFFFE5EA),
    onErrorContainer = Color(0xFF5A0B1C)
)

val CreatorPaisaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CreatorPaisaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CreatorPaisaDarkColorScheme else CreatorPaisaLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = CreatorPaisaTypography,
        shapes = CreatorPaisaShapes,
        content = content
    )
}
