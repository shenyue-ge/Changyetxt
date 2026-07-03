package com.shenyue.changyetxt

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {

    private var touchStartX = 0f
    private var touchStartY = 0f

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> { touchStartX = ev.x; touchStartY = ev.y }
            MotionEvent.ACTION_UP -> {
                val diffX = ev.x - touchStartX
                val diffY = kotlin.math.abs(ev.y - touchStartY)
                if (diffX > 150 && diffX > diffY) {
                    onBackPressedDispatcher.onBackPressed()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private var chaptersList: List<Chapter> = emptyList()
    private var currentChapterIndex = -1
    private var currentNovelDir: File? = null

    private var autoScrollJob: Job? = null
    private var isAutoScroll = false
    private var scrollSpeed = 1
    private var currentFontSize = 16f
    private var brightLevel = 0

    private var isUserTouching = false
    private var hideScrollBarJob: Job? = null
    private var isSettingsFromReading = false

    // 视图
    private lateinit var homeView: ScrollView
    private lateinit var aboutView: ScrollView

    // 书架相关
    private lateinit var libraryContainer: RelativeLayout
    private lateinit var libraryView: RecyclerView
    private lateinit var textEmptyLibrary: TextView

    private lateinit var directoryContainer: RelativeLayout
    private lateinit var directoryView: RecyclerView
    private lateinit var readingView: RelativeLayout
    private lateinit var readingScrollView: ScrollView
    private lateinit var menuView: ScrollView
    private lateinit var importMenuView: ScrollView
    private lateinit var ipInputView: ScrollView

    // 本地导入相关
    private lateinit var localImportContainer: RelativeLayout
    private lateinit var localFilesView: RecyclerView
    private lateinit var textNoLocalFiles: TextView

    private lateinit var prefsView: ScrollView
    private lateinit var autoScrollPrefsView: ScrollView
    private lateinit var displayPrefsView: ScrollView
    private lateinit var bookmarkListContainer: RelativeLayout
    private lateinit var bookmarkView: RecyclerView
    private lateinit var textNoBookmarks: TextView

    private lateinit var textContent: TextView
    private lateinit var textTime: TextView
    private lateinit var textBattery: TextView
    private lateinit var bottomSeekBar: SeekBar
    private lateinit var editIp: EditText

    private lateinit var btnToggleAutoScroll: Button
    private lateinit var btnToggleSpeed: Button
    private lateinit var tvFontSize: TextView
    private lateinit var tvBrightLevel: TextView

    private lateinit var loadingView: RelativeLayout
    private lateinit var textLoadingStatus: TextView

    private lateinit var bookmarkAdapter: BookmarkAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        // 基础绑定
        homeView = findViewById(R.id.home_view)
        aboutView = findViewById(R.id.about_view)

        libraryContainer = findViewById(R.id.library_container)
        libraryView = findViewById(R.id.library_view)
        textEmptyLibrary = findViewById(R.id.text_empty_library)

        directoryContainer = findViewById(R.id.directory_container)
        directoryView = findViewById(R.id.directory_view)
        readingView = findViewById(R.id.reading_view)
        readingScrollView = findViewById(R.id.reading_scroll_view)
        menuView = findViewById(R.id.menu_view)
        importMenuView = findViewById(R.id.import_menu_view)
        ipInputView = findViewById(R.id.ip_input_view)

        localImportContainer = findViewById(R.id.local_import_container)
        localFilesView = findViewById(R.id.local_files_view)
        textNoLocalFiles = findViewById(R.id.text_no_local_files)

        prefsView = findViewById(R.id.prefs_view)
        autoScrollPrefsView = findViewById(R.id.auto_scroll_prefs_view)
        displayPrefsView = findViewById(R.id.display_prefs_view)
        bookmarkListContainer = findViewById(R.id.bookmark_list_container)
        bookmarkView = findViewById(R.id.bookmark_view)
        textNoBookmarks = findViewById(R.id.text_no_bookmarks)
        bottomSeekBar = findViewById(R.id.bottom_seek_bar)

        textContent = findViewById(R.id.text_content)
        textTime = findViewById(R.id.text_time)
        textBattery = findViewById(R.id.text_battery)
        editIp = findViewById(R.id.edit_ip)

        loadingView = findViewById(R.id.loading_view)
        textLoadingStatus = findViewById(R.id.text_loading_status)

        libraryView.layoutManager = LinearLayoutManager(this)
        directoryView.layoutManager = LinearLayoutManager(this)
        bookmarkView.layoutManager = LinearLayoutManager(this)
        localFilesView.layoutManager = LinearLayoutManager(this)

        val prefs = getPreferences(MODE_PRIVATE)
        isAutoScroll = prefs.getBoolean("auto_scroll", false)
        scrollSpeed = prefs.getInt("scroll_speed", 1)
        currentFontSize = prefs.getFloat("font_size", 16f)
        brightLevel = prefs.getInt("bright_level", 0)

        applyDisplaySettings()
        startAutoScroll()

        readingScrollView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    isUserTouching = true
                    hideScrollBarJob?.cancel()
                    if (!readingScrollView.isVerticalScrollBarEnabled) readingScrollView.isVerticalScrollBarEnabled = true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    hideScrollBarJob?.cancel()
                    hideScrollBarJob = lifecycleScope.launch { delay(1500); readingScrollView.isVerticalScrollBarEnabled = false; isUserTouching = false }
                }
            }
            false
        }

        // 路由中心
        findViewById<View>(R.id.btn_home_library).setOnClickListener { homeView.visibility = View.GONE; libraryContainer.visibility = View.VISIBLE; refreshLibrary() }
        findViewById<View>(R.id.btn_home_settings).setOnClickListener { isSettingsFromReading = false; homeView.visibility = View.GONE; prefsView.visibility = View.VISIBLE }
        findViewById<View>(R.id.btn_home_about).setOnClickListener { homeView.visibility = View.GONE; aboutView.visibility = View.VISIBLE }

        findViewById<View>(R.id.btn_home_import).setOnClickListener { homeView.visibility = View.GONE; importMenuView.visibility = View.VISIBLE }

        findViewById<View>(R.id.btn_import_lan).setOnClickListener {
            importMenuView.visibility = View.GONE
            ipInputView.visibility = View.VISIBLE
            editIp.setText(prefs.getString("server_ip", "192.168.31.") ?: "192.168.31.")
            editIp.setSelection(editIp.text.length)
        }

        findViewById<View>(R.id.btn_import_local).setOnClickListener {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
                Toast.makeText(this, "需要存储权限以进行全盘扫描", Toast.LENGTH_SHORT).show()
            } else {
                scanAllLocalTxtFiles()
            }
        }

        findViewById<View>(R.id.menu_item_add_bookmark).setOnClickListener { menuView.visibility = View.GONE; addBookmarkForCurrentPosition() }
        findViewById<View>(R.id.menu_item_bookmarks_manage).setOnClickListener { menuView.visibility = View.GONE; showBookmarkManager() }
        bookmarkAdapter = BookmarkAdapter(emptyList(),
            onItemClick = { bookmark -> loadChapter(bookmark.chapterIndex, false, bookmark.scrollY); bookmarkListContainer.visibility = View.GONE; readingView.visibility = View.VISIBLE },
            onItemLongClick = { bookmark -> showBookmarkEditDialog(bookmark) }
        )
        bookmarkView.adapter = bookmarkAdapter

        findViewById<Button>(R.id.btn_menu_auto_scroll).setOnClickListener { prefsView.visibility = View.GONE; autoScrollPrefsView.visibility = View.VISIBLE; updateAutoScrollUI() }
        findViewById<Button>(R.id.btn_menu_display).setOnClickListener { prefsView.visibility = View.GONE; displayPrefsView.visibility = View.VISIBLE; updateDisplayUI() }
        btnToggleAutoScroll = findViewById(R.id.btn_toggle_auto_scroll)
        btnToggleSpeed = findViewById(R.id.btn_toggle_speed)
        tvFontSize = findViewById(R.id.tv_font_size)
        tvBrightLevel = findViewById(R.id.tv_bright_level)

        btnToggleAutoScroll.setOnClickListener { isAutoScroll = !isAutoScroll; prefs.edit().putBoolean("auto_scroll", isAutoScroll).apply(); updateAutoScrollUI(); startAutoScroll() }
        btnToggleSpeed.setOnClickListener { scrollSpeed = (scrollSpeed + 1) % 3; prefs.edit().putInt("scroll_speed", scrollSpeed).apply(); updateAutoScrollUI(); startAutoScroll() }
        findViewById<Button>(R.id.btn_font_down).setOnClickListener { if (currentFontSize > 12f) { currentFontSize -= 2f; prefs.edit().putFloat("font_size", currentFontSize).apply(); applyDisplaySettings() } }
        findViewById<Button>(R.id.btn_font_up).setOnClickListener { if (currentFontSize < 28f) { currentFontSize += 2f; prefs.edit().putFloat("font_size", currentFontSize).apply(); applyDisplaySettings() } }
        findViewById<Button>(R.id.btn_bright_down).setOnClickListener { if (brightLevel > 0) { brightLevel -= 1; prefs.edit().putInt("bright_level", brightLevel).apply(); applyDisplaySettings() } }
        findViewById<Button>(R.id.btn_bright_up).setOnClickListener { if (brightLevel < 5) { brightLevel += 1; prefs.edit().putInt("bright_level", brightLevel).apply(); applyDisplaySettings() } }

        val btnPageUp = findViewById<View>(R.id.btn_page_up)
        val btnPageDown = findViewById<View>(R.id.btn_page_down)
        btnPageDown.setOnClickListener { hideScrollBarJob?.cancel(); readingScrollView.isVerticalScrollBarEnabled = false; val amt = (readingScrollView.height * 0.66).toInt(); if (readingScrollView.canScrollVertically(1)) readingScrollView.smoothScrollBy(0, amt) else loadChapter(currentChapterIndex + 1, false) }
        btnPageUp.setOnClickListener { hideScrollBarJob?.cancel(); readingScrollView.isVerticalScrollBarEnabled = false; val amt = (readingScrollView.height * 0.66).toInt(); if (readingScrollView.canScrollVertically(-1)) readingScrollView.smoothScrollBy(0, -amt) else loadChapter(currentChapterIndex - 1, true) }

        val showMenuListener = View.OnLongClickListener { menuView.visibility = View.VISIBLE; true }
        textContent.setOnLongClickListener(showMenuListener)
        btnPageUp.setOnLongClickListener(showMenuListener)
        btnPageDown.setOnLongClickListener(showMenuListener)

        directoryView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) { val pos = (directoryView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition(); if (pos != RecyclerView.NO_POSITION) bottomSeekBar.progress = pos }
        })
        bottomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) directoryView.scrollToPosition(progress) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        findViewById<View>(R.id.menu_item_toc).setOnClickListener { saveReadingProgress(); menuView.visibility = View.GONE; readingView.visibility = View.GONE; directoryContainer.visibility = View.VISIBLE; if (currentChapterIndex != -1) directoryView.scrollToPosition(currentChapterIndex) }
        findViewById<View>(R.id.menu_item_library).setOnClickListener { saveReadingProgress(); menuView.visibility = View.GONE; readingView.visibility = View.GONE; libraryContainer.visibility = View.VISIBLE; refreshLibrary() }
        findViewById<View>(R.id.menu_item_prefs).setOnClickListener { isSettingsFromReading = true; menuView.visibility = View.GONE; prefsView.visibility = View.VISIBLE }

        findViewById<Button>(R.id.btn_connect).setOnClickListener {
            val targetIp = editIp.text.toString().trim()
            if (targetIp.isNotEmpty()) {
                prefs.edit().putString("server_ip", targetIp).apply()
                ipInputView.visibility = View.GONE
                fetchNovelFromServer(targetIp)
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            when {
                loadingView.visibility == View.VISIBLE -> { Toast.makeText(this@MainActivity, "请稍候...", Toast.LENGTH_SHORT).show() }
                autoScrollPrefsView.visibility == View.VISIBLE -> { autoScrollPrefsView.visibility = View.GONE; prefsView.visibility = View.VISIBLE }
                displayPrefsView.visibility == View.VISIBLE -> { displayPrefsView.visibility = View.GONE; prefsView.visibility = View.VISIBLE }
                prefsView.visibility == View.VISIBLE -> { prefsView.visibility = View.GONE; if (isSettingsFromReading) menuView.visibility = View.VISIBLE else homeView.visibility = View.VISIBLE }
                aboutView.visibility == View.VISIBLE -> { aboutView.visibility = View.GONE; homeView.visibility = View.VISIBLE }
                ipInputView.visibility == View.VISIBLE -> { ipInputView.visibility = View.GONE; importMenuView.visibility = View.VISIBLE }
                localImportContainer.visibility == View.VISIBLE -> { localImportContainer.visibility = View.GONE; importMenuView.visibility = View.VISIBLE }
                importMenuView.visibility == View.VISIBLE -> { importMenuView.visibility = View.GONE; homeView.visibility = View.VISIBLE }
                libraryContainer.visibility == View.VISIBLE -> { libraryContainer.visibility = View.GONE; homeView.visibility = View.VISIBLE }
                bookmarkListContainer.visibility == View.VISIBLE -> { bookmarkListContainer.visibility = View.GONE; menuView.visibility = View.VISIBLE }
                menuView.visibility == View.VISIBLE -> menuView.visibility = View.GONE
                readingView.visibility == View.VISIBLE -> { saveReadingProgress(); readingView.visibility = View.GONE; libraryContainer.visibility = View.VISIBLE; refreshLibrary() }
                directoryContainer.visibility == View.VISIBLE -> { directoryContainer.visibility = View.GONE; libraryContainer.visibility = View.VISIBLE }
                else -> finish()
            }
        }

        val startupIp = prefs.getString("server_ip", "") ?: ""
        if (startupIp.isNotBlank() && !startupIp.endsWith(".")) fetchNovelFromServer(startupIp, isSilent = true)
    }

    // ================= 全盘扫描 =================
    private fun scanAllLocalTxtFiles() {
        importMenuView.visibility = View.GONE
        loadingView.visibility = View.VISIBLE
        textLoadingStatus.text = "正在全盘搜索 TXT..."

        lifecycleScope.launch(Dispatchers.IO) {
            val txtFiles = mutableListOf<File>()
            val root = Environment.getExternalStorageDirectory()

            // 递归扫描算法
            fun scanDir(dir: File) {
                val files = dir.listFiles() ?: return
                for (file in files) {
                    if (file.isDirectory) {
                        // 过滤掉隐藏文件夹和系统缓存，大幅提升手表扫描速度
                        if (!file.name.startsWith(".") && file.name != "Android") {
                            scanDir(file)
                        }
                    } else if (file.name.lowercase(Locale.getDefault()).endsWith(".txt")) {
                        txtFiles.add(file)
                    }
                }
            }

            scanDir(root)

            withContext(Dispatchers.Main) {
                loadingView.visibility = View.GONE
                // 复用 LibraryAdapter 显示本地文件列表
                localFilesView.adapter = LibraryAdapter(txtFiles) { clickedFile ->
                    parseLocalTxtFile(clickedFile)
                }
                // 控制空占位符显示
                textNoLocalFiles.visibility = if (txtFiles.isEmpty()) View.VISIBLE else View.GONE
                localImportContainer.visibility = View.VISIBLE
            }
        }
    }

    // ================= 本地 TXT 切割 =================
    private fun parseLocalTxtFile(file: File) {
        loadingView.visibility = View.VISIBLE
        textLoadingStatus.text = "引擎启动：智能分卷切分中..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cleanTitle = file.nameWithoutExtension.replace(Regex("""[^a-zA-Z0-9\u4e00-\u9fa5《》〈〉()（）\[\]【】"“”'‘’,.，。！？!?_\- ]"""), "").take(50).ifBlank { "本地导入书籍" }

                val existingDirs = filesDir.listFiles { f -> f.isDirectory && f.name.startsWith("book_") } ?: emptyArray()
                if (existingDirs.any { it.name.substringAfterLast("_") == cleanTitle }) {
                    withContext(Dispatchers.Main) { loadingView.visibility = View.GONE; Toast.makeText(this@MainActivity, "《${cleanTitle}》已在书架中", Toast.LENGTH_SHORT).show() }
                    return@launch
                }

                val bookDir = File(filesDir, "book_${System.currentTimeMillis()}_$cleanTitle")
                bookDir.mkdirs()

                val chapters = mutableListOf<Chapter>()
                var currentTitle = "序章 / 开篇"
                var currentContent = StringBuilder()
                val regex = Regex("^\\s*第[0-9零一二三四五六七八九十百千万]+[章卷节回].*")

                var chapterIndex = 0
                var partCount = 1 // 用于 3000 字强制切分的子序号

                file.useLines(Charsets.UTF_8) { lines ->
                    for (line in lines) {
                        val trimmed = line.trim()

                        // 情况 A：遇到新的章节
                        if (regex.matches(trimmed) && trimmed.length < 40) {
                            if (currentContent.isNotBlank()) {
                                val titleToSave = if (partCount > 1) "$currentTitle ($partCount)" else currentTitle
                                File(bookDir, "chap_${chapterIndex}.txt").writeText(currentContent.toString())
                                chapters.add(Chapter(title = titleToSave, content = ""))
                                chapterIndex++
                            }
                            currentTitle = trimmed
                            currentContent = StringBuilder()
                            partCount = 1
                        } else {
                            if (trimmed.isNotEmpty()) currentContent.append(trimmed).append("\n\n")

                            // 情况 B：如果字数膨胀超过 3000 字，切断
                            if (currentContent.length >= 3000) {
                                val titleToSave = if (partCount == 1) currentTitle else "$currentTitle ($partCount)"
                                File(bookDir, "chap_${chapterIndex}.txt").writeText(currentContent.toString())
                                chapters.add(Chapter(title = titleToSave, content = ""))
                                chapterIndex++
                                partCount++
                                currentContent = StringBuilder()
                            }
                        }
                    }
                }

                // 收尾最后一块
                if (currentContent.isNotBlank()) {
                    val titleToSave = if (partCount > 1) "$currentTitle ($partCount)" else currentTitle
                    File(bookDir, "chap_${chapterIndex}.txt").writeText(currentContent.toString())
                    chapters.add(Chapter(title = titleToSave, content = ""))
                }

                File(bookDir, "meta.json").writeText(Gson().toJson(chapters))

                withContext(Dispatchers.Main) {
                    loadingView.visibility = View.GONE
                    localImportContainer.visibility = View.GONE
                    libraryContainer.visibility = View.VISIBLE
                    Toast.makeText(this@MainActivity, "完美导入：$cleanTitle", Toast.LENGTH_SHORT).show()
                    refreshLibrary()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingView.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "解析失败，请确保 TXT 为 UTF-8 编码", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ================= 其他核心功能 =================
    private fun setupDirectoryAdapter(chapters: List<Chapter>) {
        directoryView.adapter = ChapterAdapter(chapters,
            onItemClick = { chapter -> loadChapter(chapters.indexOf(chapter), false); directoryContainer.visibility = View.GONE; readingView.visibility = View.VISIBLE },
            onItemLongClick = { menuView.visibility = View.VISIBLE }
        )
        bottomSeekBar.max = if (chapters.isNotEmpty()) chapters.size - 1 else 0
    }

    private fun addBookmarkForCurrentPosition() {
        if (currentNovelDir == null || chaptersList.isEmpty() || currentChapterIndex == -1) return
        val scrollY = readingScrollView.scrollY
        val layout = textContent.layout
        var snippet = chaptersList[currentChapterIndex].title
        if (layout != null) {
            try {
                val line = layout.getLineForVertical(scrollY)
                val startOffset = layout.getLineStart(line)
                val endOffset = kotlin.math.min(textContent.text.length, startOffset + 40)
                snippet = textContent.text.substring(startOffset, endOffset).replace("\n", " ").trim() + "..."
            } catch (e: Exception) {}
        }
        val newBookmark = Bookmark(UUID.randomUUID().toString(), currentChapterIndex, scrollY, chaptersList[currentChapterIndex].title, snippet, System.currentTimeMillis())
        val prefs = getPreferences(MODE_PRIVATE)
        val key = "bookmarks_${currentNovelDir!!.name}"
        val existingJson = prefs.getString(key, "[]")
        val bookmarks = Gson().fromJson<MutableList<Bookmark>>(existingJson, object : TypeToken<MutableList<Bookmark>>() {}.type)
        bookmarks.add(0, newBookmark)
        prefs.edit().putString(key, Gson().toJson(bookmarks)).apply()
        Toast.makeText(this, "书签添加成功", Toast.LENGTH_SHORT).show()
    }

    private fun showBookmarkManager() {
        if (currentNovelDir == null) return
        val prefs = getPreferences(MODE_PRIVATE)
        val key = "bookmarks_${currentNovelDir!!.name}"
        val json = prefs.getString(key, "[]")
        val bookmarks = Gson().fromJson<List<Bookmark>>(json, object : TypeToken<List<Bookmark>>() {}.type)
        bookmarkAdapter.updateData(bookmarks)
        if (bookmarks.isEmpty()) { bookmarkView.visibility = View.GONE; textNoBookmarks.visibility = View.VISIBLE }
        else { bookmarkView.visibility = View.VISIBLE; textNoBookmarks.visibility = View.GONE }
        bookmarkListContainer.visibility = View.VISIBLE
    }

    private fun showBookmarkEditDialog(bookmark: Bookmark) {
        val editText = EditText(this).apply { setText(bookmark.snippet); setTextColor(Color.WHITE); setHint("输入备注..."); setHintTextColor(Color.GRAY) }
        AlertDialog.Builder(this).setTitle("管理书签").setView(editText)
            .setPositiveButton("保存备注") { _, _ -> updateOrDeleteBookmark(bookmark, editText.text.toString(), false) }
            .setNegativeButton("删除书签") { _, _ -> updateOrDeleteBookmark(bookmark, "", true) }
            .show()
    }

    private fun updateOrDeleteBookmark(bookmark: Bookmark, newSnippet: String, isDelete: Boolean) {
        val prefs = getPreferences(MODE_PRIVATE)
        val key = "bookmarks_${currentNovelDir!!.name}"
        val json = prefs.getString(key, "[]")
        val bookmarks = Gson().fromJson<MutableList<Bookmark>>(json, object : TypeToken<MutableList<Bookmark>>() {}.type)
        if (isDelete) { bookmarks.removeAll { it.id == bookmark.id }; Toast.makeText(this, "书签已删除", Toast.LENGTH_SHORT).show() }
        else { bookmarks.find { it.id == bookmark.id }?.snippet = newSnippet; Toast.makeText(this, "备注已保存", Toast.LENGTH_SHORT).show() }
        prefs.edit().putString(key, Gson().toJson(bookmarks)).apply()
        showBookmarkManager()
    }

    private fun loadChapter(index: Int, scrollToBottom: Boolean = false, targetScrollY: Int = 0) {
        if (index < 0 || index >= chaptersList.size) return
        currentChapterIndex = index
        val chapter = chaptersList[index]
        currentNovelDir?.let { getPreferences(MODE_PRIVATE).edit().putInt(it.name, index).apply() }

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        textTime.text = timeFormat.format(Date())
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { this.registerReceiver(null, it) }
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        textBattery.text = "${if (level != -1 && scale != -1) level * 100 / scale else 0}%"

        val singleChapterContent = try { File(currentNovelDir, "chap_$index.txt").readText() } catch (e: Exception) { "章节内容读取失败" }
        textContent.text = "【${chapter.title}】\n\n$singleChapterContent"
        if (targetScrollY > 0) getPreferences(MODE_PRIVATE).edit().putInt("${currentNovelDir!!.name}_scrollY", targetScrollY).apply()

        readingScrollView.post {
            when {
                targetScrollY > 0 -> readingScrollView.scrollTo(0, targetScrollY)
                scrollToBottom -> readingScrollView.fullScroll(View.FOCUS_DOWN)
                else -> readingScrollView.scrollTo(0, 0)
            }
        }
    }

    private fun saveReadingProgress() {
        if (currentNovelDir != null && readingView.visibility == View.VISIBLE) {
            getPreferences(MODE_PRIVATE).edit()
                .putInt(currentNovelDir!!.name, currentChapterIndex)
                .putInt("${currentNovelDir!!.name}_scrollY", readingScrollView.scrollY)
                .apply()
        }
    }

    private fun refreshLibrary() {
        val savedDirs = filesDir.listFiles { f -> f.isDirectory && f.name.startsWith("book_") }?.toList() ?: emptyList()

        // 控制书架空状态 UI
        if (savedDirs.isEmpty()) {
            textEmptyLibrary.visibility = View.VISIBLE
            libraryView.visibility = View.GONE
        } else {
            textEmptyLibrary.visibility = View.GONE
            libraryView.visibility = View.VISIBLE
        }

        libraryView.adapter = LibraryAdapter(savedDirs) { clickedDir ->
            val bookName = clickedDir.name.substringAfterLast("_")
            AlertDialog.Builder(this).setTitle("《$bookName》")
                .setItems(arrayOf("继续阅读", "查看目录", "彻底删除")) { _, which ->
                    when (which) {
                        0 -> {
                            loadingView.visibility = View.VISIBLE
                            textLoadingStatus.text = "马上就好..."
                            lifecycleScope.launch {
                                chaptersList = withContext(Dispatchers.Default) {
                                    val metaFile = File(clickedDir, "meta.json")
                                    Gson().fromJson(metaFile.readText(), object : TypeToken<List<Chapter>>() {}.type)
                                }
                                currentNovelDir = clickedDir
                                withContext(Dispatchers.Main) {
                                    setupDirectoryAdapter(chaptersList)
                                    val prefs = getPreferences(MODE_PRIVATE)
                                    val lastReadIndex = prefs.getInt(clickedDir.name, 0)
                                    val lastScrollY = prefs.getInt("${clickedDir.name}_scrollY", 0)
                                    loadChapter(lastReadIndex, false, lastScrollY)
                                    libraryContainer.visibility = View.GONE
                                    readingView.visibility = View.VISIBLE
                                }
                                loadingView.visibility = View.GONE
                            }
                        }
                        1 -> {
                            loadingView.visibility = View.VISIBLE
                            textLoadingStatus.text = "正在提取目录..."
                            lifecycleScope.launch {
                                chaptersList = withContext(Dispatchers.Default) {
                                    val metaFile = File(clickedDir, "meta.json")
                                    Gson().fromJson(metaFile.readText(), object : TypeToken<List<Chapter>>() {}.type)
                                }
                                currentNovelDir = clickedDir
                                withContext(Dispatchers.Main) {
                                    setupDirectoryAdapter(chaptersList)
                                    libraryContainer.visibility = View.GONE
                                    directoryContainer.visibility = View.VISIBLE
                                }
                                loadingView.visibility = View.GONE
                            }
                        }
                        2 -> {
                            val dialog = AlertDialog.Builder(this@MainActivity).setTitle("⚠️ 删除警告").setMessage("确定删除《$bookName》及所有进度与书签吗？")
                                .setPositiveButton("删除 (3s)") { _, _ ->
                                    if (clickedDir.deleteRecursively()) {
                                        getPreferences(MODE_PRIVATE).edit().remove(clickedDir.name).remove("bookmarks_${clickedDir.name}").apply()
                                        Toast.makeText(this@MainActivity, "已清理空间", Toast.LENGTH_SHORT).show()
                                        refreshLibrary()
                                    }
                                }.setNegativeButton("取消", null).create()
                            dialog.show()
                            val posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                            posBtn.isEnabled = false; posBtn.setTextColor(Color.GRAY)
                            lifecycleScope.launch {
                                for (i in 2 downTo 1) { delay(1000); if (!dialog.isShowing) return@launch; posBtn.text = "删除 (${i}s)" }
                                delay(1000); if (!dialog.isShowing) return@launch
                                posBtn.text = "彻底删除"; posBtn.isEnabled = true; posBtn.setTextColor(Color.parseColor("#FF3B30"))
                            }
                        }
                    }
                }.show()
        }
    }

    override fun onPause() {
        super.onPause()
        saveReadingProgress()
    }

    private fun applyDisplaySettings() {
        textContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentFontSize)
        val layoutParams = window.attributes
        layoutParams.screenBrightness = if (brightLevel == 0) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else brightLevel * 0.2f
        window.attributes = layoutParams
        updateDisplayUI()
    }
    private fun updateDisplayUI() { if (::tvFontSize.isInitialized) { tvFontSize.text = "字号: ${currentFontSize.toInt()}"; tvBrightLevel.text = if (brightLevel == 0) "亮度: 自动" else "亮度: ${brightLevel * 20}%" } }
    private fun updateAutoScrollUI() { if (::btnToggleAutoScroll.isInitialized) { btnToggleAutoScroll.text = if (isAutoScroll) "开关：开" else "开关：关"; btnToggleAutoScroll.backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isAutoScroll) "#E91E63" else "#444444")); btnToggleSpeed.text = when(scrollSpeed) { 0 -> "速度：慢"; 1 -> "速度：中"; else -> "速度：快" } } }

    private fun startAutoScroll() {
        autoScrollJob?.cancel()
        if (!isAutoScroll) return
        autoScrollJob = lifecycleScope.launch {
            while (isActive) {
                delay(when (scrollSpeed) { 0 -> 55L; 1 -> 30L; else -> 15L })
                if (isUserTouching) continue
                if (readingView.visibility == View.VISIBLE && menuView.visibility == View.GONE && prefsView.visibility == View.GONE && autoScrollPrefsView.visibility == View.GONE && displayPrefsView.visibility == View.GONE && bookmarkListContainer.visibility == View.GONE) {
                    if (readingScrollView.isVerticalScrollBarEnabled) readingScrollView.isVerticalScrollBarEnabled = false
                    if (readingScrollView.canScrollVertically(1)) readingScrollView.scrollBy(0, 1) else { delay(2500); if (readingView.visibility == View.VISIBLE && !readingScrollView.canScrollVertically(1) && !isUserTouching) { loadChapter(currentChapterIndex + 1, false); delay(1500) } }
                }
            }
        }
    }

    private fun fetchNovelFromServer(targetIp: String, isSilent: Boolean = false) {
        if (!isSilent) {
            loadingView.visibility = View.VISIBLE
            textLoadingStatus.text = "正在下载数据..."
        }
        lifecycleScope.launch {
            try {
                // 1. 网络下载 JSON
                val jsonString = withContext(Dispatchers.IO) {
                    val url = URL("http://$targetIp:8080")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 20000
                    connection.doInput = true
                    connection.useCaches = false

                    if (connection.responseCode == 200) {
                        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } else {
                        throw Exception("服务器返回异常代码: ${connection.responseCode}")
                    }
                }

                if (jsonString.isBlank()) throw Exception("接收到空数据")

                // 2. 解析
                if (!isSilent) withContext(Dispatchers.Main) {
                    textLoadingStatus.text = "正在接收排版..."
                }

                val payload = withContext(Dispatchers.Default) {
                    Gson().fromJson(jsonString, NovelPayload::class.java)
                }

                if (payload.chapters.isEmpty()) {
                    throw Exception("书籍章节为空，请检查手机端文件")
                }

                // 3. 清洗书名（与本地导入逻辑相同）
                val cleanTitle = payload.bookName
                    .replace(Regex("""[^a-zA-Z0-9\u4e00-\u9fa5《》〈〉()（）\[\]【】"“”'‘’,.，。！？!?_\- ]"""), "")
                    .take(50)
                    .ifBlank { "无线导入书籍" }

                // 4. 查重
                val existingDirs = filesDir.listFiles { f -> f.isDirectory && f.name.startsWith("book_") } ?: emptyArray()
                if (existingDirs.any { it.name.substringAfterLast("_") == cleanTitle }) {
                    withContext(Dispatchers.Main) {
                        loadingView.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "《${cleanTitle}》已在书架中", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 5. 创建书籍目录，写入章节和元数据
                val bookDir = File(filesDir, "book_${System.currentTimeMillis()}_$cleanTitle")
                bookDir.mkdirs()

                withContext(Dispatchers.IO) {
                    payload.chapters.forEachIndexed { index, chapter ->
                        val file = File(bookDir, "chap_$index.txt")
                        file.writeText(chapter.content)  // 字段名匹配你的 Chapter 类
                    }
                    // 保存目录信息
                    val metaJson = Gson().toJson(payload.chapters)
                    File(bookDir, "meta.json").writeText(metaJson)
                }

                // 6. 完成：关闭加载界面，提示并刷新书架
                withContext(Dispatchers.Main) {
                    loadingView.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "《${cleanTitle}》导入成功", Toast.LENGTH_SHORT).show()
                    // 返回书架并刷新
                    libraryContainer.visibility = View.VISIBLE
                    refreshLibrary()
                }

            } catch (e: Exception) {
                // 异常处理：关闭加载界面并提示
                withContext(Dispatchers.Main) {
                    loadingView.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "传输失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}