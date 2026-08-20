package com.chat.app.media.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chat.app.data.local.media.MediaCategory
import com.chat.app.data.local.media.MediaFileManager
import com.chat.app.data.local.media.MediaItem
import com.chat.app.data.local.media.MediaStorageBreakdown
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MediaStorageUiState(
    val isLoading: Boolean = true,
    val selectedCategory: MediaCategory = MediaCategory.ALL,
    val storageBreakdown: MediaStorageBreakdown = MediaStorageBreakdown(),
    val mediaItems: List<MediaItem> = emptyList(),
    val previewItem: MediaItem? = null,
    val itemToDelete: MediaItem? = null,
    val showClearConfirmation: Boolean = false
)

@HiltViewModel
class MediaStorageViewModel @Inject constructor(
    private val mediaFileManager: MediaFileManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaStorageUiState())
    val uiState: StateFlow<MediaStorageUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val breakdown = mediaFileManager.getStorageBreakdown()
            val items = mediaFileManager.getAllMediaItems(_uiState.value.selectedCategory)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    storageBreakdown = breakdown,
                    mediaItems = items
                )
            }
        }
    }

    fun selectCategory(category: MediaCategory) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedCategory = category, isLoading = true) }
            val items = mediaFileManager.getAllMediaItems(category)
            _uiState.update { it.copy(isLoading = false, mediaItems = items) }
        }
    }

    fun setPreviewItem(item: MediaItem?) {
        _uiState.update { it.copy(previewItem = item) }
    }

    fun requestDeleteItem(item: MediaItem?) {
        _uiState.update { it.copy(itemToDelete = item) }
    }

    fun confirmDeleteItem() {
        val item = _uiState.value.itemToDelete ?: return
        viewModelScope.launch {
            mediaFileManager.deleteMediaFile(item.path)
            _uiState.update { it.copy(itemToDelete = null) }
            loadData()
        }
    }

    fun showClearDialog(show: Boolean) {
        _uiState.update { it.copy(showClearConfirmation = show) }
    }

    fun confirmClearCategory() {
        val category = _uiState.value.selectedCategory
        viewModelScope.launch {
            mediaFileManager.clearMedia(category)
            _uiState.update { it.copy(showClearConfirmation = false) }
            loadData()
        }
    }
}
