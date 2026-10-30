package com.farhan.paisa

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.farhan.paisa.data.Ledger
import com.farhan.paisa.data.Repository
import com.farhan.paisa.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo: Repository = (app as PaisaApp).repository

    val ledger: StateFlow<Ledger?> = repo.ledger
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val settings: StateFlow<Settings?> = repo.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Runs a write without tying it to a screen, so leaving the screen cannot cancel a save. */
    fun write(block: suspend CoroutineScope.(Repository) -> Unit) {
        viewModelScope.launch { block(repo) }
    }
}
