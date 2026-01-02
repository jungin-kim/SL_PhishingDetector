package com.example.phishingdetector.ml

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

object ClientModelLoader {
    private const val TAG = "ClientModelLoader"
    private const val SERVER_URL = "http://10.0.2.2:8000/download_model"
    private const val MODEL_NAME = "client_part.ptl"
    private const val MAX_RETRIES = 2

    fun load(
        context: Context,
        onProgress: (Int) -> Unit,
        onStatusUpdate: (String) -> Unit,
        onLoaded: (Module) -> Unit,
        onError: (Exception) -> Unit
    ) {
        Log.i(TAG, "🧪 ClientModelLoader.load() 호출됨")

        Thread {
            var attempt = 1
            while (attempt <= MAX_RETRIES) {
                try {
                    val file = File(context.filesDir, MODEL_NAME)

                    if (!file.exists()) {
                        onStatusUpdate("모델 다운로드 준비 중...")
                        Log.i(TAG, "📥 모델 파일 없음 → 서버에서 다운로드 시도")
                        downloadModelFromServer(file, onProgress, onStatusUpdate)
                    }

                    onStatusUpdate("모델 검증 중입니다...")
                    Log.i(TAG, "📦 모델 파일 크기: ${file.length()} bytes")

                    val module = LiteModuleLoader.load(file.absolutePath)
                    Log.i(TAG, "✅ 모델 로드 성공")
                    onLoaded(module)
                    return@Thread

                } catch (e: Exception) {
                    Log.e(TAG, "❌ 모델 로드 실패 (시도 $attempt): ${e.message}", e)
                    val file = File(context.filesDir, MODEL_NAME)
                    if (file.exists()) file.delete()

                    if (attempt >= MAX_RETRIES) {
                        onError(e)
                        return@Thread
                    }
                    attempt++
                }
            }
        }.start()
    }

    private fun downloadModelFromServer(
        file: File,
        onProgress: (Int) -> Unit,
        onStatusUpdate: (String) -> Unit
    ) {
        onStatusUpdate("모델 다운로드 중입니다...")
        Log.i(TAG, "📲 [CLIENT] 서버에 모델 다운로드 요청 시작")

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(SERVER_URL).build()

        client.newCall(request).execute().use { response ->
            Log.i(TAG, "📲 [CLIENT] 응답 상태 코드: ${response.code}")

            if (!response.isSuccessful || response.body == null)
                throw IOException("HTTP 실패: ${response.code}")

            val contentLength = response.body!!.contentLength()
            var totalRead = 0L

            response.body!!.byteStream().use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        if (contentLength > 0) {
                            val percent = ((totalRead * 100) / contentLength).toInt()
                            onProgress(percent)
                        }
                    }

                    outputStream.flush()
                }
            }

            Thread.sleep(500)

            if (file.length() < 10_000) {
                Log.w(TAG, "⚠️ 모델 파일 크기 이상함 (${file.length()} bytes)")
                file.delete()
                throw IOException("모델 파일 크기 비정상. 다시 시도하세요.")
            }

            Log.i(TAG, "✅ [CLIENT] 모델 다운로드 완료 (${file.length()} bytes)")
        }
    }
}
