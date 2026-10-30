package com.farhan.paisa

import android.app.Application
import com.farhan.paisa.data.PaisaDb
import com.farhan.paisa.data.Repository
import com.farhan.paisa.data.SettingsStore
import com.farhan.paisa.work.Reminders

class PaisaApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(PaisaDb.create(this), SettingsStore(this))
        Reminders.schedule(this)
    }
}
