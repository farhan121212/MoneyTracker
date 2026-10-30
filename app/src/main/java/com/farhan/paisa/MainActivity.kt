package com.farhan.paisa

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.farhan.paisa.sms.SmsSync
import com.farhan.paisa.ui.PaisaRoot
import com.farhan.paisa.ui.theme.PaisaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()
    private val unlocked = mutableStateOf(false)
    private val openRequests = MutableStateFlow<String?>(null)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        val bars = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        readIntent(intent)
        setContent {
            PaisaTheme {
                PaisaRoot(
                    vm = vm,
                    unlocked = unlocked.value,
                    onUnlock = ::unlock,
                    openRequests = openRequests,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // A short trip away, such as to the file picker for a backup, doesn't ask to unlock again.
        if (SystemClock.elapsedRealtime() - stoppedAt > RELOCK_AFTER_MILLIS) unlocked.value = false
        lifecycleScope.launch { SmsSync.catchUp(applicationContext) }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    private fun readIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_ADD -> openRequests.value = ACTION_ADD
            ACTION_REVIEW -> openRequests.value = ACTION_REVIEW
        }
    }

    private fun unlock() {
        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            unlocked.value = true
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked.value = true
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Paisa")
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    companion object {
        const val ACTION_ADD = "com.farhan.paisa.ADD"
        const val ACTION_REVIEW = "com.farhan.paisa.REVIEW"
        private const val RELOCK_AFTER_MILLIS = 30_000L
    }
}
