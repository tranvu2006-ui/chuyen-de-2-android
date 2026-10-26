package com.chuyen_de_2.foodorder.admin.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminCategoryViewModel @Inject constructor(
    private val firebaseRepo: FirebaseRepository
) : ViewModel() {

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        observeCategories()
    }

    private fun observeCategories() {
        viewModelScope.launch {
            firebaseRepo.observeCategories(includeInactive = true).collect { list ->
                _categories.value = list.sortedBy { it.displayOrder }
            }
        }
    }

    fun saveCategory(category: Category) {
        viewModelScope.launch {
            val finalCategory = if (category.categoryId.isBlank()) {
                val generatedId = "cat_" + System.currentTimeMillis()
                category.copy(categoryId = generatedId)
            } else category

            val result = firebaseRepo.saveCategory(finalCategory)
            if (result.isSuccess) {
                _message.value = "Lưu danh mục '${finalCategory.name}' thành công!"
            } else {
                _message.value = "Lưu danh mục thất bại: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun toggleCategoryActive(category: Category, isActive: Boolean) {
        viewModelScope.launch {
            val result = firebaseRepo.toggleCategoryActive(category.categoryId, isActive)
            if (result.isSuccess) {
                val stateText = if (isActive) "Hiển thị" else "Ẩn"
                _message.value = "Đã chuyển '${category.name}' sang trạng thái: $stateText"
            }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            val result = firebaseRepo.deleteCategory(categoryId)
            if (result.isSuccess) {
                _message.value = "Đã xóa danh mục thành công!"
            } else {
                _message.value = "Xóa thất bại: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun restoreDefaults() {
        viewModelScope.launch {
            val result = firebaseRepo.restoreDefaultCategories()
            if (result.isSuccess) {
                _message.value = "Đã khôi phục 12 danh mục mẫu thành công!"
            } else {
                _message.value = "Khôi phục thất bại: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
