package com.islamicapp.noor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/**
 * The app's only screen. There is no native interface at all: this activity is a
 * full-screen WebView and every feature - navigation, loading states, offline
 * banners, dialogs, the lock screen - is rendered by the web app. Native code
 * exists only behind bridge methods.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var bridge: WebAppBridge

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val payload = JSONObject()
        result.forEach { entry -> payload.put(entry.key, entry.value) }
        bridge.emitEvent("permission", payload)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)

        NoorApp.init(this)
        bridge = WebAppBridge(this, webView)
        EventBus.attach { type, data -> bridge.emitEvent(type, data) }
        WebViewManager.configure(webView, bridge, this)

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            val route = intent?.getStringExtra(EXTRA_ROUTE).orEmpty()
            webView.loadUrl(BuildConfig.WEB_APP_URL + route)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun requestPermission(kind: String) {
        val permissions = when (kind) {
            "notifications" -> arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            "location" -> arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            else -> emptyArray()
        }
        if (permissions.isEmpty()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && kind == "notifications") return
        permissionLauncher.launch(permissions)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    /** Widget taps deep-link into the customization studio. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val route = intent?.getStringExtra(EXTRA_ROUTE).orEmpty()
        if (route.isNotBlank()) webView.loadUrl(BuildConfig.WEB_APP_URL + route)
    }

    override fun onDestroy() {
        EventBus.detach()
        bridge.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ROUTE = "noor_route"
    }
}
