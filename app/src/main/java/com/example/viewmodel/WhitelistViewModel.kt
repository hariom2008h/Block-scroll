package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.WhitelistRepository
import com.example.data.WhitelistedChannel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WhitelistViewModel(private val repository: WhitelistRepository) : ViewModel() {

    val uiState: StateFlow<List<WhitelistedChannel>> = repository.allChannels
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addChannel(channelName: String) {
        val trimmed = channelName.trim()
        if (trimmed.isNotEmpty()) {
            viewModelScope.launch {
                repository.insert(WhitelistedChannel(channelName = trimmed))
            }
        }
    }

    fun removeChannel(channelName: String) {
        viewModelScope.launch {
            repository.delete(channelName)
        }
    }
}

class WhitelistViewModelFactory(private val repository: WhitelistRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WhitelistViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WhitelistViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
