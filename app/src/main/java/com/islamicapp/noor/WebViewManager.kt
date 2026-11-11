package com.islamicapp.noor

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

object WebViewManager {

    const val BRIDGE_NAME = "AndroidBridge"

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView, bridge: WebAppBridge, activity: MainActivity) {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(bridge, BRIDGE_NAME)

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                // No native loading bar: the web app shows its own.
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                bridge.onPageReady()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    val event = JSONObject()
                    event.put("online", activity.isOnline())
                    event.put("url", request.url.toString())
                    EventBus.emitJson("network", event)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                return if (isTrusted(url)) {
                    false
                } else {
                    openExternally(activity, url)
                    true
                }
            }
        }

        webView.setDownloadListener(DownloadListener { url, _, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                request.setMimeType(mimeType)
                request.addRequestHeader("User-Agent", webView.settings.userAgentString)
                request.setTitle(Uri.parse(url).lastPathSegment ?: "Download")
                request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    Uri.parse(url).lastPathSegment ?: "download"
                )
                val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val id = manager.enqueue(request)
                val event = JSONObject()
                event.put("downloadId", id)
                event.put("url", url)
                event.put("mimeType", mimeType ?: "")
                EventBus.emitJson("download", event)
            } catch (e: Exception) {
                val event = JSONObject()
                event.put("error", e.message ?: "Download failed")
                EventBus.emitJson("download", event)
            }
        })
    }

    /** Only the configured production host is allowed inside the WebView. */
    fun isTrusted(url: String): Boolean {
        val host = Uri.parse(url).host ?: return false
        val appHost = Uri.parse(BuildConfig.WEB_APP_URL).host ?: return false
        if (host.equals(appHost, ignoreCase = true)) return true
        if (host.endsWith(".base44.app", ignoreCase = true)) return true
        return host.endsWith("." + appHost, ignoreCase = true)
    }

    fun openExternally(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (ignored: Exception) {
            // No browser available - nothing safe to do.
        }
    }
}
