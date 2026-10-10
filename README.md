# HealthExport

将华为运动健康的历史健康数据导出为 Markdown 文件的 LSPosed 模块。

## 功能

- 导出华为运动健康中的历史健康数据：日常活动、睡眠、心率、体重、血氧、压力、运动记录等数据集
- 输出为单个 Markdown 文件，按数据集分别列出数据明细
- 预约式导出：在模块中预约后，随华为运动健康下次启动自动执行
- 导出日志：可查看当前预约、最近一次报告与导出过程
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
2. 进入「健康数据导出」页，点击「开始导出」完成预约
3. 切回华为运动健康（从最近任务打开，或点开其界面）：导出会在它回到前台时自动执行；若进程已被系统回收，则在启动时执行
4. 导出完成后回到导出页查看输出路径与日志

导出文件默认保存到：

```text
Download/HuaweiHealthExport/huawei-health-export-<时间>.md
```

（Android 10 以下或写入失败时，依次回退到华为运动健康外部存储与内部存储的 `export` 目录。）

> 首次安装或更新模块后，必须重启一次华为运动健康，模块注入才会生效；此后变更预约只需切回前台。
>
> 建议先在华为运动健康中完成一次数据同步，再执行导出。

## 构建

要求：JDK 21、Android SDK（compileSdk 37）。

```bash
./gradlew :app:assembleRelease
```

## 隐私说明

- 应用不申请任何网络权限，导出的数据只写入本机存储，不会上传到任何服务器。
- 导出的 Markdown 与 JSON 摘要保存在设备本地，由用户自行保管与处置。
- 应用已关闭系统备份与设备迁移（`android:allowBackup="false"` 并显式排除全部备份域），健康数据摘要不会进入云备份。
- LSPosed 作用域仅包含 `com.huawei.health`；导出报告的进程间通道只接受本模块自身与华为运动健康进程的调用，且不提供任何数据查询或配置写入能力。

## 开源说明

本项目基于 [nairain233/huawei-health-customization](https://github.com/nairain233/huawei-health-customization) 修改而来：仅保留其 LSPosed 模块框架（模块入口、服务连接与作用域管理）相关实现，导出功能、界面与其余代码均为独立实现。原项目与本项目均以 GNU GPL-3.0 授权，详见 [LICENSE](LICENSE)。
