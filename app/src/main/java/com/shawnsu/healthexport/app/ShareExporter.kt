package com.shawnsu.healthexport.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** 一次导出/分享的结果：[savedPath] 为公共下载目录中的相对路径；为 null 表示仅完成了系统分享。 */
internal data class ExportResult(val savedPath: String?)

/**
 * 把分析结果导出为 Markdown / 图片 / 压缩包，并通过系统分享面板发出。
 *
 * 落盘策略沿用导出侧：先写进 `cacheDir/shared/`（分享用的稳定来源），再尽力复制到公共
 * `Download/<目录>`；公共目录不可用（如 Android 9 及以下）时只保留分享，不影响功能。
 * 多图分享与压缩包只做分享，不写入公共目录，避免一次操作在下载目录留下大量散件。
 */
internal object ShareExporter {
    const val MIME_MARKDOWN = "text/markdown"
    const val MIME_PNG = "image/png"
    const val MIME_ZIP = "application/zip"

    /** 与导出侧使用同一目录，保证两种产物在同一个文件夹里。 */
    private const val DOWNLOAD_DIR = "HuaweiHealthExport"
    private const val SHARED_DIR = "shared"

    fun shareText(context: Context, text: String, fileName: String, chooserTitle: String): ExportResult? =
        deliver(context, listOf(fileName to text.toByteArray(Charsets.UTF_8)), MIME_MARKDOWN, fileName, chooserTitle)

    /** 单张图片：写入公共下载目录并分享。 */
    fun shareImage(context: Context, bitmap: Bitmap, fileName: String, chooserTitle: String): ExportResult? {
        val bytes = encodePng(bitmap) ?: return null
        return deliver(context, listOf(fileName to bytes), MIME_PNG, fileName, chooserTitle)
    }

    /** 多张图片一次分享（`ACTION_SEND_MULTIPLE`），只分享不落公共目录。 */
    fun shareImages(context: Context, images: List<Pair<String, Bitmap>>, chooserTitle: String): ExportResult? {
        val staged = images.mapNotNull { (name, bitmap) -> encodePng(bitmap)?.let { name to it } }
        if (staged.isEmpty()) return null
        return deliver(context, staged, MIME_PNG, null, chooserTitle)
    }

    /** 把若干条目打成 zip 分享，用于「报告 + 图片」这类需要保持目录结构的产物。 */
    fun shareArchive(
        context: Context,
        entries: List<Pair<String, ByteArray>>,
        fileName: String,
        chooserTitle: String,
    ): ExportResult? {
        val bytes = archive(entries) ?: return null
        return deliver(context, listOf(fileName to bytes), MIME_ZIP, fileName, chooserTitle)
    }

    /** 返回 null 表示导出与分享均未完成；否则至少已发起分享。 */
    private fun deliver(
        context: Context,
        files: List<Pair<String, ByteArray>>,
        mime: String,
        publishName: String?,
        chooserTitle: String,
    ): ExportResult? {
        val dir = sharedDir(context) ?: return null
        val authority = "${context.packageName}.fileprovider"
        val uris = ArrayList<Uri>(files.size)
        for ((name, bytes) in files) {
            val file = File(dir, name)
            if (runCatching { file.writeBytes(bytes) }.isFailure) return null
            val uri = runCatching { FileProvider.getUriForFile(context, authority, file) }.getOrNull() ?: return null
            uris += uri
        }
        // 先落公共目录再分享：即使分享面板被取消，产物也已经留在下载目录里。
        val saved = publishName?.let { publish(context, it, mime, files.first().second) }
        if (!share(context, uris, mime, chooserTitle)) return null
        return ExportResult(saved)
    }

    private fun sharedDir(context: Context): File? {
        val dir = File(context.cacheDir, SHARED_DIR)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        return dir
    }

    /** 位图编码为 PNG 字节，供分享与报告打包复用。 */
    internal fun encodePng(bitmap: Bitmap): ByteArray? = runCatching {
        ByteArrayOutputStream().use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw IllegalStateException("compress")
            stream.toByteArray()
        }
    }.getOrNull()

    private fun archive(entries: List<Pair<String, ByteArray>>): ByteArray? = runCatching {
        ByteArrayOutputStream().use { buffer ->
            ZipOutputStream(buffer).use { zip ->
                for ((name, bytes) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            buffer.toByteArray()
        }
    }.getOrNull()

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

    private fun share(context: Context, uris: List<Uri>, mime: String, chooserTitle: String): Boolean = runCatching {
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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

/** 整张海报的圆角半径占宽度的比例（接近微信分享卡的观感）。 */
private const val ROUND_RADIUS_RATIO = 0.035f

/** 海报留白与水印条的取色：图表本体始终是暗色数据面板，不随主题变化。 */
private data class PosterColors(val backdrop: Int, val title: Int, val muted: Int)

private fun posterColors(theme: PosterTheme): PosterColors = when (theme) {
    PosterTheme.DARK -> PosterColors(
        backdrop = ChartPalette.PanelBottom.toArgb(),
        title = ChartPalette.Text.toArgb(),
        muted = ChartPalette.TextMuted.toArgb(),
    )

    PosterTheme.LIGHT -> PosterColors(
        backdrop = 0xFFF3F5F8.toInt(),
        title = 0xFF16202E.toInt(),
        muted = 0xFF5C6B7F.toInt(),
    )
}

/**
 * Compose 截图给出的是 HARDWARE 位图，而 API 34 之前软件 Canvas 不能绘制它
 * （会抛 IllegalArgumentException），因此合成前先取一份软件位图。
 */
private fun Bitmap.toSoftware(): Bitmap =
    if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) ?: this else this

/**
 * 把图表截图合成为分享用的海报：留白 + 底部水印条，可选圆角与右下角二维码。
 * 留白与不透明底同时解决了圆角处透明、深浅主题下截图底色不一致两个问题。
 */
internal fun composePoster(
    source: Bitmap,
    accent: Int,
    title: String,
    subtitle: String,
    stamp: String,
    theme: PosterTheme = PosterTheme.DARK,
    rounded: Boolean = true,
    mark: Bitmap? = null,
): Bitmap {
    val art = source.toSoftware()
    val margin = posterMargin(art.width)
    val width = art.width + margin * 2
    val strip = watermarkStripHeight(width)
    val stripTop = art.height + margin * 2
    val height = stripTop + strip
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val colors = posterColors(theme)
    if (rounded) {
        val radius = width * ROUND_RADIUS_RATIO
        canvas.save()
        canvas.clipPath(
            Path().apply {
                addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, Path.Direction.CW)
            },
        )
    }
    canvas.drawColor(colors.backdrop)
    canvas.drawBitmap(art, margin.toFloat(), margin.toFloat(), null)

    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        alpha = 56
        strokeWidth = max(1f, width * 0.0015f)
    }
    canvas.drawLine(
        margin.toFloat(),
        stripTop.toFloat(),
        (width - margin).toFloat(),
        stripTop.toFloat(),
        accentPaint,
    )

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = colors.title
        textSize = strip * 0.30f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = colors.muted
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

    // 右侧自右向左依次是二维码（可选）与生成时间，无二维码时时间贴右边距。
    var rightEdge = (width - margin).toFloat()
    if (mark != null) {
        val size = strip * 0.78f
        val left = width - margin - size
        val top = stripTop + (strip - size) / 2f
        val smooth = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(mark, null, RectF(left, top, left + size, top + size), smooth)
        rightEdge = left - strip * 0.12f
    }
    subPaint.textAlign = Paint.Align.RIGHT
    canvas.drawText(
        stamp,
        rightEdge,
        centerY - (subMetrics.ascent + subMetrics.descent) / 2f,
        subPaint,
    )
    if (rounded) canvas.restore()
    return output
}

/** 文件名里的时间戳片段：去掉冒号与空格，避免部分文件系统与分享目标拒绝该名称。 */
internal fun fileStamp(millis: Long): String =
    STAMP_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
        .replace(':', '-')
        .replace(' ', '-')
