# 🌙 ChangyeTXT（长夜腕阅）

[English Version](README_EN.md) | [中文版](README.md)

![Release](https://img.shields.io/github/v/release/shenyue-ge/Changyetxt?color=success&label=Release)
![Platform](https://img.shields.io/badge/Platform-Wear%20OS%20%7C%20Android-blue)
![License](https://img.shields.io/badge/License-Copyright-lightgrey)

ChangyeTXT 是一个面向 Wear OS 的电子书阅读器，同时提供 Android 手机端用于局域网传书。

项目目前支持 EPUB、TXT 电子书格式，并针对手表设备的内存和性能限制进行了适配。

项目开发由Google Gemini领衔主演。

---

# Features

## Wear OS

* 按需读取电子书内容，减少大文件加载时的内存占用。
* 支持扫描并导入本地 TXT 文件。
* 支持识别常见章节标题进行分章；未分章的长文本会自动切分为较小的阅读单元。
* 自动保存阅读进度，下次打开时继续阅读。

## Android

* 支持通过局域网向手表发送 EPUB、TXT 等电子书文件。

---

# Requirements

* Wear OS 2.0 或更高版本
* Android 8.0 或更高版本

---

# Installation

1. 在仓库 **Releases** 页面下载最新版本。

* `ChangyeReader_WearOS_vX.X.X.apk`
* `ChangyeReader_Mobile_vX.X.X.apk`

2. 安装手机端 APK。

3. 使用 ADB 或其他 Wear OS 安装工具安装手表端 APK。

```bash
adb connect <watch-ip>:5555
adb install -r ChangyeReader_WearOS_vX.X.X.apk
```

安装完成后，在手机端与手表连接到同一局域网即可进行传书。

---

# Development

欢迎提交 Issue 或 Pull Request。

如果遇到 Bug 或有改进建议，也欢迎反馈。

作者QQ：7586380

---

# License

本项目遵循仓库中提供的许可证。
