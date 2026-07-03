package com.shenyue.changyemobiletxt

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.View

class MainActivity : AppCompatActivity() {

    private lateinit var tvFileStatus: TextView
    // 将 novelServer 改为 lateinit var 并直接初始化
    private lateinit var novelServer: NovelServer

    // 获取手机在局域网里的 IP 地址
    private fun getLocalIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        return if (ipAddress == 0) {
            "未连接WiFi或获取IP失败"
        } else {
            String.format(
                "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )
        }
    }

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch {
                tvFileStatus.text = "正在疯狂解包解析中，请稍候..."
                try {
                    // 提取文件名
                    var fileName = "未知小说"
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex != -1) {
                            fileName = cursor.getString(nameIndex).substringBeforeLast(".")
                        }
                    }

                    val chapters = EpubParser.extractChapters(this@MainActivity, uri)

                    withContext(Dispatchers.Main) {
                        tvFileStatus.text = "解析完毕，正在打包..."
                    }

                    // 打包数据
                    val payload = mapOf("bookName" to fileName, "chapters" to chapters)
                    val jsonString = Gson().toJson(payload)

                    // 设置给服务器，并确保服务器已启动
                    novelServer.currentNovelJson = jsonString
                    if (!novelServer.isAlive) {
                        novelServer.start()
                    }

                    val ip = getLocalIpAddress()
                    withContext(Dispatchers.Main) {
                        tvFileStatus.text = """
                            🎉 解析并挂载成功！
                            书名：《$fileName》
                            共提取 ${chapters.size} 章
                            
                            请确保手表和手机在同一WiFi下
                            手表稍后将自动连接下载：
                            http://$ip:8080
                        """.trimIndent()
                    }

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvFileStatus.text = "发生错误: ${e.message}"
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化并启动 HTTP 服务器（可常驻后台，资源占用极低）
        novelServer = NovelServer(8080)
        novelServer.start()

        // 绑定所有 View
        val homeView = findViewById<View>(R.id.home_view)
        val transferView = findViewById<View>(R.id.transfer_view)
        val aboutView = findViewById<View>(R.id.about_view)

        // 局域网传书按钮
        findViewById<View>(R.id.btn_home_transfer).setOnClickListener {
            homeView.visibility = View.GONE
            transferView.visibility = View.VISIBLE
        }

        // 关于应用按钮
        findViewById<View>(R.id.btn_home_about).setOnClickListener {
            homeView.visibility = View.GONE
            aboutView.visibility = View.VISIBLE
        }

        // 绑定选择电子书按钮
        tvFileStatus = findViewById(R.id.tv_file_status)
        findViewById<Button>(R.id.btn_select_file).setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        // 处理物理返回键（大概率无效）
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (transferView.visibility == View.VISIBLE) {
                    transferView.visibility = View.GONE
                    homeView.visibility = View.VISIBLE
                } else if (aboutView.visibility == View.VISIBLE) {
                    aboutView.visibility = View.GONE
                    homeView.visibility = View.VISIBLE
                } else {
                    finish()
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        // 退出时关闭服务器
        novelServer.stop()
    }
}