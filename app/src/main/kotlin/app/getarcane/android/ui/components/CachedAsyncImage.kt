package app.getarcane.android.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import coil.compose.SubcomposeAsyncImage

/**
 * Loads [url] (container/registry icon from labels) with a [fallback] shown while loading or on
 * error / when null. Coil handles memory+disk caching. Mirrors iOS `CachedAsyncImage`.
 */
@Composable
fun CachedAsyncImage(
    url: String?,
    size: Dp,
    shape: androidx.compose.ui.graphics.Shape,
    fallback: @Composable () -> Unit,
) {
    if (url.isNullOrBlank()) {
        fallback()
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        modifier = Modifier.size(size).clip(shape),
        loading = { fallback() },
        error = { fallback() },
    )
}
