package com.example.phishingdetector.sms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telephony.SmsMessage
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.phishingdetector.App
import com.example.phishingdetector.R
import com.example.phishingdetector.ui.MainActivity

/**
 * SMS 수신 시 호출되는 BroadcastReceiver.
 * 1) SMS 본문에서 URL 추출
 * 2) LiveData에 URL 전달 (앱이 켜져 있을 때화면 갱신용)
 * 3) Notification(알림) 생성: 백그라운드/포그라운드 여부 상관없이 팝업으로 알림
 */
object SmsLiveData {
    val latestUrl = androidx.lifecycle.MutableLiveData<String?>()
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle: Bundle? = intent.extras
            bundle?.let {
                val pdus = it["pdus"] as? Array<*>
                if (pdus != null) {
                    for (pdu in pdus) {
                        val format = it.getString("format")
                        val msg = SmsMessage.createFromPdu(pdu as ByteArray, format)
                        val body = msg.messageBody

                        // URL 정규식 매칭 (http 또는 https 로 시작)
                        val urlRegex = Regex("(https?://[\\w\\-_.?&=#/%]+)")
                        val match = urlRegex.find(body)
                        match?.value?.let { detectedUrl ->
                            // 1) LiveData에 전달 (앱이 켜져 있으면 화면 갱신)
                            SmsLiveData.latestUrl.postValue(detectedUrl)

                            // 2) Notification 생성 (백그라운드/포그라운드 관계없이)
                            showUrlNotification(context, detectedUrl)
                        }
                    }
                }
            }
        }
    }

    private fun showUrlNotification(context: Context, url: String) {
        // (1) 알림 클릭 시 MainActivity로 URL 전달
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("detected_url", url)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        // (2) Notification 빌드
        val builder = NotificationCompat.Builder(context, App.SMS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sms_notification)   // drawable 리소스 필요
            .setContentTitle("새 SMS 내 URL 감지됨")
            .setContentText(url)
            .setStyle(NotificationCompat.BigTextStyle().bigText(url))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // (3) Notification 표시
        with(NotificationManagerCompat.from(context)) {
            // notificationId는 고유하게 주거나, System.currentTimeMillis().toInt() 사용
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}
