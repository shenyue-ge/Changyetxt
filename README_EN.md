# 🌙 ChangyeTXT

[English Version](README_EN.md) | [中文版](README.md)

![Release](https://img.shields.io/github/v/release/shenyue-ge/Changyetxt?label=Release)
![Platform](https://img.shields.io/badge/Platform-Wear%20OS%20%7C%20Android-blue)
![License](https://img.shields.io/badge/License-Copyright-lightgrey)

ChangyeTXT is an e-book reader designed for Wear OS, with an Android companion app for LAN file transfer.

The project currently supports EPUB and TXT formats, with optimizations for the memory and performance limitations of smartwatch devices.

Developed by a human, with Google Gemini as the primary coding assistant.

---

# Features

## Wear OS

* On-demand file loading to reduce memory usage when reading large books.
* Scan and import local TXT files.
* Automatically detect common chapter titles. Books without chapters are split into smaller reading sections.
* Automatically save reading progress and restore it when reopening the app.

## Android

* Transfer EPUB, TXT, and other supported e-book files to the watch over a local network.

---

# Requirements

* Wear OS 2.0 or later
* Android 8.0 or later

---

# Installation

1. Download the latest release from the repository's **Releases** page.

   * `ChangyeReader_WearOS_vX.X.X.apk`
   * `ChangyeReader_Mobile_vX.X.X.apk`

2. Install the Android APK.

3. Install the Wear OS APK using ADB or another Wear OS installation tool.

```bash
adb connect <watch-ip>:5555
adb install -r ChangyeReader_WearOS_vX.X.X.apk
```

After installation, connect your phone and watch to the same local network to transfer books.

---

# Development

Issues and Pull Requests are welcome.

If you find a bug or have suggestions for improvement, feel free to open an Issue or contact the author.

QQ: 7586380

---

# License

This project is licensed under the license provided in this repository.
