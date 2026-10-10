# HealthExport

将华为运动健康的历史健康数据导出到本机的 LSPosed 模块，并提供应用内的数据分析与报告分享。

## 功能

**数据导出**

- 导出华为运动健康中的历史健康数据：日常活动、睡眠、心率、体重、血氧、压力、运动记录等数据集
- 导出时间范围可选：最近 7 天 / 30 天 / 90 天 / 1 年、自定义起止日期，或全部历史
- 输出为 Markdown 明细报告，按数据集分别列出数据
- 同步输出结构化 JSON 摘要（`.summary.json`），记录各数据集的条数、逐月活跃量、睡眠夜数与时长、心率 / 压力分布、血氧分型、体重体脂序列与运动分类汇总
- 同步输出明细表：`…-detail.xlsx`（多工作表，一个数据集一张表）与 `…-detail-csv.zip`（每个数据集一份 CSV），可直接用于透视表与二次分析
- 预约式导出：在模块中预约后，切回（或重新打开）华为运动健康即自动执行
- 导出日志：可查看当前预约、最近一次报告与导出过程

**数据分析**

- 应用内「数据分析」页面直接读取最近一次导出的 JSON 摘要，本地自绘图表，不引入任何第三方图表库
- 概览、步数 / 距离 / 热量趋势、睡眠时长趋势与睡眠分期（深睡 / 浅睡 / 快速眼动 / 清醒）构成、心率与压力区间分布、血氧范围、运动类型排行、体重与体脂趋势

**报告与分享**

- 导出 Markdown 分析报告，并同时调起系统分享
- 单张图表卡片可截图分享：暗色 / 浅色可选、圆角、右下角仓库二维码，底部合成应用水印条
- 「导出图文报告」：把报告与全部图表图片打包为 zip（`report.md` + `images/*.png`，报告内以相对路径引用），分享出去即是图文报告
- 「分享全部图表」：一次把全部图表图片交给系统分享面板（多图分享）
- 分享图样式（配色 / 圆角 / 二维码）可记住选择

**其他**

- 中英文界面，支持浅色 / 深色模式

## 环境要求

- 已安装 LSPosed（API 102 及以上）
- 已安装华为运动健康（`com.huawei.health`）

## 安装

1. 安装 `HealthExport` APK
2. 在 LSPosed 管理器中启用 `HealthExport` 模块
3. 在模块作用域中勾选「华为运动健康」
4. 关闭并重新打开华为运动健康

## 使用

1. 打开 `HealthExport`，在首页确认「LSPosed 服务已连接」且已获得作用域
2. 进入「健康数据导出」页，选择时间范围后点击「开始导出」完成预约
3. 切回华为运动健康（从最近任务打开，或点开其界面）：导出会在它回到前台时自动执行；若进程已被系统回收，则在启动时执行
4. 导出完成后回到导出页查看输出路径与日志，或进入「数据分析」页查看图表、导出报告与分享图表

导出文件默认保存到：

```text
Download/HuaweiHealthExport/huawei-health-export-<时间>.md
Download/HuaweiHealthExport/huawei-health-export-<时间>.summary.json
Download/HuaweiHealthExport/huawei-health-export-<时间>-detail.xlsx
Download/HuaweiHealthExport/huawei-health-export-<时间>-detail-csv.zip
```

（Android 10 以下或写入失败时，依次回退到华为运动健康外部存储与内部存储的 `export` 目录。）

> 首次安装或更新模块后，必须重启一次华为运动健康，模块注入才会生效；此后变更预约只需切回前台。
>
> 建议先在华为运动健康中完成一次数据同步，再执行导出。
>
> 睡眠分期需要账号内存在科学睡眠记录（佩戴华为穿戴设备并在运动健康中开启）。若导出后仍无分期数据，界面会明确提示该前提，而不是显示 0。

## 构建

要求：JDK 21、Android SDK（compileSdk 37）。

```bash
./gradlew :app:assembleRelease
```

## 隐私说明

- 应用不申请任何网络权限，导出的数据只写入本机存储，不会上传到任何服务器。
- 导出的 Markdown、JSON 摘要、明细表与报告压缩包均保存在设备本地，由用户自行保管与处置。
- 应用已关闭系统备份与设备迁移（`android:allowBackup="false"` 并显式排除全部备份域），健康数据摘要不会进入云备份。
- LSPosed 作用域仅包含 `com.huawei.health`；导出报告的进程间通道只接受本模块自身与华为运动健康进程的调用，且不提供任何数据查询或配置写入能力。
- 所有数据解析、图表绘制与图片合成均在设备本地完成，不依赖任何第三方服务。

## 开源说明与修改声明

本项目以 **GNU GPL-3.0** 授权发布，许可证全文见 [LICENSE](LICENSE)。

本项目是基于 [nairain233/huawei-health-customization](https://github.com/nairain233/huawei-health-customization)（同样以 GPL-3.0 授权）修改而来的**衍生作品**。原项目是一个面向华为运动健康的多功能 LSPosed 模块，本项目在其基础上裁剪与改造，成为只做健康数据导出与分析的工具。

依据 GPL-3.0 第 5(a) 条，在此声明：**本项目对上游文件进行了修改，修改发生于 2026-10-10 起，后续修改时间见 [changelog.md](changelog.md)。**

### 源自上游并已修改的文件

以下文件由上游项目对应文件修改而来（已重命名包名、裁剪与改写内容）：

```text
app/src/main/java/com/shawnsu/healthexport/app/AppTheme.kt
app/src/main/java/com/shawnsu/healthexport/app/ExportActivity.kt
app/src/main/java/com/shawnsu/healthexport/app/ExportStatusCard.kt
app/src/main/java/com/shawnsu/healthexport/app/ExportStatusController.kt
app/src/main/java/com/shawnsu/healthexport/app/ModuleApplication.kt
app/src/main/java/com/shawnsu/healthexport/app/ScopeSettingsCoordinator.kt
app/src/main/java/com/shawnsu/healthexport/app/SettingsActivity.kt
app/src/main/java/com/shawnsu/healthexport/app/SettingsScreen.kt
app/src/main/java/com/shawnsu/healthexport/app/SettingsUiState.kt
app/src/main/java/com/shawnsu/healthexport/config/SettingsKeys.kt
app/src/main/java/com/shawnsu/healthexport/export/ExportLogAnalyzer.kt
app/src/main/java/com/shawnsu/healthexport/export/ExportProtocol.kt
app/src/main/java/com/shawnsu/healthexport/export/ExportProvider.kt
app/src/main/java/com/shawnsu/healthexport/export/ExportRequests.kt
app/src/main/java/com/shawnsu/healthexport/export/ExportRuntime.kt
app/src/main/java/com/shawnsu/healthexport/hook/HookCoordinator.kt
app/src/main/java/com/shawnsu/healthexport/hook/HookEntry.kt
app/src/main/java/com/shawnsu/healthexport/hook/HookInstallPolicy.kt
app/src/main/java/com/shawnsu/healthexport/hook/util/ModuleLogger.kt
app/src/main/res/values/export_strings.xml
app/src/main/res/values-en/export_strings.xml
app/src/main/res/values/strings.xml
app/src/main/res/values-en/strings.xml
app/src/main/res/values/themes.xml
app/src/main/res/values-night/themes.xml
app/src/main/res/drawable/ic_launcher_background.xml
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/main/res/mipmap-anydpi/ic_launcher.xml
app/src/main/res/mipmap-anydpi/ic_launcher_round.xml
app/src/main/res/resources.properties
```

### 本项目新增的文件与功能

以下内容为独立实现，不源自上游：

- 应用图标（`ic_launcher_monochrome.xml` 及重绘的前景 / 背景）与仓库二维码（`repo_qr.png`）
- 数据分析页面与全部图表：`AnalysisActivity.kt`、`AnalysisReport.kt`、`AnalysisScreen.kt`、`AnalysisSupport.kt`、`ChartComponents.kt`、`ChartTheme.kt`
- 导出范围与产出格式：`ExportRange.kt`、`ExportRangeCard.kt`、`ExportSummary.kt`、`DetailWriter.kt`
- 报告分享：`ShareExporter.kt`、`ShareSession.kt`、`ShareStyle.kt`
- 明细表（Excel / CSV）导出、图文报告打包、图表图片合成与系统分享
- 睡眠分期深度分析、名称映射与单位 / 排序修正、前台恢复触发等改动

> 上游项目中的底部标签定制、服务屏蔽、布局裁剪、关于页 / 背景特效（`app/about/` 下若干文件为 Apache-2.0）等功能，本项目**未保留**。

## 免责声明

> 完整条款见 [DISCLAIMER.md](DISCLAIMER.md)。以下为要点摘要，二者不一致时以 `DISCLAIMER.md` 为准。

- **非官方、无关联**：本项目是个人开发的开源工具，与华为技术有限公司及其关联公司无任何关系，未经其授权、认可或赞助。「华为」「华为运动健康」等名称与商标归其各自权利人所有，本项目仅在描述兼容对象时使用。
- **仅限个人合法用途**：本项目仅供用户在**自己拥有或已获合法授权**的设备上，导出**自己账号下**的健康数据，用于个人数据备份、查看与研究学习。禁止用于任何侵犯他人隐私、非法获取他人数据或违反当地法律法规的用途。
- **不提供破解或绕过**：本项目不破解、不修改、不绕过华为运动健康的任何功能、付费内容或数字版权保护（DRM），不绕过任何账号或权限校验，仅在你已合法登录的前提下读取你本机已有的数据。
- **数据由你自行保管**：导出的健康数据属于敏感个人信息。文件保存在你的设备本地，其保管、传输、分享与合规使用（包括是否受《个人信息保护法》等法规约束）由你自行负责。请勿将导出的文件分享给他人或上传至不可信位置。
- **按「现状」提供，无任何担保**：本软件以「现状」提供，不附带任何明示或默示担保，包括但不限于适销性、特定用途适用性与不侵权担保。导出结果的完整性、准确性与兼容性不作保证。
- **使用风险自负**：使用本软件可能带来的设备异常、数据丢失、系统不稳定、保修失效或账号受限等风险，由使用者自行承担。
- **责任限制**：在法律允许的最大范围内，作者及贡献者不对因使用或无法使用本软件而产生的任何直接、间接、附带、特殊或后果性损失承担责任。
- **地区合规自负**：不同国家和地区对逆向工程、系统修改与个人数据处理的规定不同，请在下载与使用前自行确认符合当地法律法规。
- **许可证**：本项目以 GNU GPL-3.0 授权，任何再分发须遵守该许可证并保留版权声明。软件本身不附带任何形式的商业支持或质量承诺。

**下载、安装或使用本项目，即表示你已阅读、理解并同意上述全部条款。如不同意，请勿使用。**
