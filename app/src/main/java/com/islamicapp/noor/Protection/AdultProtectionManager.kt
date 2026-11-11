package com.islamicapp.noor.Protection

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.islamicapp.noor.Storage.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Adult content protection through local DNS filtering.
 *
 * Honest limits (surfaced in the UI as well):
 * - only DNS names are filtered, never page content;
 * - apps that use their own DNS-over-HTTPS resolver, direct IP addresses or a
 *   different VPN bypass this filter;
 * - only one VPN can be active at a time on Android.
 */
object AdultProtectionManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        // Status is derived from the service flag and the saved preference.
    }

    fun enable(context: Context): JSONObject {
        val json = JSONObject()
        val consent = try {
            VpnService.prepare(context)
        } catch (e: Exception) {
            null
        }

        if (consent != null) {
            // Android needs the user to approve the VPN once. The web layer asks
            // again after the user returns, and this second call starts filtering.
            consent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(consent)
            } catch (ignored: Exception) {
            }
            json.put("enabled", false)
            json.put("needsConsent", true)
            json.put("note", "Approve the VPN request, then enable protection again.")
            return json
        }

        val intent = Intent(context, DnsVpnService::class.java)
        try {
            context.startService(intent)
            scope.launch { AppPreferences.setProtection(true) }
            json.put("enabled", true)
            json.put("needsConsent", false)
        } catch (e: Exception) {
            json.put("enabled", false)
            json.put("needsConsent", false)
            json.put("error", e.message ?: "Could not start protection")
        }
        return json
    }

    fun disable(context: Context): JSONObject {
        val intent = Intent(context, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_STOP
        }
        try {
            context.startService(intent)
        } catch (ignored: Exception) {
        }
        scope.launch { AppPreferences.setProtection(false) }
        val json = JSONObject()
        json.put("enabled", false)
        return json
    }

    fun status(context: Context): JSONObject {
        val json = JSONObject()
        json.put("enabled", AppPreferences.current().protectionEnabled)
        json.put("running", DnsVpnService.isRunning)
        json.put("mode", "local-dns-filter")
        json.put("upstream", DnsVpnService.UPSTREAM_DNS)
        json.put("privateDnsHost", "family.cloudflare-dns.com")
        json.put(
            "limits",
            "DNS filtering cannot block every adult app, embedded content, direct IP connections or alternate resolvers."
        )
        return json
    }
}
