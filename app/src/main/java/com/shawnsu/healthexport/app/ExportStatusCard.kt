package com.shawnsu.healthexport.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shawnsu.healthexport.R
import com.shawnsu.healthexport.export.ExportProtocol
import com.shawnsu.healthexport.export.ExportRange
import com.shawnsu.healthexport.export.ExportReport
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class ExportUiState(
    val report: ExportReport? = null,
    val request: String = "",
    val range: String = ExportRange.ALL,
    val pending: Boolean = false,
    val uncertain: Boolean = false,
    val writable: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val requestUncertain: Boolean = false,
) {
    val exporting: Boolean get() = report?.phase == "running" && !uncertain
    val canRequest: Boolean get() = writable && !pending && !saving && !exporting && !requestUncertain
}

/** 数据集 id → 文案资源；未知 id 回退为通用文案。 */
internal fun exportDatasetNameRes(id: String): Int = when (id) {
    "daily" -> R.string.export_dataset_daily
    "sleep" -> R.string.export_dataset_sleep
    "heart_rate" -> R.string.export_dataset_heart_rate
    "weight" -> R.string.export_dataset_weight
    "blood_pressure" -> R.string.export_dataset_blood_pressure
    "workout" -> R.string.export_dataset_workout
    "blood_oxygen" -> R.string.export_dataset_blood_oxygen
    "pressure" -> R.string.export_dataset_pressure
    "blood_sugar" -> R.string.export_dataset_blood_sugar
    "temperature" -> R.string.export_dataset_temperature
    else -> R.string.export_dataset_unknown
}

@Composable
internal fun ExportStatusCard(state: ExportUiState, onExport: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        BasicComponent(
            modifier = Modifier.testTag("export:card").semantics(mergeDescendants = true) {
                role = Role.Button
                if (!state.canRequest) disabled()
            },
            enabled = state.canRequest,
            onClickLabel = stringResource(R.string.export_start),
            role = Role.Button,
            onClick = onExport,
        ) {
            val titleColors = BasicComponentDefaults.titleColor()
            Text(
                stringResource(R.string.export_title),
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = if (state.canRequest) titleColors.color else titleColors.disabledColor,
            )
            val report = state.report
            val phaseText = when {
                report?.phase == "complete" && !state.uncertain -> stringResource(
                    if (report.failed.isEmpty()) R.string.export_complete_all else R.string.export_complete_partial,
                    report.total, report.failed.size,
                )
                state.uncertain -> stringResource(R.string.export_uncertain)
                state.exporting -> stringResource(R.string.export_progress, report?.done?.size ?: 0, ExportProtocol.datasets.size)
                report == null -> stringResource(R.string.export_not_started)
                report.phase == "failed" -> stringResource(R.string.export_failed)
                else -> stringResource(R.string.export_complete)
            }
            ExportSummary(phaseText, state.canRequest, Modifier.testTag("export:phase"))
            ExportSummary(
                stringResource(R.string.export_range_current, ExportRange.label(state.range, System.currentTimeMillis())),
                state.canRequest, Modifier.testTag("export:range"),
            )
            val output = report?.output.orEmpty()
            if (output.isNotEmpty()) ExportSummary(
                stringResource(R.string.export_output, output), state.canRequest, Modifier.testTag("export:output"),
            )
            if (state.pending) ExportSummary(
                stringResource(R.string.export_scheduled), state.canRequest, Modifier.testTag("export:pending"),
            )
            if (state.saveFailed) ExportSummary(stringResource(R.string.export_save_failed), state.canRequest)
            if (state.requestUncertain) ExportSummary(
                stringResource(R.string.export_request_uncertain), state.canRequest,
                Modifier.testTag("export:request-uncertain"),
            )
            if (state.saving) ExportSummary(stringResource(R.string.export_saving), state.canRequest)
        }
    }
}

@Composable
private fun ExportSummary(text: String, enabled: Boolean, modifier: Modifier = Modifier) {
    val colors = BasicComponentDefaults.summaryColor()
    Text(
        text = text,
        modifier = modifier,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = if (enabled) colors.color else colors.disabledColor,
    )
}
