package com.shenyue.changyemobiletxt

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream

// 定义一个数据结构，用来装每一章的书名和内容
data class Chapter(val title: String, val content: String)

object EpubParser {

    // 使用 suspend 关键字，表明这是一个“耗时操作”，必须在协程（后台线程）中运行
    suspend fun extractChapters(context: Context, epubUri: Uri): List<Chapter> = withContext(Dispatchers.IO) {
        val chapters = mutableListOf<Chapter>()

        // 预先读取整个文件为字节数组，方便后续判断和处理
        val rawBytes: ByteArray = context.contentResolver.openInputStream(epubUri)?.use { it.readBytes() }
            ?: return@withContext chapters

        // 尝试判断是否为 EPUB（ZIP 文件以 PK 开头）
        val isZip = rawBytes.size >= 4 && rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte()

        if (isZip) {
            // ================= EPUB 解析逻辑 =================
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
        } else {
            // ================= TXT 纯文本解析 =================
            val rawText = String(rawBytes, Charsets.UTF_8).trim()
            if (rawText.isNotBlank()) {
                // 尝试按常见章节标题分割
                val regex = Regex("^\\s*第[0-9零一二三四五六七八九十百千万]+[章卷节回].*")
                val lines = rawText.lines()
                var currentTitle = "开篇"
                var currentContent = StringBuilder()
                var partCount = 1

                for (line in lines) {
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
                        // 每满 3000 字强行切分
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

        // ================= 最终兜底：如果 chapters 仍为空，按 3000 字暴力分割全文 =================
        if (chapters.isEmpty()) {
            val fullText = if (isZip) {
                // EPUB 中没有提取到任何章节，合并所有 HTML 的文本
                // 由于前面已经尝试过 EPub 解析，这里几乎不会执行，但以防万一
                ""
            } else {
                String(rawBytes, Charsets.UTF_8).trim()
            }

            if (fullText.isNotBlank()) {
                var index = 0
                var start = 0
                while (start < fullText.length) {
                    val end = minOf(start + 3000, fullText.length)
                    val segment = fullText.substring(start, end).trim()
                    if (segment.isNotEmpty()) {
                        chapters.add(Chapter(title = "分段${index + 1}", content = segment))
                        index++
                    }
                    start = end
                }
            }
        }

        return@withContext chapters
    }
}