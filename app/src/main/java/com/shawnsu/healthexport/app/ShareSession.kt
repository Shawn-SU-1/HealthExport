package com.shawnsu.healthexport.app

import android.graphics.Bitmap
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer

/** 一张截图：图表标识（同时是文件名片段）、卡片标题（作水印条副标题）与位图。 */
internal data class ChartShot(val slug: String, val title: String, val bitmap: Bitmap)

/**
 * 各图表卡片登记自己绘图图层的地方，供「分享全部图表」与图文报告取图。
 *
 * 图层由卡片在组合期登记、离开组合时注销；分析页整体一次性组合（非惰性列表），
 * 因此未滚动到的卡片也已经绘制过，取图不依赖用户当前滚动位置。
 */
internal class ChartShots {
    private class Entry(val title: String, val layer: GraphicsLayer)

    private val entries = LinkedHashMap<String, Entry>()

    fun register(slug: String, title: String, layer: GraphicsLayer) {
        entries[slug] = Entry(title, layer)
    }

    fun unregister(slug: String) {
        entries.remove(slug)
    }

    fun isEmpty(): Boolean = entries.isEmpty()

    /** 按登记顺序（即界面顺序）截图，尚未绘制或截图失败的项直接跳过。 */
    suspend fun captureAll(): List<ChartShot> = entries.entries.mapNotNull { (slug, entry) ->
        val bitmap = runCatching { entry.layer.toImageBitmap().asAndroidBitmap() }.getOrNull() ?: return@mapNotNull null
        ChartShot(slug, entry.title, bitmap)
    }

    suspend fun capture(slug: String): ChartShot? {
        val entry = entries[slug] ?: return null
        val bitmap = runCatching { entry.layer.toImageBitmap().asAndroidBitmap() }.getOrNull() ?: return null
        return ChartShot(slug, entry.title, bitmap)
    }
}

/** 录到图层再按原样绘制：卡片把自己画进 [layer]，屏幕显示仍走正常路径。 */
internal fun Modifier.captureLayer(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

/** 当前分析页的图层登记表；为 null 表示该卡片不参与导出与分享。 */
internal val LocalChartShots = staticCompositionLocalOf<ChartShots?> { null }

/** 当前分享图样式。 */
internal val LocalPosterStyle = compositionLocalOf { PosterStyle() }

/** 仓库二维码；未启用品牌角标时为 null。 */
internal val LocalPosterMark = staticCompositionLocalOf<Bitmap?> { null }
