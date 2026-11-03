package com.shenyue.changyetxt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 版本清单（托管在仓库根的 update.json）：
 * {
 *   "versionCode": 5,
 *   "versionName": "1.0.4",
 *   "notes": "本次更新说明（可选，简短）",
 *   "downloadUrl": "可选，留空则仅提示去交流群获取"
 * }
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val downloadUrl: String
)

/**
 * 检查更新。
 *
 * 为什么不直连 GitHub：api.github.com / raw.githubusercontent.com 在国内存在
 * DNS 污染与 TLS 拦截，请求会直接失败或被导向错误主机。因此版本清单放在
 * 国内可达的镜像上，并按顺序尝试多个来源，任一成功即返回。
 *
 * 安全约束：
 * - 只走 HTTPS。明文 HTTP 可被污染网络篡改，可能把用户导向恶意安装包。
 * - 只解析版本号等元数据，不下载、不执行任何内容。
 */
object UpdateChecker {

    // 依次尝试；全部失败则视为"无法检查"，静默跳过，不打扰用户
    private val SOURCES = listOf(
        // Gitee 主源（国内直连稳定）
        "https://gitee.com/shenyuechen/changyetxt/raw/master/update.json",
        // jsDelivr 加速 GitHub 仓库，作为兜底
        "https://cdn.jsdelivr.net/gh/shenyue-ge/Changyetxt@master/update.json"
    )

    sealed class Result {
        /** 有新版本 */
        data class Available(val info: UpdateInfo) : Result()
        /** 已是最新 */
        object UpToDate : Result()
        /** 所有源都不可达，无法判断 */
        object Unavailable : Result()
    }

    suspend fun check(currentVersionCode: Int): Result = withContext(Dispatchers.IO) {
        for (url in SOURCES) {
            val info = try {
                fetch(url)
            } catch (e: Exception) {
                null   // 换下一个源
            }
            if (info != null) {
                return@withContext if (info.versionCode > currentVersionCode) Result.Available(info) else Result.UpToDate
            }
        }
        Result.Unavailable
    }

    private fun fetch(urlString: String): UpdateInfo? {
        if (!urlString.startsWith("https://")) return null   // 拒绝明文传输
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "ChangyeTXT-Updater")
        }
        return try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parse(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String): UpdateInfo? {
        // 有些平台在文件不存在时会返回 HTML 登录页/错误页，这里必须是合法 JSON 才算数
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) return null
        return try {
            val obj = JSONObject(trimmed)
            val code = obj.optInt("versionCode", -1)
            val name = obj.optString("versionName", "").trim()
            if (code <= 0 || name.isEmpty()) return null
            UpdateInfo(
                versionCode = code,
                versionName = name,
                notes = obj.optString("notes", "").trim(),
                downloadUrl = obj.optString("downloadUrl", "").trim()
            )
        } catch (e: Exception) {
            null
        }
    }
}
