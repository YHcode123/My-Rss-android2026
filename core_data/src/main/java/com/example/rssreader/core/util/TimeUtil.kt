package com.example.rssreader.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * RSS 各家的日期写法五花八门，这里做三件事：
 *  1. parse()：按一组常见 pattern 逐个尝试，全部失败返回 0（列表里就不会显示时间）
 *  2. relative()：列表里显示的"3 分钟前 / 昨天 09:12 / 08-15"
 *  3. absolute()：详情页与大图展示用的完整时间
 */
object TimeUtil {

    private val PATTERNS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss zzz",      // RFC 1123（RSS 2.0 最常见）
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss",
        "dd MMM yyyy HH:mm:ss zzz",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",       // ISO 8601（Atom）
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy-MM-dd",
        "yyyy/MM/dd"
    )

    fun parse(raw: String?): Long {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return 0L
        for (pattern in PATTERNS) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.timeZone = TimeZone.getTimeZone("GMT")
                val parsed = format.parse(text) ?: continue
                return parsed.time
            } catch (_: Exception) {
                // 换下一个 pattern
            }
        }
        return 0L
    }

    fun relative(time: Long): String {
        if (time <= 0L) return ""
        val now = System.currentTimeMillis()
        val diff = now - time
        return when {
            diff < 60_000L -> "刚刚"
            diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
            diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
            diff < 172_800_000L -> "昨天 ${format(time, "HH:mm")}"
            diff < 31_536_000_000L -> format(time, "MM-dd HH:mm")
            else -> format(time, "yyyy-MM-dd")
        }
    }

    fun absolute(time: Long): String {
        if (time <= 0L) return "未知时间"
        return format(time, "yyyy-MM-dd HH:mm")
    }

    private fun format(time: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.CHINA).format(Date(time))
}
