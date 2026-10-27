package com.chuyen_de_2.foodorder.admin.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AdminAuthState {
    object Idle : AdminAuthState()
    object Loading : AdminAuthState()
    data class Success(val user: User) : AdminAuthState()
    data class Error(val message: String) : AdminAuthState()
}

@HiltViewModel
class AdminAuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AdminAuthState>(AdminAuthState.Idle)
    val authState: StateFlow<AdminAuthState> = _authState.asStateFlow()

    fun checkExistingSession() {
        if (authRepository.currentUser == null) {
            _authState.value = AdminAuthState.Idle
            return
        }

        viewModelScope.launch {
            _authState.value = AdminAuthState.Loading
            val currentEmail = authRepository.currentUser?.email.orEmpty().trim()
            val role = authRepository.getUserRole()
            if (role == User.Role.ADMIN && currentEmail.equals("admin@gmail.com", ignoreCase = true)) {
                val userResult = authRepository.getCurrentUserData()
                val user = userResult.getOrNull() ?: User(
                    uid = authRepository.currentUser?.uid.orEmpty(),
                    role = User.Role.ADMIN,
                    name = "Quản trị viên",
                    email = currentEmail
                )
                _authState.value = AdminAuthState.Success(user)
            } else {
                authRepository.logout()
                _authState.value = AdminAuthState.Idle
            }
        }
    }

    fun login(email: String, pass: String) {
        val trimmedEmail = email.trim()
        val trimmedPass = pass.trim()

        if (trimmedEmail.isBlank() || trimmedPass.isBlank()) {
            _authState.value = AdminAuthState.Error("Vui lòng nhập đầy đủ Email và Mật khẩu.")
            return
        }

        if (!trimmedEmail.equals("admin@gmail.com", ignoreCase = true)) {
            _authState.value = AdminAuthState.Error("Hệ thống chỉ chấp nhận tài khoản quản trị duy nhất: admin@gmail.com")
            return
        }

        viewModelScope.launch {
            _authState.value = AdminAuthState.Loading
            val loginResult = authRepository.login(trimmedEmail, trimmedPass)
            if (loginResult.isFailure) {
                _authState.value = AdminAuthState.Error(
                    loginResult.exceptionOrNull()?.localizedMessage ?: "Đăng nhập thất bại. Vui lòng kiểm tra lại tài khoản."
                )
                return@launch
            }

            val role = authRepository.getUserRole()
            if (role != User.Role.ADMIN) {
                authRepository.logout()
                _authState.value = AdminAuthState.Error(
                    "Từ chối truy cập: Tài khoản '$trimmedEmail' không thuộc nhóm quyền QUẢN TRỊ VIÊN (ADMIN)."
                )
                return@launch
            }

            val userRes = authRepository.getCurrentUserData()
            val user = userRes.getOrNull() ?: User(
                uid = authRepository.currentUser?.uid.orEmpty(),
                role = User.Role.ADMIN,
                name = "Quản trị viên",
                email = trimmedEmail
            )
            _authState.value = AdminAuthState.Success(user)
        }
    }

    fun logout() {
        authRepository.logout()
        _authState.value = AdminAuthState.Idle
    }
}
