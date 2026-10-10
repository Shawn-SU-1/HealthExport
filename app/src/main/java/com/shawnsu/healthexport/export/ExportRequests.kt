package com.shawnsu.healthexport.export

import android.content.SharedPreferences
import android.annotation.SuppressLint

/** RemotePreferences 可能先修改本地缓存；失败时恢复旧值，不能乐观显示已预约。 */
internal enum class ExportRequestOutcome { SUCCESS, RESTORED, UNCERTAIN }

internal object ExportRequests {
    @SuppressLint("ApplySharedPref", "UseKtx") // 工作线程需要 commit 返回值确认预约与回滚是否落盘。
    fun save(
        preferences: SharedPreferences,
        request: String,
        canWrite: () -> Boolean = { true },
    ): ExportRequestOutcome {
        val existed: Boolean
        val previous: String?
        try {
            existed = preferences.contains(ExportProtocol.REQUEST)
            previous = preferences.getString(ExportProtocol.REQUEST, null)
        } catch (_: RuntimeException) {
            // 尚未写入；调用方显示失败，允许重试。
            return ExportRequestOutcome.RESTORED
        }
        val saved = try {
            val editor = preferences.edit().putString(ExportProtocol.REQUEST, request)
            // 读取原值期间也可能停止或换绑；此处是首次提交的最后一道守卫。
            if (!canWrite()) return ExportRequestOutcome.RESTORED
            editor.commit()
        } catch (_: RuntimeException) { false }
        if (saved) return ExportRequestOutcome.SUCCESS
        val restored = try {
            val editor = preferences.edit()
            if (existed) editor.putString(ExportProtocol.REQUEST, previous)
            else editor.remove(ExportProtocol.REQUEST)
            editor.commit()
        } catch (_: RuntimeException) { false }
        // 两次提交都必须确认；缓存中的旧值不能证明远端已恢复。
        return if (restored) ExportRequestOutcome.RESTORED else ExportRequestOutcome.UNCERTAIN
    }
}

internal object ExportAdmission {
    fun allows(uid: Int, ownUid: Int, packages: List<String>): Boolean = uid == ownUid ||
        "com.huawei.health" in packages
}
