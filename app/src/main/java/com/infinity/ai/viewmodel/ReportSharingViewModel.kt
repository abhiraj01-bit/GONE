package com.infinity.ai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infinity.ai.data.ReportSharingPreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReportSharingViewModel(app: Application) : AndroidViewModel(app) {

    private val pref = ReportSharingPreference(app)

    val sharingEnabled = pref.sharingEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    val sharingEmail = pref.sharingEmail.stateIn(
        viewModelScope, SharingStarted.Eagerly, ""
    )

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { pref.setSharingEnabled(enabled) }
    }

    fun setEmail(email: String) {
        viewModelScope.launch { pref.setSharingEmail(email) }
    }
}
