package com.remote.viewer

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object Uploader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun postJson(path: String, json: JSONObject) {
        try {
            val body = json.toString().toRequestBody("application/json".toMediaType())
            client.newCall(Request.Builder().url(MainActivity.APP_URL + path).post(body).build()).execute().close()
        } catch (e: Exception) {}
    }

    fun putBytes(path: String, bytes: ByteArray, filename: String) {
        try {
            val url = MainActivity.APP_URL + path + "?filename=" + URLEncoder.encode(filename, "UTF-8") + "&notify=0"
            val body = bytes.toRequestBody("application/octet-stream".toMediaType())
            client.newCall(Request.Builder().url(url).put(body).build()).execute().close()
        } catch (e: Exception) {}
    }

    fun putStream(path: String, stream: InputStream, mime: String, filename: String) {
        try {
            val url = MainActivity.APP_URL + path + "?filename=" + URLEncoder.encode(filename, "UTF-8") + "&notify=0"
            val body = object : RequestBody() {
                override fun contentType() = mime.toMediaType()
                override fun contentLength() = -1L
                override fun writeTo(sink: okio.BufferedSink) {
                    val buf = ByteArray(65536)
                    stream.use { input ->
                        var n = input.read(buf)
                        while (n >= 0) {
                            if (n > 0) sink.write(buf, 0, n)
                            n = input.read(buf)
                        }
                    }
                }
            }
            client.newCall(Request.Builder().url(url).put(body).build()).execute().close()
        } catch (e: Exception) {}
    }
}
