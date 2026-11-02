package com.farhan.paisa

import android.app.Application
import com.farhan.paisa.data.PaisaDb
import com.farhan.paisa.data.Repository
import com.farhan.paisa.data.SettingsStore
import com.farhan.paisa.work.Reminders
import com.farhan.paisa.work.Watcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PaisaApp : Application() {
    lateinit var repository: Repository
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        repository = Repository(PaisaDb.create(this), SettingsStore(this))
        Reminders.schedule(this)
        Watcher.start(this, repository, scope)
    }
}
