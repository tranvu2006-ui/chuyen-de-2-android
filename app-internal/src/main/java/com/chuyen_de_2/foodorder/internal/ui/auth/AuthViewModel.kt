package com.chuyen_de_2.foodorder.internal.ui.auth

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

    private val _phoneOtpRequestResult = MutableLiveData<Result<Unit>>()
    val phoneOtpRequestResult: LiveData<Result<Unit>> = _phoneOtpRequestResult

    private val _phoneOtpVerifyResult = MutableLiveData<Result<String>>()
    val phoneOtpVerifyResult: LiveData<Result<String>> = _phoneOtpVerifyResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

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
        if (!code.matches(Regex("(\\d{6}|\\d{8})"))) {
            _otpVerifyResult.value = Result.failure(Exception("Mã OTP phải gồm đúng 6 hoặc 8 chữ số"))
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            _otpVerifyResult.value = authRepository.verifyEmailOtp(email, code)
            _isLoading.value = false
        }
    }

    fun requestPhoneOtp(phone: String) {
        _isLoading.value = true
        viewModelScope.launch {
            _phoneOtpRequestResult.value = authRepository.requestPhoneOtp(phone)
            _isLoading.value = false
        }
    }

    fun verifyPhoneOtp(phone: String, code: String) {
        if (!code.matches(Regex("\\d{6}"))) {
            _phoneOtpVerifyResult.value = Result.failure(Exception("Mã SMS OTP phải gồm đúng 6 chữ số"))
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            _phoneOtpVerifyResult.value = authRepository.verifyPhoneOtp(phone, code)
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
        pass: String,
        confirmPass: String,
        role: String,
        phone: String = "",
        verifiedEmail: String,
        verifiedPhone: String,
        verificationMethod: String
    ) {
        if (name.isBlank() || email.isBlank() || pass.isBlank()) {
            _registerResult.value = Result.failure(Exception("Vui lòng điền đầy đủ các thông tin bắt buộc"))
            return
        }
        if (pass.length < 6) {
            _registerResult.value = Result.failure(Exception("Mật khẩu phải có ít nhất 6 ký tự"))
            return
        }
        if (pass != confirmPass) {
            _registerResult.value = Result.failure(Exception("Mật khẩu xác nhận không khớp"))
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            val result = authRepository.register(
                name, email, pass, role, phone, verifiedEmail, verifiedPhone, verificationMethod
            )
            _registerResult.value = result.map { it.uid }
            _isLoading.value = false
        }
    }
}
