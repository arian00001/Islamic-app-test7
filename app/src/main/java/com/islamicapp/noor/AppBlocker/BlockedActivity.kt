package com.islamicapp.noor.AppBlocker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

class BlockedActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)

        val blockedPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val label = try {
            val info = packageManager.getApplicationInfo(blockedPackage, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            blockedPackage
        }

        findViewById<TextView>(R.id.blockedApp).text = label

        findViewById<Button>(R.id.openQuran).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }

        findViewById<Button>(R.id.emergency).setOnClickListener { confirmEmergency() }

        ticker = scope.launch {
            while (isActive) {
                val snapshot = ListeningTracker.snapshot(this@BlockedActivity)
                val remaining = snapshot.optLong("remainingSeconds", 0L)
                val text = String.format("%02d:%02d", remaining / 60, remaining % 60)
                findViewById<TextView>(R.id.remaining).text = text
                if (!snapshot.optBoolean("sessionActive", false)) {
                    finish()
                }
                delay(1000)
            }
        }
    }

    private fun confirmEmergency() {
        AlertDialog.Builder(this)
            .setTitle("Emergency unlock")
            .setMessage(
                "This stops blocking for now so you can reach anything urgent. " +
                    "Your listening goal stays open and you can start again from the app."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Unlock") { _, _ ->
                AppBlockingManager.stopSession(this, emergency = true)
                finish()
            }
            .show()
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PACKAGE = "blocked_package"
    }
}
