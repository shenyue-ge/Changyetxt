package com.shenyue.changyetxt

data class Chapter(val title: String, val content: String)
data class NovelPayload(val bookName: String, val chapters: List<Chapter>)

// 新增：书签数据结构
data class Bookmark(
    val id: String,          // 唯一标识符，用于修改和删除
    val chapterIndex: Int,   // 章节索引
    val scrollY: Int,        // 滑动位置(精确到像素)
    val chapterTitle: String,// 章节标题
    var snippet: String,     // 预览文字(默认抓取屏幕，可被用户修改)
    val timestamp: Long      // 添加时间，用于排序
)