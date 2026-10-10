package com.shawnsu.healthexport.app

import android.content.Context
import android.content.SharedPreferences

/** 分享图的留白与水印条底色；图表本体始终是暗色数据面板，不随此项变化。 */
internal enum class PosterTheme(val key: String) {
    DARK("dark"),
    LIGHT("light"),
    ;

    companion object {
        fun from(value: String?): PosterTheme = entries.firstOrNull { it.key == value } ?: DARK
    }
}

/** 分享图样式：配色、圆角、右下角仓库二维码。选项持久化在应用私有偏好里。 */
internal data class PosterStyle(
    val theme: PosterTheme = PosterTheme.DARK,
    val rounded: Boolean = true,
    val branded: Boolean = true,
) {
    companion object {
        private const val PREFERENCES_NAME = "poster_style"
        private const val KEY_THEME = "theme"
        private const val KEY_ROUNDED = "rounded"
        private const val KEY_BRANDED = "branded"

        internal fun read(preferences: SharedPreferences): PosterStyle = PosterStyle(
            theme = PosterTheme.from(preferences.getString(KEY_THEME, null)),
            rounded = preferences.getBoolean(KEY_ROUNDED, true),
            branded = preferences.getBoolean(KEY_BRANDED, true),
        )

        internal fun write(preferences: SharedPreferences, style: PosterStyle) {
            preferences.edit()
                .putString(KEY_THEME, style.theme.key)
                .putBoolean(KEY_ROUNDED, style.rounded)
                .putBoolean(KEY_BRANDED, style.branded)
                .apply()
        }

        fun of(context: Context): PosterStyle = read(preferences(context))

        fun store(context: Context, style: PosterStyle) = write(preferences(context), style)

        private fun preferences(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
}
