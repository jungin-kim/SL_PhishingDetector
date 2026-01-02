package com.example.phishingdetector.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.phishingdetector.ml.ClientModelLoader
import com.example.phishingdetector.ml.OfflineModelLoader
import com.example.phishingdetector.repository.CacheRepository
import com.example.phishingdetector.repository.DomainRepository
import com.example.phishingdetector.utils.HtmlPreprocessor
import com.example.phishingdetector.utils.NetworkUtils
import com.example.phishingdetector.utils.Tokenizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor

class MainViewModel(application: Application) : AndroidViewModel(application) {

    data class Prediction(val isPhishing: Boolean, val score: Float)

    val predictionResult = MutableLiveData<Prediction?>()
    val errorMessage = MutableLiveData<String?>()

    private var clientModule: Module? = null
    private val offlineModule by lazy { OfflineModelLoader.load(application) }
    private val cacheRepo = CacheRepository.getInstance(application)
    private val domainRepo = DomainRepository.getInstance(application)
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val httpClient = OkHttpClient()

    // ===== Server endpoints (에뮬레이터 → 호스트PC) =====
    private val BASE = "http://10.0.2.2:5000"
    private val URL_TOKENIZE = "$BASE/tokenize"
    private val URL_PREDICT  = "$BASE/predict/"

    // ===== Model constants =====
    private val SEQ_LEN = 128               // padding/truncation 길이 (client/server와 동일해야 함)

    init {
        // ClientModel 다운로드 및 로드
        ClientModelLoader.load(
            context = application,
            onProgress = { _ -> },
            onStatusUpdate = { _ -> },
            onLoaded = { module -> clientModule = module },
            onError = { e -> errorMessage.postValue("모델 로드 실패: ${e.message}") }
        )
    }

    fun onUrlDetected(url: String) {
        ioScope.launch {
            try {
                val domain = extractDomain(url)
                if (domainRepo.isDomainPhishing(domain)) {
                    predictionResult.postValue(Prediction(true, 0.99f))
                    return@launch
                }

                val cached = cacheRepo.getValidCache(url)
                if (cached != null) {
                    predictionResult.postValue(Prediction(cached.isPhishing, cached.score))
                    return@launch
                }

                if (!NetworkUtils.isOnline(getApplication())) {
                    // 오프라인 경로: 기존 로컬 토크나이저/오프라인 모델 사용
                    val tokenData = Tokenizer.tokenize(url)
                    val isPhishingOffline = runOfflineModel(tokenData.first, tokenData.second)
                    val scoreOffline = if (isPhishingOffline) 0.6f else 0.4f
                    predictionResult.postValue(Prediction(isPhishingOffline, scoreOffline))
                } else {
                    val html = fetchHtmlContent(url)
                    if (html == null) {
                        errorMessage.postValue("웹페이지를 불러오는 데 실패했습니다.")
                        return@launch
                    }
                    val cleanedText = HtmlPreprocessor.cleanHtml(html)

                    val module = clientModule
                    if (module == null) {
                        errorMessage.postValue("Client 모델이 아직 로드되지 않았습니다.")
                        return@launch
                    }

                    // ===== 서버 토크나이즈 → ids/mask/vocab_size =====
                    val (idsFixed, maskFixed) = tokenizeOnServer(cleanedText) ?: run {
                        errorMessage.postValue("토크나이즈 실패")
                        return@launch
                    }

                    // ===== client forward → smashed =====
                    val smashedData = runClientModel(module, idsFixed, maskFixed)

                    // ===== 서버 예측 =====
                    val (isPhishing, score) = callPredictionApi(smashedData)
                    cacheRepo.saveCache(url, isPhishing, score)
                    predictionResult.postValue(Prediction(isPhishing, score))
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error onUrlDetected", e)
                errorMessage.postValue("알 수 없는 오류가 발생했습니다.")
            }
        }
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = java.net.URI(url)
            uri.host ?: url
        } catch (_: Exception) {
            url
        }
    }

    // ===== 서버 토크나이즈 =====
    private fun tokenizeOnServer(text: String): Pair<LongArray, LongArray>? {
        return try {
            val reqJson = JSONObject().apply { put("text", text) }
            val body = reqJson.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(URL_TOKENIZE)
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e("MainViewModel", "❌ /tokenize 실패: ${resp.code}")
                    return null
                }
                val obj = JSONObject(resp.body!!.string())
                val idsArr  = obj.getJSONArray("input_ids")
                val maskArr = obj.getJSONArray("attention_mask")
                val vocabSize = obj.optInt("vocab_size", 32128) // 서버가 주는 vocab_size 사용 (fallback 32128)

                // JSON -> LongArray
                val ids  = LongArray(idsArr.length())  { i -> idsArr.getInt(i).toLong() }
                val mask = LongArray(maskArr.length()) { i -> maskArr.getInt(i).toLong() }

                // 길이 고정 (pad/trim)
                val idsFixed  = LongArray(SEQ_LEN) { 0L }
                val maskFixed = LongArray(SEQ_LEN) { 0L }
                val copyLen = minOf(SEQ_LEN, ids.size)
                for (i in 0 until copyLen) {
                    idsFixed[i]  = ids[i]
                    maskFixed[i] = mask[i]
                }

                // 디버그 로그 (필수)
                val minId = idsFixed.minOrNull()
                val maxId = idsFixed.maxOrNull()
                Log.d("TOK", "ids len=${ids.size} min=$minId max=$maxId vocabSize=$vocabSize")

                // 범위 밖 값 클램프
                var outOfRange = false
                val limit = vocabSize.toLong()
                for (i in idsFixed.indices) {
                    if (idsFixed[i] < 0L || idsFixed[i] >= limit) {
                        outOfRange = true
                        idsFixed[i] = when {
                            idsFixed[i] < 0L -> 0L
                            else -> (limit - 1)
                        }
                    }
                }
                if (outOfRange) {
                    Log.w("TOK", "Some token ids were out of range and clamped to [0, ${limit - 1}]")
                }

                Pair(idsFixed, maskFixed)
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "tokenizeOnServer failed", e)
            null
        }
    }

    // ===== 오프라인 모델 실행 (기존 경로 유지) =====
    private fun runOfflineModel(inputIds: LongArray, attentionMask: LongArray): Boolean {
        val tensorIds = Tensor.fromBlob(inputIds, longArrayOf(1, inputIds.size.toLong()))
        val tensorMask = Tensor.fromBlob(attentionMask, longArrayOf(1, attentionMask.size.toLong()))
        val output = offlineModule.forward(IValue.from(tensorIds), IValue.from(tensorMask)).toTensor()
        val scores = output.dataAsFloatArray
        val maxIdx = scores.withIndex().maxByOrNull { it.value }?.index ?: 0
        return (maxIdx == 1)
    }

    // ===== 클라이언트 파트 모델 실행 =====
    private fun runClientModel(module: Module, ids: LongArray, mask: LongArray): FloatArray {
        val tensorIds = Tensor.fromBlob(ids, longArrayOf(1, ids.size.toLong()))
        val tensorMask = Tensor.fromBlob(mask, longArrayOf(1, mask.size.toLong()))
        return try {
            val out = module.forward(IValue.from(tensorIds), IValue.from(tensorMask)).toTensor()
            val smashed = out.dataAsFloatArray
            Log.d("TOK", "smashed length=${smashed.size}") // 보통 128*512 = 65536
            smashed
        } catch (t: Throwable) {
            Log.e("TOK", "forward() failed", t)
            throw t
        }
    }

    // ===== 서버 예측 API 호출 =====
    private fun callPredictionApi(smashedData: FloatArray): Pair<Boolean, Float> {
        val json = JSONObject().apply {
            put("smashed_data", JSONArray(smashedData.toList()))
        }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(URL_PREDICT)
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("서버 예측 오류: ${response.code}")
            val respJson = JSONObject(response.body!!.string())
            val score = respJson.getDouble("phishing_probability").toFloat()
            val isPhishing = respJson.getBoolean("is_phishing")
            return Pair(isPhishing, score)
        }
    }

    // ===== HTML 가져오기 =====
    private fun fetchHtmlContent(url: String): String? {
        try {
            Log.d("MainViewModel", "요청 URL: $url")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android)")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("MainViewModel", "❌ HTTP 실패 코드: ${response.code} / 메시지: ${response.message}")
                    return null
                }
                Log.i("MainViewModel", "✅ HTTP 성공: ${response.code}")
                return response.body?.string()
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "❌ 예외 발생: ${e.message}", e)
            return null
        }
    }
}
