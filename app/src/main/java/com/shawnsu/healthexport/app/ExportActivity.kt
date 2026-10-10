package com.shawnsu.healthexport.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.libxposed.service.XposedService
import com.shawnsu.healthexport.R
import com.shawnsu.healthexport.export.ExportLogAnalyzer
import com.shawnsu.healthexport.export.ExportProtocol
import com.shawnsu.healthexport.export.ExportReport
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 汇总导出入口、进度与结果明细。 */
class ExportActivity : AppCompatActivity(), ModuleApplication.ServiceStateListener {
    private var exportState by mutableStateOf(ExportUiState())
    private val exportController by lazy { ExportStatusController(this) { exportState = it } }

    override fun onStart() {
        super.onStart()
        (application as ModuleApplication).addServiceStateListener(this)
        exportController.start()
    }

    override fun onStop() {
        exportController.stop()
        (application as ModuleApplication).removeServiceStateListener(this)
        super.onStop()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        exportController.bind(service)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val moduleApplication = application as ModuleApplication
        setContent {
            HealthExportTheme(
                colorMode = moduleApplication.colorMode,
                window = window,
            ) {
                ExportScreen(
                    exportState = exportState,
                    onExport = { exportController.request() },
                    onClose = ::finish,
                )
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, ExportActivity::class.java)
    }
}

@Composable
internal fun ExportScreen(
    exportState: ExportUiState,
    onExport: () -> Unit,
    onClose: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val safeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                color = MiuixTheme.colorScheme.surface,
                scrollBehavior = scrollBehavior,
                title = stringResource(R.string.export_title),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings:export")
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                start = safeInsets.calculateLeftPadding(layoutDirection),
                top = padding.calculateTopPadding() + 8.dp,
                end = safeInsets.calculateRightPadding(layoutDirection),
                bottom = padding.calculateBottomPadding(),
            ),
            overscrollEffect = null,
        ) {
            item(key = "export_status") {
                ExportStatusCard(
                    exportState, onExport,
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            item(key = "export_description") {
                ExportDescriptionCard()
            }
            val report = exportState.report
            if (report != null) {
                item(key = "export_report") {
                    ExportReportCard(report)
                }
            }
            item(key = "export_log") {
                ExportLogCard(
                    exportState,
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            item(key = "navigation_bar_spacer") {
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun ExportDescriptionCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        insideMargin = PaddingValues(0.dp),
    ) {
        val colors = BasicComponentDefaults.summaryColor()
        Text(
            stringResource(R.string.export_description),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = colors.color,
        )
    }
}

@Composable
private fun ExportReportCard(report: ExportReport) {
    val context = LocalContext.current
    val titleColors = BasicComponentDefaults.titleColor()
    val summaryColors = BasicComponentDefaults.summaryColor()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("export:report"),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.export_datasets_title),
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = titleColors.color,
            )
            for (dataset in ExportProtocol.datasets) {
                val name = context.getString(exportDatasetNameRes(dataset.id))
                val count = report.counts[dataset.id]
                val text = when {
                    count != null -> context.getString(R.string.export_dataset_count, name, count)
                    dataset.id in report.failed -> context.getString(R.string.export_dataset_failed, name)
                    else -> context.getString(R.string.export_dataset_waiting, name)
                }
                Text(
                    text,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = summaryColors.color,
                )
            }
            val output = report.output
            if (output.isNotEmpty()) {
                Text(
                    stringResource(R.string.export_output, output),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = summaryColors.color,
                )
            }
        }
    }
}

@Composable
private fun ExportLogCard(state: ExportUiState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val titleColors = BasicComponentDefaults.titleColor()
    val summaryColors = BasicComponentDefaults.summaryColor()
    val report = state.report
    val hints = ExportLogAnalyzer.hints(report, state.request, state.uncertain)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("export:log"),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.export_log_title),
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = titleColors.color,
            )
            Text(
                if (state.request.isEmpty()) stringResource(R.string.export_log_request_none)
                else stringResource(R.string.export_log_request, state.request.take(8)),
                modifier = Modifier.testTag("export:log-request"),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = summaryColors.color,
            )
            Text(
                if (report == null) stringResource(R.string.export_log_report_none)
                else stringResource(
                    R.string.export_log_report,
                    "${report.run.take(8)} · ${EXPORT_LOG_STAMP.format(Instant.ofEpochMilli(report.time).atZone(ZoneId.systemDefault()))}",
                ),
                modifier = Modifier.testTag("export:log-report"),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = summaryColors.color,
            )
            for (hint in hints) {
                Text(
                    hintText(context, hint),
                    modifier = Modifier.testTag("export:log-hint"),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = summaryColors.color,
                )
            }
            Text(
                stringResource(R.string.export_log_detail),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                fontWeight = FontWeight.Medium,
                color = titleColors.color,
            )
            SelectionContainer {
                Text(
                    report?.trace?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.export_log_empty),
                    modifier = Modifier.testTag("export:log-trace"),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = summaryColors.color,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun hintText(context: Context, hint: ExportLogAnalyzer.Hint): String = when (hint) {
    is ExportLogAnalyzer.Hint.NoRecord -> stringResource(R.string.export_log_hint_none)
    is ExportLogAnalyzer.Hint.WaitingFirst -> stringResource(R.string.export_log_hint_waiting, hint.request)
    is ExportLogAnalyzer.Hint.WaitingNext -> stringResource(R.string.export_log_hint_waiting_previous, hint.request, hint.previous)
    is ExportLogAnalyzer.Hint.Running -> stringResource(R.string.export_log_hint_running, hint.done, hint.total)
    is ExportLogAnalyzer.Hint.Stale -> stringResource(R.string.export_log_hint_stale)
    is ExportLogAnalyzer.Hint.Complete -> stringResource(R.string.export_log_hint_complete, hint.total)
    is ExportLogAnalyzer.Hint.CompletePartial -> {
        val separator = stringResource(R.string.export_log_name_separator)
        val names = hint.failed.sorted().joinToString(separator) { context.getString(exportDatasetNameRes(it)) }
        stringResource(R.string.export_log_hint_complete_partial, hint.total, names)
    }
    is ExportLogAnalyzer.Hint.Failed -> when (hint.error) {
        "write_error" -> stringResource(R.string.export_log_hint_failed_write)
        else -> stringResource(R.string.export_log_hint_failed_read)
    }
    is ExportLogAnalyzer.Hint.PreviousLog -> stringResource(R.string.export_log_hint_previous, hint.previous)
}

private val EXPORT_LOG_STAMP = DateTimeFormatter.ofPattern("MM-dd HH:mm")
