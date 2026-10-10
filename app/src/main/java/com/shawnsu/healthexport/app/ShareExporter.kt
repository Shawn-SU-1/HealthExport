package com.shawnsu.healthexport.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt

/** 一次导出/分享的结果：[savedPath] 为公共下载目录中的相对路径；为 null 表示仅完成了系统分享。 */
internal data class ExportResult(val savedPath: String?)

/**
 * 把分析结果导出为 Markdown 文件或图片，并通过系统分享面板发出。
 *
 * 落盘策略沿用导出侧：先写进 `cacheDir/shared/`（分享用的稳定来源），再尽力复制到公共
 * `Download/<目录>`；公共目录不可用（如 Android 9 及以下）时只保留分享，不影响功能。
 */
internal object ShareExporter {
    const val MIME_MARKDOWN = "text/markdown"
    const val MIME_PNG = "image/png"

    /** 与导出侧使用同一目录，保证两种产物在同一个文件夹里。 */
    private const val DOWNLOAD_DIR = "HuaweiHealthExport"
    private const val SHARED_DIR = "shared"

    fun shareText(context: Context, text: String, fileName: String, chooserTitle: String): ExportResult? =
        export(context, fileName, MIME_MARKDOWN, chooserTitle, text.toByteArray(Charsets.UTF_8))

    fun shareImage(context: Context, bitmap: Bitmap, fileName: String, chooserTitle: String): ExportResult? {
        val bytes = runCatching {
            ByteArrayOutputStream().use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw IllegalStateException("compress")
                stream.toByteArray()
            }
        }.getOrNull() ?: return null
        return export(context, fileName, MIME_PNG, chooserTitle, bytes)
    }

    /** 返回 null 表示导出与分享均未完成；否则至少已发起分享。 */
    private fun export(
        context: Context,
        fileName: String,
        mime: String,
        chooserTitle: String,
        bytes: ByteArray,
    ): ExportResult? {
        val file = cacheFile(context, fileName) ?: return null
        if (runCatching { file.writeBytes(bytes) }.isFailure) return null
        val saved = publish(context, fileName, mime, bytes)
        val shared = share(context, file, mime, chooserTitle)
        if (!shared) return null
        return ExportResult(saved)
    }

    private fun cacheFile(context: Context, fileName: String): File? {
        val dir = File(context.cacheDir, SHARED_DIR)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        return File(dir, fileName)
    }

    /**
     * 复制到公共下载目录：Android 10 起走 MediaStore（无需任何存储权限），失败或低版本回退到
     * 应用外部目录。返回可展示的相对路径或绝对路径。
     */
    private fun publish(context: Context, fileName: String, mime: String, bytes: ByteArray): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/$DOWNLOAD_DIR")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = runCatching { resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) }.getOrNull()
            if (uri != null) {
                val written = runCatching {
                    val stream = resolver.openOutputStream(uri) ?: throw IllegalStateException("no stream")
                    stream.use { it.write(bytes) }
                }.isSuccess
                if (written) {
                    runCatching {
                        resolver.update(
                            uri,
                            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                            null,
                            null,
                        )
                    }
                    return "Download/$DOWNLOAD_DIR/$fileName"
                }
                runCatching { resolver.delete(uri, null, null) }
            }
        }
        val root = context.getExternalFilesDir(null) ?: return null
        val dir = File(root, SHARED_DIR)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val file = File(dir, fileName)
        return runCatching {
            file.writeBytes(bytes)
            file.absolutePath
        }.getOrNull()
    }

    private fun share(context: Context, file: File, mime: String, chooserTitle: String): Boolean = runCatching {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }.isSuccess
}

/** 输出图四周的留白：按底图宽度的 5% 取整，最小 12px。 */
internal fun posterMargin(sourceWidth: Int): Int = max(12, (sourceWidth * 0.05f).roundToInt())

/** 底部水印条高度：随输出宽度等比缩放，最小 64px，保证小图上文字仍可读。 */
internal fun watermarkStripHeight(posterWidth: Int): Int = max(64, (posterWidth * 0.155f).roundToInt())

/**
 * 把图表截图合成为分享用的海报：暗色底 + 等距留白 + 底部水印条。
 * 留白与暗色底同时解决了圆角处透明、深浅主题下截图底色不一致两个问题；
 * 水印条内含强调色圆点、App 名称、图表名称与生成时间。
 */
internal fun composePoster(
    source: Bitmap,
    accent: Int,
    title: String,
    subtitle: String,
    stamp: String,
): Bitmap {
    val margin = posterMargin(source.width)
    val width = source.width + margin * 2
    val strip = watermarkStripHeight(width)
    val stripTop = source.height + margin * 2
    val output = Bitmap.createBitmap(width, stripTop + strip, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val backdrop = ChartPalette.PanelBottom.toArgb()
    canvas.drawColor(backdrop)
    canvas.drawBitmap(source, margin.toFloat(), margin.toFloat(), null)

    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    accentPaint.alpha = 56
    accentPaint.strokeWidth = max(1f, width * 0.0015f)
    canvas.drawLine(
        margin.toFloat(),
        stripTop.toFloat(),
        (width - margin).toFloat(),
        stripTop.toFloat(),
        accentPaint,
    )

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = ChartPalette.Text.toArgb()
        textSize = strip * 0.30f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = ChartPalette.TextMuted.toArgb()
        textSize = strip * 0.20f
        typeface = Typeface.DEFAULT
    }
    val titleMetrics = titlePaint.fontMetrics
    val subMetrics = subPaint.fontMetrics
    val gap = strip * 0.06f
    val blockHeight = (titleMetrics.descent - titleMetrics.ascent) + gap + (subMetrics.descent - subMetrics.ascent)
    val centerY = stripTop + strip / 2f
    val titleBaseline = centerY - blockHeight / 2f - titleMetrics.ascent
    val subBaseline = titleBaseline + titleMetrics.descent + gap - subMetrics.ascent

    val dotRadius = strip * 0.13f
    val dotCenterX = margin + dotRadius * 1.2f
    accentPaint.alpha = 255
    canvas.drawCircle(dotCenterX, centerY, dotRadius, accentPaint)

    val textLeft = dotCenterX + dotRadius * 2f
    canvas.drawText(title, textLeft, titleBaseline, titlePaint)
    canvas.drawText(subtitle, textLeft, subBaseline, subPaint)

    subPaint.textAlign = Paint.Align.RIGHT
    canvas.drawText(
        stamp,
        (width - margin).toFloat(),
        centerY - (subMetrics.ascent + subMetrics.descent) / 2f,
        subPaint,
    )
    return output
}

/** 文件名里的时间戳片段：去掉冒号与空格，避免部分文件系统与分享目标拒绝该名称。 */
internal fun fileStamp(millis: Long): String =
    STAMP_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
        .replace(':', '-')
        .replace(' ', '-')
