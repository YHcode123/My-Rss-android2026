package com.example.rssreader.core.util

/**
 * 极简 HTML 处理：列表摘要只需要纯文本，详情页需要保留标签交给 HtmlCompat 渲染。
 * 没有引入第三方富文本库，保持零依赖。
 */
object HtmlText {

    private val TAG = Regex("<[^>]*>")
    private val SPACE = Regex("&nbsp;|&#160;")
    private val ENTITIES = mapOf(
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'",
        "&apos;" to "'",
        "&amp;" to "&"
    )

    fun strip(html: String?): String {
        if (html.isNullOrBlank()) return ""
        var text = TAG.replace(html, " ")
        text = SPACE.replace(text, " ")
        for ((entity, char) in ENTITIES) {
            text = text.replace(entity, char)
        }
        return text.replace(Regex("\\s+"), " ").trim()
    }

    /** 正文限长，避免一篇超长文章把数据库撑爆。 */
    fun clamp(html: String?, maxLength: Int = 200_000): String? {
        if (html.isNullOrBlank()) return null
        return if (html.length <= maxLength) html else html.substring(0, maxLength)
    }
}
