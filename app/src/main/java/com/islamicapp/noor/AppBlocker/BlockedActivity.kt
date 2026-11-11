package com.islamicapp.noor.AppBlocker

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.islamicapp.noor.BuildConfig
import com.islamicapp.noor.MainActivity
import com.islamicapp.noor.Quran.ListeningTracker
import com.islamicapp.noor.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Shown over a blocked app. It is a WebView loading this app's own /lock page, so
 * the lock screen is web UI like everything else: no native layout, no native
 * dialog. The page drives it through window.NoorLock.
 */
class BlockedActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null
    private var blockedPackage = ""
    private var blockedLabel = ""

    inner class LockInterface {

        @JavascriptInterface
        fun unlock() {
            AppBlockingManager.stopSession(this@BlockedActivity, emergency = true)
            finish()
        }

        @JavascriptInterface
        fun openApp() {
            startActivity(
                Intent(this@BlockedActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
        }

        @JavascriptInterface
        fun remainingSeconds(): Long =
            ListeningTracker.snapshot(this@BlockedActivity).optLong("remainingSeconds", 0L)

        @JavascriptInterface
        fun blockedPackage(): String = blockedPackage

        @JavascriptInterface
        fun blockedLabel(): String = blockedLabel
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)

        webView = findViewById(R.id.lockWeb)

        blockedPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        blockedLabel = try {
            val info = packageManager.getApplicationInfo(blockedPackage, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            blockedPackage
        }

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(LockInterface(), "NoorLock")
        webView.loadUrl(
            BuildConfig.WEB_APP_URL + "/lock?pkg=" + Uri.encode(blockedPackage) +
                "&label=" + Uri.encode(blockedLabel)
        )

        // The session can end elsewhere (goal reached, emergency unlock, reboot).
        watcher = scope.launch {
            while (isActive) {
                val snapshot = ListeningTracker.snapshot(this@BlockedActivity)
                if (!snapshot.optBoolean("sessionActive", false)) {
                    finish()
                    break
                }
                delay(1000)
            }
        }
    }

    override fun onDestroy() {
        watcher?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PACKAGE = "blocked_package"
    }
}
