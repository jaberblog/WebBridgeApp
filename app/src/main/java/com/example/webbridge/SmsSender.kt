package com.example.webbridge

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/**
 * ارسال پیامک با پشتیبانی از پیامک بلند (divideMessage + sendMultipartTextMessage)
 * وضعیت نهایی (همه‌ی بخش‌ها) به JS گزارش می‌شود.
 */
class SmsSender(private val activity: MainActivity) {

    private class Job(val phone: String, var remaining: Int, var error: String? = null)

    private val jobs = HashMap<Int, Job>()
    private var nextId = 1

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getIntExtra(EXTRA_ID, -1)
            val job = jobs[id] ?: return
            if (resultCode != Activity.RESULT_OK && job.error == null) {
                job.error = describe(resultCode)
            }
            job.remaining--
            if (job.remaining <= 0) {
                jobs.remove(id)
                val err = job.error
                activity.notifySmsResult(job.phone, err == null, err ?: "پیامک ارسال شد")
            }
        }
    }

    fun register() {
        ContextCompat.registerReceiver(
            activity, receiver, IntentFilter(ACTION_SENT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun unregister() {
        try {
            activity.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    fun send(phone: String, text: String) {
        try {
            val sms: SmsManager = if (Build.VERSION.SDK_INT >= 31) {
                activity.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = sms.divideMessage(text)
            val id = nextId++
            val sentIntents = ArrayList<PendingIntent>()
            for (i in parts.indices) {
                val intent = Intent(ACTION_SENT)
                    .setPackage(activity.packageName)
                    .putExtra(EXTRA_ID, id)
                // requestCode یکتا برای هر بخش، چون extra در یکسان‌بودن PendingIntent حساب نمی‌شود
                sentIntents.add(
                    PendingIntent.getBroadcast(
                        activity, id * 1000 + i, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }

            jobs[id] = Job(phone, parts.size)
            sms.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
        } catch (e: Exception) {
            activity.notifySmsResult(phone, false, "خطا در ارسال: ${e.message}")
        }
    }

    private fun describe(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "خطای عمومی در ارسال"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "آنتن‌دهی یا سرویس موجود نیست"
        SmsManager.RESULT_ERROR_NULL_PDU -> "خطای PDU"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "رادیو خاموش است (حالت پرواز؟)"
        else -> "ارسال ناموفق (کد $code)"
    }

    companion object {
        private const val ACTION_SENT = "com.example.webbridge.SMS_SENT"
        private const val EXTRA_ID = "job_id"
    }
}
