package com.example.webbridge

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var notifier: NotificationHelper
    private lateinit var smsSender: SmsSender

    private var pendingNotification: Pair<String, String>? = null
    private var pendingSms: Pair<String, String>? = null

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val pending = pendingNotification
            pendingNotification = null
            if (granted && pending != null) {
                notifier.show(pending.first, pending.second)
            } else if (!granted) {
                showToast("اجازه‌ی نمایش نوتیفیکیشن داده نشد", true)
            }
        }

    private val smsPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val pending = pendingSms
            pendingSms = null
            if (pending != null) {
                if (granted) {
                    smsSender.send(pending.first, pending.second)
                } else {
                    notifySmsResult(pending.first, false, "اجازه‌ی ارسال پیامک داده نشد")
                }
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        notifier = NotificationHelper(applicationContext)
        smsSender = SmsSender(this).also { it.register() }

        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowContentAccess = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                // فقط صفحه‌های داخل assets اجازه‌ی لود دارند؛ بقیه بیرون از برنامه باز می‌شوند
                if (url.startsWith(ASSET_PREFIX)) return false
                openExternal(url)
                return true
            }
        }

        webView.addJavascriptInterface(JsBridge(this), "Android")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitOrBack()
            }
        })

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(HOME_URL)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        smsSender.unregister()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    // ---------------------------------------------------------------
    // توابعی که پل (JsBridge) صدا می‌زند — همه روی Main Thread
    // ---------------------------------------------------------------

    fun showToast(message: String, long: Boolean) {
        Toast.makeText(
            this,
            message,
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        ).show()
    }

    /** صفحه اصلی: خروج از برنامه — صفحه‌ی دیگر: برگشت به عقب */
    fun exitOrBack() {
        val path = (webView.url ?: "").substringBefore('#').substringBefore('?')
        val isHome = path.isEmpty() || path == HOME_URL
        when {
            isHome -> finish()
            webView.canGoBack() -> webView.goBack()
            else -> webView.loadUrl(HOME_URL)
        }
    }

    fun sendNotification(title: String, text: String) {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

        if (needsPermission) {
            pendingNotification = title to text
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notifier.show(title, text)
        }
    }

    fun sendSms(phone: String, text: String) {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            notifySmsResult(phone, false, "این دستگاه قابلیت ارسال پیامک ندارد")
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            smsSender.send(phone, text)
        } else {
            pendingSms = phone to text
            smsPermLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    /** نتیجه‌ی ارسال پیامک را به جاوااسکریپت برمی‌گرداند: window.onSmsResult(phone, ok, message) */
    fun notifySmsResult(phone: String, ok: Boolean, message: String) {
        val js = "window.onSmsResult && window.onSmsResult(" +
            "${JSONObject.quote(phone)}, $ok, ${JSONObject.quote(message)})"
        runOnUiThread { webView.evaluateJavascript(js, null) }
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            // برنامه‌ای برای باز کردن لینک نیست
        }
    }

    companion object {
        private const val ASSET_PREFIX = "file:///android_asset/"
        const val HOME_URL = "file:///android_asset/index.html"
    }
}
