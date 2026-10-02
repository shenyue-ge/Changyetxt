package com.shenyue.changyetxt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipInputStream

// 手表端 EPUB 解析器：从手机端移植，改为直接读取本地 File
object EpubParser {

    suspend fun extractChapters(file: File): List<Chapter> = withContext(Dispatchers.IO) {
        val chapters = mutableListOf<Chapter>()

        val rawBytes = file.readBytes()

        // ZIP 文件（EPUB 容器）以 PK 开头；不是 ZIP 就按纯文本兜底处理
        val isZip = rawBytes.size >= 4 && rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte()

        if (isZip) {
            val htmlFiles = mutableMapOf<String, String>()
            var opfContent: String? = null
            var opfPath = ""

            ByteArrayInputStream(rawBytes).use { byteStream ->
                ZipInputStream(byteStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val name = entry.name
                            if (name.endsWith(".opf")) {
                                opfContent = zip.readBytes().toString(Charsets.UTF_8)
                                opfPath = name
                            } else if (name.endsWith(".html") || name.endsWith(".xhtml")) {
                                htmlFiles[name] = zip.readBytes().toString(Charsets.UTF_8)
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            if (opfContent != null) {
                // 有 OPF：按 spine 阅读顺序提取章节
                val doc = Jsoup.parse(opfContent, "", Parser.xmlParser())
                val manifestMap = mutableMapOf<String, String>()
                doc.select("manifest > item").forEach { item ->
                    val id = item.attr("id")
                    val href = item.attr("href")
                    if (id.isNotBlank() && href.isNotBlank()) manifestMap[id] = href
                }
                val basePath = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
                doc.select("spine > itemref").forEach { itemref ->
                    val idref = itemref.attr("idref")
                    val href = manifestMap[idref]
                    if (href != null) {
                        val decodedHref = URLDecoder.decode(href, "UTF-8")
                        val fullPath = basePath + decodedHref
                        var htmlContent = htmlFiles[fullPath]
                        if (htmlContent == null) htmlContent = htmlFiles.entries.find { it.key.endsWith(decodedHref) }?.value
                        if (htmlContent != null) {
                            val htmlDoc = Jsoup.parse(htmlContent)
                            val title = htmlDoc.selectFirst("h1, h2, h3")?.text()?.ifBlank { null }
                                ?: htmlDoc.title().ifBlank { decodedHref.substringAfterLast("/") }
                            val text = htmlDoc.body()?.text() ?: ""
                            if (text.isNotBlank()) chapters.add(Chapter(title, text))
                        }
                    }
                }
            } else {
                // 无 OPF 的 EPUB，按文件名排序解析
                htmlFiles.toSortedMap().forEach { (path, htmlContent) ->
                    val htmlDoc = Jsoup.parse(htmlContent)
                    val title = htmlDoc.selectFirst("h1, h2, h3")?.text()?.ifBlank { null }
                        ?: htmlDoc.title().ifBlank { path.substringAfterLast("/") }
                    val text = htmlDoc.body()?.text() ?: ""
                    if (text.isNotBlank()) chapters.add(Chapter(title, text))
                }
            }

            // 兜底：EPUB 结构异常导致没提取到任何章节时，把全部 HTML 文本按 3000 字切分
            if (chapters.isEmpty()) {
                val fullText = htmlFiles.toSortedMap().values.joinToString("\n") { html ->
                    Jsoup.parse(html).body()?.text() ?: ""
                }.trim()
                if (fullText.isNotBlank()) splitPlainText(fullText, chapters)
            }
        } else {
            val rawText = String(rawBytes, Charsets.UTF_8).trim()
            if (rawText.isNotBlank()) splitPlainText(rawText, chapters)
        }

        return@withContext chapters
    }

    // 与本地 TXT 导入一致的切分规则：识别章节标题 + 3000 字强制分卷
    private fun splitPlainText(rawText: String, chapters: MutableList<Chapter>) {
        val regex = Regex("^\\s*第[0-9零一二三四五六七八九十百千万]+[章卷节回].*")
        var currentTitle = "开篇"
        var currentContent = StringBuilder()
        var partCount = 1

        for (line in rawText.lines()) {
            val trimmed = line.trim()
            if (regex.matches(trimmed) && trimmed.length < 40) {
                if (currentContent.isNotBlank()) {
                    chapters.add(Chapter(currentTitle, currentContent.toString()))
                    currentContent = StringBuilder()
                }
                currentTitle = trimmed
                partCount = 1
            } else if (trimmed.isNotEmpty()) {
                currentContent.append(trimmed).append("\n\n")
                if (currentContent.length >= 3000) {
                    chapters.add(Chapter(if (partCount == 1) currentTitle else "$currentTitle ($partCount)", currentContent.toString()))
                    partCount++
                    currentContent = StringBuilder()
                }
            }
        }
        if (currentContent.isNotBlank()) {
            chapters.add(Chapter(if (partCount == 1) currentTitle else "$currentTitle ($partCount)", currentContent.toString()))
        }
    }
}
