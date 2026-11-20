package com.example.rssreader.core.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * 抓取 + 字符集判定。
 *
 * 字符集判定顺序：XML 声明里的 encoding → HTTP 响应头的 charset → UTF-8。
 * 判定时先用 ISO-8859-1 把前 512 字节当成"一字节一字符"来读，
 * 这样即使整篇是 GBK 也不会解码失败，也不会破坏 encoding= 这些 ASCII 关键字。
 */
object RssFetcher {

    const val ASSET_PREFIX = "asset://"

    private const val TIMEOUT_MS = 15_000
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) RssReader/1.0"

    data class RawDocument(
        val bytes: ByteArray,
        val charset: Charset
    )

    suspend fun fetch(context: Context, url: String): RawDocument = withContext(Dispatchers.IO) {
        if (url.startsWith(ASSET_PREFIX)) {
            val name = url.removePrefix(ASSET_PREFIX)
            val bytes = context.assets.open(name).use { it.readBytes() }
            RawDocument(bytes, detectCharset(bytes, null))
        } else {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "application/rss+xml, application/atom+xml, */*")
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IOException("HTTP $code")
                }
                val bytes = connection.inputStream.use { it.readBytes() }
                RawDocument(bytes, detectCharset(bytes, connection.contentType))
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun detectCharset(bytes: ByteArray, contentType: String?): Charset {
        // 1) XML 声明：<?xml version="1.0" encoding="GBK"?>
        val head = String(bytes, 0, minOf(bytes.size, 512), StandardCharsets.ISO_8859_1)
        val declared = Regex("encoding\\s*=\\s*[\"']([A-Za-z0-9_\\-]+)[\"']")
            .find(head)?.groupValues?.getOrNull(1)
            ?.let { runCatching { Charset.forName(it) }.getOrNull() }
        if (declared != null) return declared

        // 2) HTTP 头 charset
        val header = Regex("charset=([A-Za-z0-9_\\-]+)")
            .find(contentType.orEmpty())?.groupValues?.getOrNull(1)
            ?.let { runCatching { Charset.forName(it) }.getOrNull() }
        if (header != null) return header

        // 3) 默认 UTF-8
        return StandardCharsets.UTF_8
    }
}
