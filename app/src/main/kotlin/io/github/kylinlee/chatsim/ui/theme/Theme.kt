package io.github.kylinlee.chatsim.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = FallbackPrimary,
    onPrimary = FallbackOnPrimary,
    primaryContainer = FallbackPrimaryContainer,
    onPrimaryContainer = FallbackOnPrimaryContainer,
    secondary = FallbackSecondary,
    onSecondary = FallbackOnSecondary,
    secondaryContainer = FallbackSecondaryContainer,
    onSecondaryContainer = FallbackOnSecondaryContainer,
    tertiary = FallbackTertiary,
    onTertiary = FallbackOnTertiary,
    tertiaryContainer = FallbackTertiaryContainer,
    onTertiaryContainer = FallbackOnTertiaryContainer,
    error = FallbackError,
    onError = FallbackOnError,
    errorContainer = FallbackErrorContainer,
    onErrorContainer = FallbackOnErrorContainer,
    background = FallbackBackground,
    onBackground = FallbackOnBackground,
    surface = FallbackSurface,
    onSurface = FallbackOnSurface,
    surfaceVariant = FallbackSurfaceVariant,
    onSurfaceVariant = FallbackOnSurfaceVariant,
    outline = FallbackOutline,
)

private val DarkColorScheme = darkColorScheme(
    primary = FallbackPrimaryDark,
    onPrimary = FallbackOnPrimaryDark,
    primaryContainer = FallbackPrimaryContainerDark,
    onPrimaryContainer = FallbackOnPrimaryContainerDark,
    secondary = FallbackSecondaryDark,
    onSecondary = FallbackOnSecondaryDark,
    secondaryContainer = FallbackSecondaryContainerDark,
    onSecondaryContainer = FallbackOnSecondaryContainerDark,
    tertiary = FallbackTertiaryDark,
    onTertiary = FallbackOnTertiaryDark,
    tertiaryContainer = FallbackTertiaryContainerDark,
    onTertiaryContainer = FallbackOnTertiaryContainerDark,
    error = FallbackErrorDark,
    onError = FallbackOnErrorDark,
    errorContainer = FallbackErrorContainerDark,
    onErrorContainer = FallbackOnErrorContainerDark,
    background = FallbackBackgroundDark,
    onBackground = FallbackOnBackgroundDark,
    surface = FallbackSurfaceDark,
    onSurface = FallbackOnSurfaceDark,
    surfaceVariant = FallbackSurfaceVariantDark,
    onSurfaceVariant = FallbackOnSurfaceVariantDark,
    outline = FallbackOutlineDark,
)

@Composable
fun SmsMessengerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
