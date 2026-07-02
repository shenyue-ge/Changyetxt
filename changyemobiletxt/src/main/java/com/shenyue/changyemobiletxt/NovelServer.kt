package com.shenyue.changyemobiletxt

// 你的 package ...

import fi.iki.elonen.NanoHTTPD

class NovelServer(port: Int = 8080) : NanoHTTPD(port) {

    // 用来存放我们要发送的小说 JSON 字符串
    var currentNovelJson: String = ""

    // 当手表访问手机 IP:8080 时，就会触发这个方法
    override fun serve(session: IHTTPSession): Response {
        // 如果小说还没准备好，返回提示
        if (currentNovelJson.isEmpty()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No novel data available.")
        }

        // 如果准备好了，就把 JSON 字符串作为网页内容返回给手表！
        // 加上跨域允许，防止一些网络拦截
        val response = newFixedLengthResponse(Response.Status.OK, "application/json", currentNovelJson)
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }
}