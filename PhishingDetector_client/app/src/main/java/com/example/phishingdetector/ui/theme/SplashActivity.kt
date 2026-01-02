package com.example.phishingdetector.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.phishingdetector.R
import com.example.phishingdetector.ml.ClientModelLoader

class SplashActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)

        ClientModelLoader.load(
            context = this,
            onProgress = { percent ->
                runOnUiThread {
                    progressBar.progress = percent
                    statusText.text = "모델 다운로드 중입니다... ($percent%)"
                }
            },
            onStatusUpdate = { msg ->
                runOnUiThread {
                    statusText.text = msg
                }
            },
            onLoaded = {
                runOnUiThread {
                    statusText.text = "모델 준비 완료!"
                    progressBar.progress = 100
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            },
            onError = { e ->
                runOnUiThread {
                    Toast.makeText(this, "모델 로드 실패: ${e.message}", Toast.LENGTH_LONG).show()
                    finishAffinity()
                    System.exit(0)
                }
            }
        )
    }
}
