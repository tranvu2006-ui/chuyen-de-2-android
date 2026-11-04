package com.chuyen_de_2.foodorder.customer.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _loginResult = MutableLiveData<Result<String>>()
    val loginResult: LiveData<Result<String>> = _loginResult

    private val _registerResult = MutableLiveData<Result<String>>()
    val registerResult: LiveData<Result<String>> = _registerResult

    private val _otpRequestResult = MutableLiveData<Result<Unit>>()
    val otpRequestResult: LiveData<Result<Unit>> = _otpRequestResult

    private val _otpVerifyResult = MutableLiveData<Result<String>>()
    val otpVerifyResult: LiveData<Result<String>> = _otpVerifyResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    val isLoggedIn: Boolean get() = authRepository.isLoggedIn

    suspend fun getUserRole(): String? = authRepository.getUserRole()

    suspend fun checkAccountActiveStatus() = authRepository.checkAccountActiveStatus()

    fun logout() = authRepository.logout()

    fun requestEmailOtp(email: String) {
        if (email.isBlank()) {
            _otpRequestResult.value = Result.failure(Exception("Vui lòng nhập email"))
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            _otpRequestResult.value = authRepository.requestEmailOtp(email)
            _isLoading.value = false
        }
    }

    fun verifyEmailOtp(email: String, code: String) {
        if (!code.matches(Regex("\\d{6}"))) {
            _otpVerifyResult.value = Result.failure(Exception("Mã OTP phải gồm đúng 6 chữ số"))
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            _otpVerifyResult.value = authRepository.verifyEmailOtp(email, code)
            _isLoading.value = false
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _loginResult.value = Result.failure(Exception("Vui lòng nhập đầy đủ thông tin"))
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            val result = authRepository.login(email, password)
            _loginResult.value = result.map { it.uid }
            _isLoading.value = false
        }
    }

    fun register(
        name: String,
        email: String,
        password: String,
        confirmPassword: String,
        role: String = com.chuyen_de_2.foodorder.core.data.model.User.Role.CUSTOMER,
        phone: String = "",
        verifiedEmail: String
    ) {
        when {
            name.isBlank() || email.isBlank() || password.isBlank() -> {
                _registerResult.value = Result.failure(Exception("Vui lòng nhập đầy đủ thông tin"))
                return
            }
            password != confirmPassword -> {
                _registerResult.value = Result.failure(Exception("Mật khẩu không khớp"))
                return
            }
            password.length < 6 -> {
                _registerResult.value = Result.failure(Exception("Mật khẩu phải ít nhất 6 ký tự"))
                return
            }
        }

        _isLoading.value = true
        viewModelScope.launch {
            val result = authRepository.register(
                name, email, password, role, phone, verifiedEmail
            )
            _registerResult.value = result.map { it.uid }
            _isLoading.value = false
        }
    }

}
