package io.nativeplanet.grove.ui.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.nativeplanet.grove.GroveApp
import io.nativeplanet.grove.domain.model.GroveFile
import io.nativeplanet.grove.domain.model.GroveView
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BrowseUiState(
    val files: List<GroveFile> = emptyList(),
    val views: List<GroveView> = emptyList(),
    val selectedView: String? = null,
    val searchQuery: String = "",
    val isConnected: Boolean = false,
    val shipName: String? = null,
    val pendingUploads: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

class BrowseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GroveApp.instance.repository

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            combine(
                repository.files,
                repository.views,
                repository.isConnected,
                repository.shipName,
                repository.pendingUploads,
                _searchQuery
            ) { files, views, connected, shipName, pending, query ->
                val filteredFiles = if (query.isBlank()) {
                    files
                } else {
                    files.filter {
                        it.name.contains(query, ignoreCase = true) ||
                        it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
                    }
                }

                BrowseUiState(
                    files = filteredFiles.sortedByDescending { it.modified },
                    views = views,
                    searchQuery = query,
                    isConnected = connected,
                    shipName = shipName,
                    pendingUploads = pending,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }

        autoConnect()
    }

    private fun autoConnect() {
        viewModelScope.launch {
            val prefs = getApplication<GroveApp>().getSharedPreferences("grove", 0)
            val savedCode = prefs.getString("ship_code", null)
            if (savedCode != null) {
                repository.connect(savedCode)
            }
        }
    }

    fun connect(code: String) {
        viewModelScope.launch {
            val success = repository.connect(code)
            if (success) {
                getApplication<GroveApp>().getSharedPreferences("grove", 0)
                    .edit()
                    .putString("ship_code", code)
                    .apply()
            } else {
                _uiState.update { it.copy(error = "Connection failed") }
            }
        }
    }

    fun disconnect() {
        repository.disconnect()
        getApplication<GroveApp>().getSharedPreferences("grove", 0)
            .edit()
            .remove("ship_code")
            .apply()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectView(viewName: String?) {
        _uiState.update { it.copy(selectedView = viewName) }
    }

    fun toggleStar(fileId: String) {
        viewModelScope.launch {
            repository.toggleStar(fileId)
        }
    }

    fun deleteFile(fileId: String) {
        viewModelScope.launch {
            repository.deleteFile(fileId)
        }
    }

    fun shareFile(fileId: String) {
        viewModelScope.launch {
            repository.shareFile(fileId)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.syncAll()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
