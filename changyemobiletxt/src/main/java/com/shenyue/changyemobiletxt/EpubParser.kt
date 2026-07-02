package com.shenyue.changyemobiletxt

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream

// 定义一个数据结构，用来装每一章的书名和内容
data class Chapter(val title: String, val content: String)

object EpubParser {

    // 使用 suspend 关键字，表明这是一个“耗时操作”，必须在协程（后台线程）中运行
    suspend fun extractChapters(context: Context, epubUri: Uri): List<Chapter> = withContext(Dispatchers.IO) {

        // 用于暂存所有网页内容 (路径 -> 网页代码)
        val htmlFiles = mutableMapOf<String, String>()
        var opfContent: String? = null
        var opfPath = ""

        // ================= 第一阶段：将所有文件全部抓进内存 =================
        val inputStream: InputStream? = context.contentResolver.openInputStream(epubUri)
        inputStream?.use { stream ->
            val zipInputStream = ZipInputStream(stream)
            var entry = zipInputStream.nextEntry

            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name
                    if (name.endsWith(".opf")) {
                        // 找到了传说中的目录大脑！
                        opfContent = zipInputStream.readBytes().toString(Charsets.UTF_8)
                        opfPath = name
                    } else if (name.endsWith(".html") || name.endsWith(".xhtml")) {
                        // 把网页文件全部暂存进 HashMap
                        htmlFiles[name] = zipInputStream.readBytes().toString(Charsets.UTF_8)
                    }
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
        }

        val chapters = mutableListOf<Chapter>()

        // ================= 第二阶段：根据大脑 (OPF) 的脊骨顺序重组小说 =================
        if (opfContent != null) {
            // 使用 Jsoup 的专门 XML 解析器解析 OPF
            val doc = Jsoup.parse(opfContent, "", Parser.xmlParser())

            // 1. 提取所有资源清单 (清单ID -> 文件路径)
            val manifestMap = mutableMapOf<String, String>()
            doc.select("manifest > item").forEach { item ->
                val id = item.attr("id")
                val href = item.attr("href")
                if (id.isNotBlank() && href.isNotBlank()) {
                    manifestMap[id] = href
                }
            }

            // 计算 OPF 文件所在的基础路径，用来拼接出真实的 HTML 路径
            val basePath = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

            // 2. 核心魔法：按照 spine (脊骨) 中定义的严格阅读顺序去提取文章！
            doc.select("spine > itemref").forEach { itemref ->
                val idref = itemref.attr("idref")
                val href = manifestMap[idref]

                if (href != null) {
                    // EPUB 路径里如果有空格会被转码为 %20，需要先解码
                    val decodedHref = URLDecoder.decode(href, "UTF-8")
                    val fullPath = basePath + decodedHref

                    // 去我们的内存库里精确提取对应的网页
                    var htmlContent = htmlFiles[fullPath]

                    // 究极防错：如果相对路径没匹配上，尝试通过文件名后缀直接强行匹配
                    if (htmlContent == null) {
                        htmlContent = htmlFiles.entries.find { it.key.endsWith(decodedHref) }?.value
                    }

                    if (htmlContent != null) {
                        val htmlDoc = Jsoup.parse(htmlContent)
                        val headerTitle = htmlDoc.selectFirst("h1, h2, h3")?.text()
                        val chapterTitle = headerTitle?.ifBlank { null } ?: htmlDoc.title().ifBlank { decodedHref.substringAfterLast("/") }
                        val chapterContent = htmlDoc.body()?.text() ?: ""

                        if (chapterContent.isNotBlank()) {
                            chapters.add(Chapter(chapterTitle, chapterContent))
                        }
                    }
                }
            }
        } else {
            // ================= 第三阶段：兜底机制 =================
            // 万一遇到极度劣质、连 OPF 都没有的 EPUB，我们采用文件名按字母表顺序(自然排序)的兜底方案
            htmlFiles.toSortedMap().forEach { (path, htmlContent) ->
                val htmlDoc = Jsoup.parse(htmlContent)
                val headerTitle = htmlDoc.selectFirst("h1, h2, h3")?.text()
                val chapterTitle = headerTitle?.ifBlank { null } ?: htmlDoc.title().ifBlank { path.substringAfterLast("/") }
                val chapterContent = htmlDoc.body()?.text() ?: ""

                if (chapterContent.isNotBlank()) {
                    chapters.add(Chapter(chapterTitle, chapterContent))
                }
            }
        }

        return@withContext chapters
    }
}