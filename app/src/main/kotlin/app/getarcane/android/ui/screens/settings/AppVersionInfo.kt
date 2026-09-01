package app.getarcane.android.ui.screens.settings

import app.getarcane.android.BuildConfig

internal data class AppVersionInfo(
    val name: String,
    val code: Long,
)

internal fun installedAppVersion(): AppVersionInfo = AppVersionInfo(
    name = BuildConfig.VERSION_NAME,
    code = BuildConfig.VERSION_CODE.toLong(),
)
