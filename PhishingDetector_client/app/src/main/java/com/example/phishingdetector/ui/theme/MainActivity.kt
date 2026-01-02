package com.example.phishingdetector.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.example.phishingdetector.R
import com.example.phishingdetector.sms.SmsLiveData

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var etUrl: EditText
    private lateinit var tvResult: TextView
    private lateinit var btnScan: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUrl = findViewById(R.id.etUrl)
        tvResult = findViewById(R.id.tvResult)
        btnScan = findViewById(R.id.btnScan)

        // 기존의 SMS LiveData 감지 기능 유지 (선택사항)
        SmsLiveData.latestUrl.observe(this, Observer { url ->
            url?.let {
                etUrl.setText(it)
                viewModel.onUrlDetected(it)
            }
        })

        viewModel.predictionResult.observe(this, Observer { result ->
            result?.let {
                val displayText = if (it.isPhishing) {
                    "⚠️ 피싱 사이트 감지됨 (확률: ${"%.0f".format(it.score * 100)}%)"
                } else {
                    "✅ 정상 사이트로 확인됨 (확률: ${"%.0f".format(it.score * 100)}%)"
                }
                tvResult.text = displayText
            }
        })

        viewModel.errorMessage.observe(this, Observer { msg ->
            msg?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        })

        btnScan.setOnClickListener {
            val urlToCheck = etUrl.text.toString().trim()
            if (urlToCheck.isEmpty()) {
                Toast.makeText(this, "URL을 입력하세요", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.onUrlDetected(urlToCheck)
            }
        }
    }
}
