package com.example.phishingdetector

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.phishingdetector.ml.ClientModelLoader
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import org.pytorch.IValue
import org.pytorch.Tensor
import java.io.IOException

class MainActivity : AppCompatActivity() {

    // 에뮬레이터 → 호스트PC 고정 주소
    private val BASE = "http://10.0.2.2:5000"
    private val URL_TOKENIZE = "$BASE/tokenize"
    private val URL_UPLOAD_SMASH = "$BASE/predict/"   // ★ 통합 서버 기준

    // T5-small 기준
    private val SEQ_LEN = 128
    private val VOCAB_SIZE = 32128L   // 안전 범위 체크용(대략치)

    private val http = OkHttpClient()
    private lateinit var module: org.pytorch.Module

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etUrl = findViewById<EditText>(R.id.etUrl)
        val btnScan = findViewById<Button>(R.id.btnScan)
        val tvResult = findViewById<TextView>(R.id.tvResult)

        // 1) 클라이언트 파트 모델 로드 (앱 시작 시 1회)
        ClientModelLoader.load(
            context = this,
            onProgress = { /* optional */ },
            onStatusUpdate = { /* optional */ },
            onLoaded = { loaded ->
                module = loaded
                Log.i("ClientModel", "✅ Client model loaded")
            },
            onError = { e ->
                e.printStackTrace()
                tvResult.text = "⚠️ Failed to load client model."
            }
        )

        // 2) 버튼 클릭 → 서버 토크나이즈 → client forward → smashed 업로드
        btnScan.setOnClickListener {
            val urlText = etUrl.text.toString().trim()
            if (urlText.isEmpty()) {
                tvResult.text = "❗ Please enter a valid URL."
                return@setOnClickListener
            }
            if (!::module.isInitialized) {
                tvResult.text = "⏳ Client model is still loading..."
                return@setOnClickListener
            }
            tvResult.text = "🔄 Tokenizing on server..."
            requestTokensThenRun(urlText, tvResult)
        }
    }

    /** 서버에서 tokens 받아온 다음 client 모델로 smashed 생성 → 서버로 전송 */
    private fun requestTokensThenRun(text: String, tvResult: TextView) {
        val reqJson = JSONObject().apply { put("text", text) }
        val body = RequestBody.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
            reqJson.toString()
        )
        val req = Request.Builder().url(URL_TOKENIZE).post(body).build()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnUiThread { tvResult.text = "❌ /tokenize 실패" }
            }

            override fun onResponse(call: Call, response: Response) {
                val resp = response.body?.string()
                if (!response.isSuccessful || resp == null) {
                    runOnUiThread { tvResult.text = "⚠️ /tokenize 에러: ${response.code}" }
                    return
                }
                try {
                    val obj = JSONObject(resp)
                    val idsArr  = obj.getJSONArray("input_ids")
                    val maskArr = obj.getJSONArray("attention_mask")

                    // 1) JSON → LongArray
                    val ids  = LongArray(idsArr.length())  { i -> idsArr.getInt(i).toLong() }
                    val mask = LongArray(maskArr.length()) { i -> maskArr.getInt(i).toLong() }

                    // 2) 길이 고정: 128로 패딩/잘라내기
                    val idsFixed  = LongArray(SEQ_LEN) { 0L }
                    val maskFixed = LongArray(SEQ_LEN) { 0L }
                    val copyLen = minOf(SEQ_LEN, ids.size)
                    for (i in 0 until copyLen) {
                        idsFixed[i]  = ids[i]
                        maskFixed[i] = mask[i]
                    }

                    // 3) 값 범위 확인 + 디버그 로그
                    val minId = idsFixed.minOrNull()
                    val maxId = idsFixed.maxOrNull()
                    Log.d("TOK", "ids len=${ids.size} min=$minId max=$maxId") // ★ 필수 로그

                    val bad = idsFixed.firstOrNull { it < 0L || it >= VOCAB_SIZE }
                    if (bad != null) {
                        runOnUiThread {
                            tvResult.text = "❌ token id out of range: $bad (vocab<$VOCAB_SIZE)"
                        }
                        return
                    }

                    // 4) Tensor 생성 (정수 Long 텐서!)
                    val inputTensor = Tensor.fromBlob(idsFixed,  longArrayOf(1, SEQ_LEN.toLong()))
                    val attnTensor  = Tensor.fromBlob(maskFixed, longArrayOf(1, SEQ_LEN.toLong()))

                    // 5) client 모델 forward → smashed
                    val smashed = module
                        .forward(IValue.from(inputTensor), IValue.from(attnTensor))
                        .toTensor()
                        .dataAsFloatArray

                    Log.d("TOK", "smashed length=${smashed.size}") // ★ 길이 확인 로그 (보통 128*512=65536)

                    // 6) 서버로 업로드
                    runOnUiThread { tvResult.text = "📤 Uploading smashed data..." }
                    uploadSmashedData(smashed, tvResult)

                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread { tvResult.text = "⚠️ 토큰 파싱/인퍼런스 실패" }
                }
            }
        })
    }

    /** smashed data를 서버로 전송해 최종 분류 */
    private fun uploadSmashedData(smashedData: FloatArray, tvResult: TextView) {
        val json = JSONObject().apply {
            put("smashed_data", JSONArray(smashedData.toList()))
        }
        val body = RequestBody.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
            json.toString()
        )
        val req = Request.Builder().url(URL_UPLOAD_SMASH).post(body).build()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnUiThread { tvResult.text = "❌ Upload failed." }
            }

            override fun onResponse(call: Call, response: Response) {
                val resp = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        tvResult.text = "✅ Result: $resp"
                    } else {
                        tvResult.text = "⚠️ Server error: ${response.code}"
                    }
                }
            }
        })
    }
}
