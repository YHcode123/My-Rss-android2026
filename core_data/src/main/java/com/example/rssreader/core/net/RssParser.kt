package com.example.rssreader.core.net

import android.util.Xml
import com.example.rssreader.core.util.HtmlText
import com.example.rssreader.core.util.TimeUtil
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

/** 解析结果里的一篇文章（还没写库，纯内存对象）。 */
data class ParsedArticle(
    val guid: String,
    val title: String,
    val link: String?,
    val author: String?,
    val summary: String?,
    val content: String?,
    val pubDate: Long
)

/** 一个源解析出来的频道标题 + 文章列表。 */
data class ParsedFeed(
    val title: String,
    val articles: List<ParsedArticle>
)

/**
 * 一套逻辑同时吃三种订阅格式：
 *   RSS 2.0  → item / pubDate / description / content:encoded
 *   Atom     → entry / published / updated / summary / link[href]
 *   RSS 1.0  → item(RDF) / dc:date / dc:creator
 *
 * 做法：关闭命名空间处理后，比较标签名时统一"去掉前缀 + 转小写"，
 * 因此 "dc:date" 会被当作 "date"、"content:encoded" 当作 "encoded"。
 */
class RssParser {

    fun parse(stream: InputStream, charset: Charset): ParsedFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(InputStreamReader(stream, charset))

        var feedTitle = ""
        val articles = ArrayList<ParsedArticle>()

        var inItem = false
        var title = ""
        var link = ""
        var author = ""
        var summary = ""
        var content = ""
        var guid = ""
        var dateText = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tagName(parser)) {
                        "item", "entry" -> {
                            inItem = true
                            title = ""
                            link = ""
                            author = ""
                            summary = ""
                            content = ""
                            guid = ""
                            dateText = ""
                        }

                        "title" -> {
                            val text = readText(parser)
                            if (inItem) {
                                if (title.isEmpty()) title = text
                            } else if (feedTitle.isEmpty()) {
                                feedTitle = text
                            }
                        }

                        "link" -> {
                            // Atom 用属性写地址，RSS 用标签文本写地址，两种都吃
                            val href = attr(parser, "href")
                            val text = if (href.isNullOrBlank()) readText(parser) else ""
                            if (inItem) {
                                val candidate = if (!href.isNullOrBlank()) href else text
                                if (link.isEmpty() && candidate.isNotBlank()) link = candidate
                            }
                        }

                        "description", "summary" -> {
                            val text = readText(parser)
                            if (inItem && summary.isEmpty()) summary = text
                        }

                        "encoded", "content" -> {
                            val text = readText(parser)
                            if (inItem && content.isEmpty()) content = text
                        }

                        "pubdate", "published", "updated", "date" -> {
                            val text = readText(parser)
                            if (inItem && dateText.isEmpty()) dateText = text
                        }

                        "author", "creator" -> {
                            val text = readText(parser)
                            if (inItem && author.isEmpty()) author = text
                        }

                        "guid", "id" -> {
                            val text = readText(parser)
                            if (inItem && guid.isEmpty()) guid = text
                        }

                        else -> Unit
                    }
                }

                XmlPullParser.END_TAG -> {
                    val name = tagName(parser)
                    if (name == "item" || name == "entry") {
                        inItem = false
                        buildArticle(guid, title, link, author, summary, content, dateText)?.let(articles::add)
                    }
                }

                else -> Unit
            }
            event = parser.next()
        }

        return ParsedFeed(title = feedTitle.trim(), articles = articles)
    }

    private fun buildArticle(
        guid: String,
        title: String,
        link: String,
        author: String,
        summary: String,
        content: String,
        dateText: String
    ): ParsedArticle? {
        val cleanTitle = title.trim()
        val cleanLink = link.trim().ifBlank { null }
        if (cleanTitle.isEmpty() && cleanLink == null) return null

        // guid 三级兜底：源站 guid → 链接 → 标题哈希 + 日期
        val stableGuid = guid.trim()
            .ifBlank { cleanLink.orEmpty() }
            .ifBlank { "urn:rss:${cleanTitle.hashCode()}:$dateText" }

        val contentHtml = content.ifBlank { summary }

        return ParsedArticle(
            guid = stableGuid,
            title = cleanTitle.ifBlank { "（无标题）" },
            link = cleanLink,
            author = author.trim().ifBlank { null },
            summary = HtmlText.strip(summary.ifBlank { content }).ifBlank { null },
            content = HtmlText.clamp(contentHtml),
            pubDate = TimeUtil.parse(dateText)
        )
    }

    private fun tagName(parser: XmlPullParser): String =
        (parser.name ?: "").substringAfterLast(':').lowercase()

    private fun attr(parser: XmlPullParser, name: String): String? =
        parser.getAttributeValue(null, name)

    /**
     * 读一个标签内部的全部文本（能处理 CDATA 和嵌套标签）。
     * 返回时解析器停在对应的 END_TAG 上。
     */
    private fun readText(parser: XmlPullParser): String {
        var depth = 1
        val builder = StringBuilder()
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF ->
                    builder.append(parser.text ?: "")
                XmlPullParser.END_DOCUMENT -> return builder.toString().trim()
            }
        }
        return builder.toString().trim()
    }
}
