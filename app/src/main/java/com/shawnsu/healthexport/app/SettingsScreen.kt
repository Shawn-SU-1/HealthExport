package com.shawnsu.healthexport.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawnsu.healthexport.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
private fun MiuixServiceStatusCard(
    state: SettingsUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = state.isServiceConnected
    val darkTheme = LocalAppDarkTheme.current
    val dynamicColor = MiuixTheme.isDynamicColor
    val container = when {
        connected && dynamicColor -> MiuixTheme.colorScheme.secondaryContainer
        connected && darkTheme -> Color(0xFF1A3825)
        connected -> Color(0xFFDFFAE4)
        dynamicColor -> MiuixTheme.colorScheme.errorContainer
        darkTheme -> Color(0xFF3A1E22)
        else -> Color(0xFFFFE4E1)
    }
    val iconTint = when {
        connected && dynamicColor -> MiuixTheme.colorScheme.primary
        connected -> Color(0xFF36D167)
        dynamicColor -> MiuixTheme.colorScheme.error
        darkTheme -> Color(0xFFFF8A80)
        else -> Color(0xFFD32F2F)
    }
    val contentColor = if (dynamicColor) {
        if (connected) {
            MiuixTheme.colorScheme.onSecondaryContainer
        } else {
            MiuixTheme.colorScheme.onErrorContainer
        }
    } else {
        MiuixTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings:service-status"),
            colors = CardDefaults.defaultColors(
                color = container,
                contentColor = contentColor,
            ),
            onClick = onRefresh,
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Tilt,
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(27.dp, 31.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Icon(
                        modifier = Modifier.size(110.dp),
                        imageVector = if (connected) {
                            Icons.Rounded.CheckCircleOutline
                        } else {
                            Icons.Rounded.ErrorOutline
                        },
                        tint = iconTint,
                        contentDescription = null,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp, 14.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(
                                if (connected) R.string.settings_status_connected
                                else R.string.settings_status_disconnected,
                            ),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                        )
                        Text(
                            text = stringResource(
                                if (connected) {
                                    R.string.settings_status_connected_summary
                                } else {
                                    R.string.settings_status_disconnected_summary
                                },
                            ),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = contentColor.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

private fun scopeSummary(state: ScopeUiState): Int = when {
    !state.isServiceConnected -> R.string.scope_settings_disconnected
    state.isLoading -> R.string.scope_settings_loading
    state.isChanging -> R.string.scope_settings_updating
    state.hasError -> R.string.scope_settings_error
    state.isGranted -> R.string.scope_settings_granted
    else -> R.string.scope_settings_not_granted
}

@Composable
internal fun NavigationCard(
    title: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        ArrowPreference(
            title = title,
            onClick = onClick,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    scopeState: ScopeUiState = ScopeUiState(),
    onScopeChange: (Boolean) -> Unit = {},
    onOpenExport: () -> Unit = {},
    onOpenAnalysis: () -> Unit = {},
    onRefreshStatus: () -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val horizontalSafeInsets = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Horizontal)
        .asPaddingValues()
    val lazyListState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                color = MiuixTheme.colorScheme.surface,
                scrollBehavior = scrollBehavior,
                title = stringResource(R.string.settings_title),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings:list")
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = lazyListState,
            contentPadding = PaddingValues(
                start = horizontalSafeInsets.calculateLeftPadding(layoutDirection),
                top = paddingValues.calculateTopPadding() + 8.dp,
                end = horizontalSafeInsets.calculateRightPadding(layoutDirection),
                bottom = paddingValues.calculateBottomPadding(),
            ),
            overscrollEffect = null,
        ) {
            item(key = "settings_status") {
                MiuixServiceStatusCard(
                    state = state,
                    onRefresh = onRefreshStatus,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                )
            }
            item(key = "settings_scope") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp)
                        .testTag("settings:scope-card"),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.scope_settings_title),
                        summary = stringResource(scopeSummary(scopeState)),
                        checked = scopeState.isGranted,
                        enabled = scopeState.writable,
                        onCheckedChange = onScopeChange,
                        modifier = Modifier.testTag("setting:scope"),
                    )
                }
            }
            item(key = "export_navigation") {
                NavigationCard(
                    title = stringResource(R.string.export_title),
                    testTag = "settings:export-nav",
                    onClick = onOpenExport,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                )
            }
            item(key = "analysis_navigation") {
                NavigationCard(
                    title = stringResource(R.string.analysis_title),
                    testTag = "settings:analysis-nav",
                    onClick = onOpenAnalysis,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                )
            }
            item(key = "settings_description") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp)
                        .testTag("settings:description"),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Text(
                        stringResource(R.string.export_description),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
            item(key = "navigation_bar_spacer") {
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}
