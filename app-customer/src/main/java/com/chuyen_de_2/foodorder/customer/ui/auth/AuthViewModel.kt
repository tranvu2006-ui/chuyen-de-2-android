package com.chuyen_de_2.foodorder.customer.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel xử lý đăng nhập, đăng ký và xác thực mã OTP qua Email.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _loginResult = MutableLiveData<Result<String>>()
    val loginResult: LiveData<Result<String>> = _loginResult

    private val _registerResult = MutableLiveData<Result<String>>()
    val registerResult: LiveData<Result<String>> = _registerResult

    private val _otpRequestResult = MutableLiveData<Result<String>>()
    val otpRequestResult: LiveData<Result<String>> = _otpRequestResult

    private val _otpVerifyResult = MutableLiveData<Result<String>>()
    val otpVerifyResult: LiveData<Result<String>> = _otpVerifyResult

    private val _loginOtpResult = MutableLiveData<Result<String>>()
    val loginOtpResult: LiveData<Result<String>> = _loginOtpResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    val isLoggedIn: Boolean get() = authRepository.isLoggedIn

    suspend fun getUserRole(): String? = authRepository.getUserRole()

    suspend fun checkAccountActiveStatus() = authRepository.checkAccountActiveStatus()

    fun logout() = authRepository.logout()

    /**
     * Yêu cầu gửi mã OTP xác thực qua Email cho khách hàng đăng ký tài khoản.
     */
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

    /**
     * Xác minh mã OTP 6 số do khách hàng nhập từ email.
     */
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

    /**
     * Xác minh OTP khi đăng nhập tài khoản yêu cầu xác thực 2 bước.
     * Gọi sau khi LoginActivity nhận được lỗi EmailOtpRequiredException.
     */
    fun verifyLoginOtp(email: String, password: String, code: String) {
        if (!code.matches(Regex("\\d{6}"))) {
            _loginOtpResult.value = Result.failure(Exception("Mã OTP phải gồm đúng 6 chữ số"))
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            _loginOtpResult.value = authRepository.verifyLoginOtp(email, password, code)
                .map { it.uid }
            _isLoading.value = false
        }
    }

    /**
     * Đăng nhập bằng email/password. Nếu tài khoản yêu cầu OTP → AuthRepository ném EmailOtpRequiredException.
     * LoginActivity bắt exception đó và gọi showLoginOtpDialog().
     */
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

    /**
     * Tạo tài khoản Firebase mới sau khi email đã được xác minh OTP thành công.
     * verifiedEmail: email trả về từ Supabase, dùng để đảm bảo đúng email đã xác thực.
     */
    fun register(
        name: String,
        email: String,
        password: String,
        confirmPassword: String,
        role: String = com.chuyen_de_2.foodorder.core.data.model.User.Role.CUSTOMER,
        phone: String = "",
        verifiedEmail: String = ""
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
                name = name,
                email = email,
                password = password,
                role = role,
                phone = phone,
                verifiedEmail = verifiedEmail
            )
            _registerResult.value = result.map { it.uid }
            _isLoading.value = false
        }
    }
    fun registerWithOtp(
        name: String,
        email: String,
        password: String,
        confirmPassword: String,
        role: String = com.chuyen_de_2.foodorder.core.data.model.User.Role.CUSTOMER,
        phone: String = "",
        otpCode: String
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
            !otpCode.matches(Regex("\\d{6}")) -> {
                _registerResult.value = Result.failure(Exception("Mã OTP phải gồm đúng 6 chữ số"))
                return
            }
        }

        _isLoading.value = true
        viewModelScope.launch {
            val result = authRepository.registerWithOtp(
                name = name,
                email = email,
                password = password,
                role = role,
                phone = phone,
                otpCode = otpCode
            )
            _registerResult.value = result.map { it.uid }
            _isLoading.value = false
        }
    }
}
