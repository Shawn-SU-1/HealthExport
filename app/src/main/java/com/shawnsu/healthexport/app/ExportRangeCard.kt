package com.shawnsu.healthexport.app

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shawnsu.healthexport.R
import com.shawnsu.healthexport.export.ExportRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 预设时间范围：编码与文案一一对应，顺序即界面顺序。 */
private val RANGE_PRESETS = listOf(
    ExportRange.ALL to R.string.export_range_all,
    "7" to R.string.export_range_days_7,
    "30" to R.string.export_range_days_30,
    "90" to R.string.export_range_days_90,
    "365" to R.string.export_range_days_365,
)

private const val CUSTOM_PREFIX = "custom:"
private const val CUSTOM_DEFAULT_DAYS = 30L
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * 导出时间范围选择：预设（全部历史 / 最近 N 天）与自定义起止日期。
 * 选择只影响下一次导出，不改变已经生效的预约内容。
 */
@Composable
internal fun ExportRangeCard(
    range: String,
    onRangeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val customSelected = range.startsWith(CUSTOM_PREFIX)
    val custom = customBounds(range)
    Card(modifier = modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.export_range_title),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
            )
            for ((code, labelRes) in RANGE_PRESETS) {
                RadioButtonPreference(
                    title = stringResource(labelRes),
                    selected = range == code,
                    onClick = { onRangeChange(code) },
                    modifier = Modifier.testTag("export:range-$code"),
                )
            }
            RadioButtonPreference(
                title = stringResource(R.string.export_range_custom),
                selected = customSelected,
                onClick = { onRangeChange(ExportRange.encodeCustom(custom.first, custom.second)) },
                modifier = Modifier.testTag("export:range-custom"),
            )
            if (customSelected) {
                DatePreference(
                    titleRes = R.string.export_range_custom_start,
                    testTag = "export:range-start",
                    date = toDate(custom.first),
                    onPick = { picked ->
                        val start = fromDate(picked)
                        // 起点不得越过终点：终点早于起点时顺延一天，保证范围始终有效。
                        val end = if (start < custom.second) custom.second else start + ExportRange.DAY_MS
                        onRangeChange(ExportRange.encodeCustom(start, end))
                    },
                )
                DatePreference(
                    titleRes = R.string.export_range_custom_end,
                    testTag = "export:range-end",
                    date = toDate(custom.second),
                    onPick = { picked ->
                        // 结束日期按整天含入，因此取下一天零点。
                        val end = fromDate(picked) + ExportRange.DAY_MS
                        val start = if (custom.first < end) custom.first else end - ExportRange.DAY_MS
                        onRangeChange(ExportRange.encodeCustom(start, end))
                    },
                )
            }
        }
    }
}

@Composable
private fun DatePreference(
    titleRes: Int,
    testTag: String,
    date: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    ArrowPreference(
        title = stringResource(titleRes),
        summary = DATE_FORMAT.format(date),
        onClick = { showDatePicker(context, date, onPick) },
        modifier = Modifier.testTag(testTag),
    )
}

/** 自定义范围的起止毫秒；非自定义或解析失败时回退为最近 30 天。 */
private fun customBounds(range: String): Pair<Long, Long> {
    val now = System.currentTimeMillis()
    if (range.startsWith(CUSTOM_PREFIX)) {
        val bounds = ExportRange.resolve(range, now)
        if (bounds.first >= 1L && bounds.first < bounds.second) return bounds
    }
    return (now - CUSTOM_DEFAULT_DAYS * ExportRange.DAY_MS) to now
}

private fun toDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

private fun fromDate(date: LocalDate): Long = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun showDatePicker(context: Context, initial: LocalDate, onPick: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onPick(LocalDate.of(year, month + 1, day)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}
