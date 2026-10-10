package com.shawnsu.healthexport.export

import android.content.SharedPreferences

/** RemotePreferences 可能先修改本地缓存；失败时恢复旧值，不能乐观显示已预约。 */
internal enum class ExportRequestOutcome { SUCCESS, RESTORED, UNCERTAIN }

internal object ExportRequests {
    /** 预约与时间范围同一次提交写入；失败时一并回滚，避免「新范围配旧预约」。 */
    fun save(
        preferences: SharedPreferences,
        request: String,
        range: String = ExportRange.ALL,
        canWrite: () -> Boolean = { true },
    ): ExportRequestOutcome {
        val encodedRange = if (ExportRange.isValid(range)) range else ExportRange.ALL
        val existed: Boolean
        val previousRequest: String?
        val previousRange: String?
        try {
            existed = preferences.contains(ExportProtocol.REQUEST)
            previousRequest = preferences.getString(ExportProtocol.REQUEST, null)
            previousRange = preferences.getString(ExportProtocol.RANGE, null)
        } catch (_: RuntimeException) {
            // 尚未写入；调用方显示失败，允许重试。
            return ExportRequestOutcome.RESTORED
        }
        val saved = try {
            val editor = preferences.edit()
                .putString(ExportProtocol.REQUEST, request)
                .putString(ExportProtocol.RANGE, encodedRange)
            // 读取原值期间也可能停止或换绑；此处是首次提交的最后一道守卫。
            if (!canWrite()) return ExportRequestOutcome.RESTORED
            editor.commit()
        } catch (_: RuntimeException) { false }
        if (saved) return ExportRequestOutcome.SUCCESS
        val restored = try {
            val editor = preferences.edit()
            if (existed) editor.putString(ExportProtocol.REQUEST, previousRequest)
            else editor.remove(ExportProtocol.REQUEST)
            if (previousRange == null) editor.remove(ExportProtocol.RANGE)
            else editor.putString(ExportProtocol.RANGE, previousRange)
            editor.commit()
        } catch (_: RuntimeException) { false }
        // 两次提交都必须确认；缓存中的旧值不能证明远端已恢复。
        return if (restored) ExportRequestOutcome.RESTORED else ExportRequestOutcome.UNCERTAIN
    }

    /** 读取预约的时间范围编码；键缺席或读取失败均视为全部历史。 */
    fun range(preferences: SharedPreferences?): String = try {
        preferences?.getString(ExportProtocol.RANGE, null)?.takeIf { ExportRange.isValid(it) } ?: ExportRange.ALL
    } catch (_: RuntimeException) {
        ExportRange.ALL
    }
}

internal object ExportAdmission {
    fun allows(uid: Int, ownUid: Int, packages: List<String>): Boolean = uid == ownUid ||
        "com.huawei.health" in packages
}
