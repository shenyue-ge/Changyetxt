package com.shenyue.changyetxt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class LibraryAdapter(
    private val files: List<File>,
    private val onItemClick: (File) -> Unit
) : RecyclerView.Adapter<LibraryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleView: TextView = view.findViewById(R.id.text_chapter_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chapter, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = files[position]
        val displayName = file.name.substringAfterLast("_").removeSuffix(".json")
        holder.titleView.text = "📚 $displayName"

        // 【核心魔法】：强制激活文字选中状态，触发跑马灯滚动
        holder.titleView.isSelected = true

        holder.itemView.setOnClickListener { onItemClick(file) }
    }

    override fun getItemCount() = files.size
}

class ChapterAdapter(
    private val chapters: List<Chapter>,
    private val onItemClick: (Chapter) -> Unit,
    private val onItemLongClick: () -> Unit
) : RecyclerView.Adapter<ChapterAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleView: TextView = view.findViewById(R.id.text_chapter_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chapter, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chapter = chapters[position]
        holder.titleView.text = chapter.title

        // 【核心魔法】：目录页的长章节名也能自动滚动了！
        holder.titleView.isSelected = true

        holder.itemView.setOnClickListener { onItemClick(chapter) }
        holder.itemView.setOnLongClickListener { onItemLongClick(); true }
    }

    override fun getItemCount() = chapters.size
}

// 书签适配器 (保持不变)
class BookmarkAdapter(
    private var bookmarks: List<Bookmark>,
    private val onItemClick: (Bookmark) -> Unit,
    private val onItemLongClick: (Bookmark) -> Unit
) : RecyclerView.Adapter<BookmarkAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleView: TextView = view.findViewById(R.id.text_bookmark_title)
        val snippetView: TextView = view.findViewById(R.id.text_bookmark_snippet)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bookmark, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bookmark = bookmarks[position]
        holder.titleView.text = bookmark.chapterTitle

        // 书签的标题也加上跑马灯
        holder.titleView.isSelected = true

        holder.snippetView.text = bookmark.snippet
        holder.itemView.setOnClickListener { onItemClick(bookmark) }
        holder.itemView.setOnLongClickListener { onItemLongClick(bookmark); true }
    }

    override fun getItemCount() = bookmarks.size

    fun updateData(newBookmarks: List<Bookmark>) {
        bookmarks = newBookmarks
        notifyDataSetChanged()
    }
}