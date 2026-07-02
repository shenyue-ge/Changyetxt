package com.shenyue.changyemobiletxt

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private lateinit var homeView: LinearLayout
    private lateinit var transferView: LinearLayout
    private lateinit var aboutView: LinearLayout

    private lateinit var textServerStatus: TextView
    private lateinit var btnSelectFile: Button

    // 文件选择器启动器
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            // 【核心逻辑接入点】：在这里调用你现有的 EpubParser 和启动服务器的代码
            textServerStatus.text = "正在解析文件并准备发送...\n请稍候..."

            // TODO: 调用你的 startServer(uri) 或相应的解析逻辑
            // 成功启动后，更新 textServerStatus.text = "服务器已就绪！\n请在手表端输入 IP: 192.168.x.x"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 绑定视图
        homeView = findViewById(R.id.home_view)
        transferView = findViewById(R.id.transfer_view)
        aboutView = findViewById(R.id.about_view)

        textServerStatus = findViewById(R.id.text_server_status)
        btnSelectFile = findViewById(R.id.btn_select_file)

        // 主页：点击进入局域网传书
        findViewById<View>(R.id.btn_home_transfer).setOnClickListener {
            homeView.visibility = View.GONE
            transferView.visibility = View.VISIBLE
        }

        // 主页：点击进入关于应用
        findViewById<View>(R.id.btn_home_about).setOnClickListener {
            homeView.visibility = View.GONE
            aboutView.visibility = View.VISIBLE
        }

        // 传书页：点击选择文件
        btnSelectFile.setOnClickListener {
            // 启动系统文件管理器，可以选择过滤 mime type
            filePickerLauncher.launch("*/*")
        }

        // 物理返回键全局拦截路由
        onBackPressedDispatcher.addCallback(this) {
            when {
                aboutView.visibility == View.VISIBLE -> {
                    aboutView.visibility = View.GONE
                    homeView.visibility = View.VISIBLE
                }
                transferView.visibility == View.VISIBLE -> {
                    // 如果有正在运行的服务器，最好在这里写一行关闭服务器的代码
                    // stopServer()
                    transferView.visibility = View.GONE
                    homeView.visibility = View.VISIBLE
                    textServerStatus.text = "等待选择文件..." // 重置状态
                }
                else -> {
                    finish() // 在主页按返回键则退出应用
                }
            }
        }
    }
}