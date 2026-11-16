package com.example.webbridge

import android.webkit.JavascriptInterface

/**
 * پل جاوااسکریپت ↔ کاتلین
 * در JS با نام Android در دسترس است، مثلا: Android.showToast("سلام")
 * متدهای JavascriptInterface روی ترد جدا اجرا می‌شوند، پس همه را به Main Thread می‌بریم.
 */
class JsBridge(private val activity: MainActivity) {

    @JavascriptInterface
    fun showToast(message: String?) {
        activity.runOnUiThread { activity.showToast(message.orEmpty(), false) }
    }

    @JavascriptInterface
    fun showLongToast(message: String?) {
        activity.runOnUiThread { activity.showToast(message.orEmpty(), true) }
    }

    @JavascriptInterface
    fun exitOrBack() {
        activity.runOnUiThread { activity.exitOrBack() }
    }

    @JavascriptInterface
    fun sendNotification(title: String?, text: String?) {
        activity.runOnUiThread {
            activity.sendNotification(title.orEmpty(), text.orEmpty())
        }
    }

    @JavascriptInterface
    fun sendSms(phone: String?, message: String?) {
        activity.runOnUiThread {
            val number = phone.orEmpty().filter { it.isDigit() || it == '+' }
            val body = message.orEmpty()
            if (number.isEmpty()) {
                activity.notifySmsResult(phone.orEmpty(), false, "شماره‌ی گیرنده خالی یا نامعتبر است")
            } else if (body.isBlank()) {
                activity.notifySmsResult(number, false, "متن پیامک خالی است")
            } else {
                activity.sendSms(number, body)
            }
        }
    }
}
