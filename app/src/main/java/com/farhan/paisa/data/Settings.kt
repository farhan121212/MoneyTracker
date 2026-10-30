package com.farhan.paisa.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val name: String = "",
    val onboarded: Boolean = false,
    val monthlyBudget: Long = 0,
    val savingsTarget: Long = 0,
    val lockEnabled: Boolean = false,
    val dailyReminder: Boolean = true,
    val dueReminder: Boolean = true,
    val smsEnabled: Boolean = false,
    /** Messages sent before this moment are left alone unless you ask for them. */
    val smsSince: Long = 0,
    val smsPromptDismissed: Boolean = false,
    /** Sender IDs you added yourself, such as NEWBNK, separated by spaces. */
    val extraSenders: String = "",
) {
    val extraSenderSet: Set<String>
        get() = extraSenders.uppercase().split(' ', ',').filter { it.isNotBlank() }.toSet()
}

private val Context.store by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val name = stringPreferencesKey("name")
        val onboarded = booleanPreferencesKey("onboarded")
        val budget = longPreferencesKey("monthlyBudget")
        val savings = longPreferencesKey("savingsTarget")
        val lock = booleanPreferencesKey("lockEnabled")
        val daily = booleanPreferencesKey("dailyReminder")
        val due = booleanPreferencesKey("dueReminder")
        val sms = booleanPreferencesKey("smsEnabled")
        val smsSince = longPreferencesKey("smsSince")
        val smsPrompt = booleanPreferencesKey("smsPromptDismissed")
        val senders = stringPreferencesKey("extraSenders")
    }

    val settings: Flow<Settings> = context.store.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun update(change: (Settings) -> Settings) {
        context.store.edit { prefs ->
            val next = change(prefs.toSettings())
            prefs[Keys.name] = next.name
            prefs[Keys.onboarded] = next.onboarded
            prefs[Keys.budget] = next.monthlyBudget
            prefs[Keys.savings] = next.savingsTarget
            prefs[Keys.lock] = next.lockEnabled
            prefs[Keys.daily] = next.dailyReminder
            prefs[Keys.due] = next.dueReminder
            prefs[Keys.sms] = next.smsEnabled
            prefs[Keys.smsSince] = next.smsSince
            prefs[Keys.smsPrompt] = next.smsPromptDismissed
            prefs[Keys.senders] = next.extraSenders
        }
    }

    private fun Preferences.toSettings() = Settings(
        name = this[Keys.name] ?: "",
        onboarded = this[Keys.onboarded] ?: false,
        monthlyBudget = this[Keys.budget] ?: 0,
        savingsTarget = this[Keys.savings] ?: 0,
        lockEnabled = this[Keys.lock] ?: false,
        dailyReminder = this[Keys.daily] ?: true,
        dueReminder = this[Keys.due] ?: true,
        smsEnabled = this[Keys.sms] ?: false,
        smsSince = this[Keys.smsSince] ?: 0,
        smsPromptDismissed = this[Keys.smsPrompt] ?: false,
        extraSenders = this[Keys.senders] ?: "",
    )
}
