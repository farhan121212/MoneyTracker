package com.farhan.paisa.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.farhan.paisa.MainViewModel

/**
 * Returns a function that asks for SMS access and turns detection on. It starts from now, because
 * older payments may already be part of the balances you entered.
 */
@Composable
fun rememberSmsEnabler(vm: MainViewModel): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.all { it }) {
            val now = System.currentTimeMillis()
            vm.write { repo ->
                repo.settings.update { it.copy(smsEnabled = true, smsSince = now, smsPromptDismissed = true) }
            }
        } else {
            // Android can block SMS for apps installed outside the Play Store until it's allowed here.
            Toast.makeText(context, "Allow SMS for Paisa on this screen", Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        }
    }
    return { launcher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)) }
}
