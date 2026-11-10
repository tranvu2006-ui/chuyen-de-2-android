package com.chuyen_de_2.foodorder.core.data.firebase

import com.chuyen_de_2.foodorder.core.data.model.Customer
import com.chuyen_de_2.foodorder.core.data.model.Driver
import com.chuyen_de_2.foodorder.core.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class EmailOtpRequiredException : Exception("Email chưa được xác minh. Vui lòng nhập mã OTP đã gửi đến email.")

/**
 * Repository xử lý xác thực — Firebase Authentication
 * Hỗ trợ: Login Email/Password, Register, Supabase Email OTP
 */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val emailOtpApi: EmailOtpApi
) {
    /** Đăng nhập bằng Email/Password (kiểm tra khóa tài khoản từ Admin & tự động đồng bộ Gmail/Hồ sơ lên Realtime DB) */
    suspend fun login(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: return Result.failure(Exception("Đăng nhập thất bại"))
            firebaseUser.reload().await()
            val userRef = database.getReference("users").child(firebaseUser.uid)
            val verificationState = if (firebaseUser.isEmailVerified) {
                null
            } else {
                try {
                    userRef.get().await()
                } catch (error: Exception) {
                    auth.signOut()
                    throw error
                }
            }
            val driverSnapAtLogin = try {
                database.getReference("drivers").child(firebaseUser.uid).get().await()
            } catch (_: Exception) { null }
            val hasDriverProfile = driverSnapAtLogin != null && driverSnapAtLogin.exists()
            val driverStoreIdFromProfile = driverSnapAtLogin?.child("storeId")?.getValue(String::class.java).orEmpty()

            val emailVerifiedByOtp = verificationState
                ?.child("emailVerified")
                ?.getValue(Boolean::class.java) == true ||
                verificationState
                    ?.child("registrationVerified")
                    ?.getValue(Boolean::class.java) == true
            val isStoreCreatedDriver = hasDriverProfile || (
                verificationState
                    ?.child("role")
                    ?.getValue(String::class.java)
                    .orEmpty()
                    .uppercase() in setOf(User.Role.DRIVER, User.Role.SHIPPER) &&
                    !verificationState
                        ?.child("storeId")
                        ?.getValue(String::class.java)
                        .isNullOrBlank()
                )
            // Chấp nhận một trong hai cách xác thực: emailVerified của Firebase hoặc cờ xác minh OTP đã lưu trong hồ sơ.
            // Ngoại lệ: tài khoản DRIVER/SHIPPER có storeId là shipper do quán tạo nên không cần xác minh email tại đây.
            // Nếu chưa xác thực qua Firebase/OTP, gửi OTP Supabase và dừng đăng nhập cho tới khi người dùng nhập mã.
            if (!firebaseUser.isEmailVerified && !emailVerifiedByOtp && !isStoreCreatedDriver) {
                try {
                    emailOtpApi.requestCode(email.trim())
                } catch (error: Exception) {
                    auth.signOut()
                    return Result.failure(
                        Exception("Không gửi được mã OTP xác minh email.", error)
                    )
                }
                auth.signOut()
                return Result.failure(EmailOtpRequiredException())
            }

            val user = completePostLogin(
                firebaseUser = firebaseUser,
                email = email,
                emailVerifiedByOtp = emailVerifiedByOtp || isStoreCreatedDriver,
                isStoreCreatedDriver = isStoreCreatedDriver
            )
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Quy trình hậu đăng nhập: kiểm tra trạng thái khóa từ Admin và đồng bộ hồ sơ vào Realtime DB.
     */
    private suspend fun completePostLogin(
        firebaseUser: FirebaseUser,
        email: String,
        emailVerifiedByOtp: Boolean,
        isStoreCreatedDriver: Boolean = false
    ): FirebaseUser {
        val userRef = database.getReference("users").child(firebaseUser.uid)
        try { userRef.keepSynced(true) } catch (_: Exception) {}

        // Ưu tiên đọc trực tiếp từ Firebase Server qua REST API với idToken mới để vượt qua SQLite Persistence Cache cũ
        val freshJson = fetchFreshUserJsonFromServer(firebaseUser.uid)
        val userSnap = try { userRef.get().await() } catch (_: Exception) { null }

        val actualEmail = firebaseUser.email?.takeIf { it.isNotBlank() } ?: email.trim()
        val isAdminEmail = actualEmail.equals("admin@gmail.com", ignoreCase = true)

        if (!isAdminEmail) {
            if (freshJson != null) {
                val status = freshJson.optString("status", "ACTIVE").uppercase()
                val isActive = when {
                    freshJson.has("isActive") && !freshJson.isNull("isActive") -> freshJson.optBoolean("isActive", true)
                    freshJson.has("active") && !freshJson.isNull("active") -> freshJson.optBoolean("active", true)
                    else -> true
                }
                if (status == "LOCKED" || status == "SUSPENDED" || status == "INACTIVE" || !isActive) {
                    val reason = freshJson.optString("lockReason", "").ifBlank {
                        freshJson.optString("suspendedReason", "").ifBlank { "Vi phạm quy định nền tảng" }
                    }
                    try { auth.signOut() } catch (_: Exception) {}
                    throw Exception("Tài khoản đã bị khóa bởi Admin: $reason")
                }
            } else if (userSnap != null && userSnap.exists()) {
                val status = userSnap.child("status").getValue(String::class.java)?.uppercase() ?: "ACTIVE"
                val isActive = userSnap.child("isActive").getValue(Boolean::class.java)
                    ?: userSnap.child("active").getValue(Boolean::class.java)
                    ?: true
                if (status == "LOCKED" || status == "SUSPENDED" || status == "INACTIVE" || !isActive) {
                    val reason = userSnap.child("lockReason").getValue(String::class.java)
                        ?: userSnap.child("suspendedReason").getValue(String::class.java)
                        ?: "Vi phạm quy định nền tảng"
                    try { auth.signOut() } catch (_: Exception) {}
                    throw Exception("Tài khoản đã bị khóa bởi Admin: $reason")
                }
            }
        }

        // Đồng bộ Gmail & thông tin định danh từ Firebase Auth vào users/{uid} để Admin Dashboard nhận diện chính xác
        try {
            val dbEmail = freshJson?.optString("email", "")?.takeIf { it.isNotBlank() }
                ?: userSnap?.child("email")?.getValue(String::class.java).orEmpty()
            val dbName = freshJson?.optString("name", "")?.takeIf { it.isNotBlank() }
                ?: userSnap?.child("name")?.getValue(String::class.java).orEmpty()
            val dbPhone = freshJson?.optString("phone", "")?.takeIf { it.isNotBlank() }
                ?: userSnap?.child("phone")?.getValue(String::class.java).orEmpty()
            val rawDbRole = freshJson?.optString("role", "")?.takeIf { it.isNotBlank() }
                ?: userSnap?.child("role")?.getValue(String::class.java).orEmpty()
            val rawDbStoreId = freshJson?.optString("storeId", "")?.takeIf { it.isNotBlank() }
                ?: userSnap?.child("storeId")?.getValue(String::class.java).orEmpty()

            val driverSnapAtLogin = try {
                database.getReference("drivers").child(firebaseUser.uid).get().await()
            } catch (_: Exception) { null }
            val hasDriverProfile = driverSnapAtLogin != null && driverSnapAtLogin.exists()
            val driverStoreIdFromProfile = driverSnapAtLogin?.child("storeId")?.getValue(String::class.java).orEmpty()

            val (dbRole, dbStoreId) = if (hasDriverProfile) {
                Pair(User.Role.DRIVER, driverStoreIdFromProfile.ifBlank { rawDbStoreId })
            } else {
                sanitizeCorruptedCustomerRoleIfNeeded(
                    uid = firebaseUser.uid,
                    email = actualEmail,
                    currentRole = rawDbRole,
                    currentStoreId = rawDbStoreId
                )
            }

            val wasCorruptedMock = dbEmail == "mai.tran@oishifood.vn" && !actualEmail.equals("mai.tran@oishifood.vn", ignoreCase = true)
            val resolvedRole = when {
                actualEmail.equals("admin@gmail.com", ignoreCase = true) -> User.Role.ADMIN
                hasDriverProfile -> User.Role.DRIVER
                dbRole.isNotBlank() -> dbRole.uppercase()
                actualEmail.contains("manager", ignoreCase = true) || actualEmail.contains("merchant", ignoreCase = true) -> User.Role.MERCHANT
                actualEmail.contains("shipper", ignoreCase = true) || actualEmail.contains("driver", ignoreCase = true) -> User.Role.DRIVER
                else -> User.Role.CUSTOMER
            }
            val resolvedRoleId = when (resolvedRole) {
                User.Role.ADMIN -> 1
                User.Role.MERCHANT, User.Role.MANAGER -> 3
                User.Role.DRIVER, User.Role.SHIPPER -> 4
                else -> 2
            }
            val resolvedName = if (dbName.isNotBlank() && !wasCorruptedMock) {
                dbName
            } else {
                firebaseUser.displayName?.takeIf { it.isNotBlank() } ?: actualEmail.substringBefore("@")
            }
            val resolvedPhone = if (dbPhone.isNotBlank() && !wasCorruptedMock) {
                dbPhone
            } else {
                firebaseUser.phoneNumber.orEmpty()
            }

            val syncUpdates = mutableMapOf<String, Any?>(
                "uid" to firebaseUser.uid,
                "email" to actualEmail,
                "name" to resolvedName,
                "phone" to resolvedPhone,
                "role" to resolvedRole,
                "roleId" to resolvedRoleId,
                "storeId" to dbStoreId,
                "status" to User.Status.ACTIVE,
                "emailVerified" to (firebaseUser.isEmailVerified || emailVerifiedByOtp || isStoreCreatedDriver),
                "isActive" to true,
                "lockReason" to null,
                "suspendedReason" to null
            )
            userRef.updateChildren(syncUpdates).await()

            if (resolvedRole == User.Role.CUSTOMER) {
                val custUpdates = mutableMapOf<String, Any>(
                    "customerId" to firebaseUser.uid,
                    "uid" to firebaseUser.uid,
                    "email" to actualEmail,
                    "name" to resolvedName,
                    "status" to User.Status.ACTIVE
                )
                if (resolvedPhone.isNotBlank()) custUpdates["phone"] = resolvedPhone
                database.getReference("customers").child(firebaseUser.uid).updateChildren(custUpdates)
            } else if ((resolvedRole == User.Role.MERCHANT || resolvedRole == User.Role.MANAGER) && dbStoreId.isNotBlank()) {
                database.getReference("stores").child(dbStoreId).child("email").setValue(actualEmail)
            }
        } catch (_: Exception) {}

        return firebaseUser
    }

    /**
     * Gửi yêu cầu mã OTP 6 số đến email đăng ký thông qua dịch vụ Supabase Auth.
     */
    suspend fun requestEmailOtp(email: String): Result<String> {
        return try {
            // Chuẩn hóa khoảng trắng trước khi gửi email lên Supabase.
            emailOtpApi.requestCode(email.trim())
            // Thành công ở đây nghĩa là Supabase nhận yêu cầu gửi mã, chưa phải tài khoản Firebase đã được tạo.
            Result.success("")
        } catch (error: Exception) {
            // Giữ lỗi để ViewModel/UI hiển thị và không cho đi tiếp như thể gửi mã thành công.
            Result.failure(error)
        }
    }

    /**
     * Xác thực mã OTP người dùng nhập với email đăng ký.
     * Trả về Result chứa email đã xác thực thành công.
     */
    suspend fun verifyEmailOtp(email: String, code: String): Result<String> {
        return try {
            // Xác minh thật qua Supabase; kết quả chứa email được máy chủ xác nhận.
            Result.success(emailOtpApi.verifyCode(email.trim(), code.trim()))
        } catch (error: Exception) {
            // OTP sai/hết hạn hoặc lỗi mạng phải chặn đăng ký và trả lỗi về UI.
            Result.failure(error)
        }
    }

    /**
     * Xác thực đăng nhập bằng OTP: Chạy song song kiểm tra OTP trên Supabase và đăng nhập Firebase
     * giúp giảm tối đa độ trễ mạng (giảm từ 2 lần round-trip nối tiếp xuống còn 1 lần song song).
     */
    suspend fun verifyLoginOtp(email: String, password: String, code: String): Result<FirebaseUser> {
        return try {
            val cleanEmail = email.trim().lowercase()
            val cleanCode = code.trim()

            supervisorScope {
                val otpDeferred = async {
                    runCatching { emailOtpApi.verifyCode(cleanEmail, cleanCode) }
                }
                val authDeferred = async {
                    runCatching { auth.signInWithEmailAndPassword(cleanEmail, password).await() }
                }

                val otpRes = otpDeferred.await()
                val authRes = authDeferred.await()

                if (otpRes.isFailure) {
                    try { auth.signOut() } catch (_: Exception) {}
                    val rawMsg = otpRes.exceptionOrNull()?.message.orEmpty()
                    val userMsg = if (
                        rawMsg.contains("expired", ignoreCase = true) ||
                        rawMsg.contains("invalid", ignoreCase = true) ||
                        rawMsg.contains("token", ignoreCase = true) ||
                        rawMsg.contains("otp", ignoreCase = true)
                    ) {
                        "Mã OTP không đúng hoặc đã hết hạn. Vui lòng bấm 'Lấy mã' để nhận mã mới."
                    } else {
                        rawMsg.ifBlank { "Xác thực mã OTP thất bại." }
                    }
                    throw Exception(userMsg)
                }

                if (authRes.isFailure) {
                    val rawMsg = authRes.exceptionOrNull()?.message.orEmpty()
                    val userMsg = if (
                        rawMsg.contains("password", ignoreCase = true) ||
                        rawMsg.contains("credential", ignoreCase = true) ||
                        rawMsg.contains("mật khẩu", ignoreCase = true)
                    ) {
                        "Mật khẩu không chính xác."
                    } else if (rawMsg.contains("no user", ignoreCase = true) || rawMsg.contains("user-not-found", ignoreCase = true)) {
                        "Không tìm thấy tài khoản với email này."
                    } else {
                        rawMsg.ifBlank { "Đăng nhập thất bại." }
                    }
                    throw Exception(userMsg)
                }

                val verifiedEmail = otpRes.getOrNull().orEmpty()
                val authResult = authRes.getOrNull()
                val firebaseUser = authResult?.user
                    ?: throw Exception("Không xác định được tài khoản Firebase.")

                if (!cleanEmail.equals(verifiedEmail, ignoreCase = true) ||
                    !cleanEmail.equals(firebaseUser.email.orEmpty(), ignoreCase = true)) {
                    auth.signOut()
                    throw Exception("Email OTP không khớp với tài khoản Firebase.")
                }

                val userRef = database.getReference("users").child(firebaseUser.uid)
                try {
                    userRef.child("emailVerified").setValue(true).await()
                } catch (_: Exception) {}

                val user = completePostLogin(firebaseUser, cleanEmail, emailVerifiedByOtp = true)
                Result.success(user)
            }
        } catch (error: Exception) {
            try { auth.signOut() } catch (_: Exception) {}
            Result.failure(error)
        }
    }

    /** Đăng ký tài khoản kèm xác thực mã OTP 6 số từ Supabase trong cùng một quy trình mượt mà */
    suspend fun registerWithOtp(
        name: String,
        email: String,
        password: String,
        role: String = User.Role.CUSTOMER,
        phone: String = "",
        otpCode: String
    ): Result<FirebaseUser> {
        return try {
            val cleanEmail = email.trim().lowercase()
            val cleanOtp = otpCode.trim()
            val verifiedEmail = try {
                emailOtpApi.verifyCode(cleanEmail, cleanOtp)
            } catch (e: Exception) {
                val rawMsg = e.message.orEmpty()
                val userMsg = if (
                    rawMsg.contains("expired", ignoreCase = true) ||
                    rawMsg.contains("invalid", ignoreCase = true) ||
                    rawMsg.contains("token", ignoreCase = true) ||
                    rawMsg.contains("otp", ignoreCase = true)
                ) {
                    "Mã OTP không đúng hoặc đã hết hạn. Vui lòng bấm 'Lấy mã' để nhận mã mới."
                } else {
                    rawMsg.ifBlank { "Xác thực mã OTP thất bại." }
                }
                throw Exception(userMsg)
            }
            register(
                name = name,
                email = cleanEmail,
                password = password,
                role = role,
                phone = phone,
                verifiedEmail = verifiedEmail
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }



    /** Đăng ký tài khoản mới sau khi OTP email đã được xác nhận và phân loại theo Actor */
    suspend fun register(
        name: String,
        email: String,
        password: String,
        role: String = User.Role.CUSTOMER,
        phone: String = "",
        verifiedEmail: String = ""
    ): Result<FirebaseUser> {
        return try {
            // Chỉ cho tạo Firebase account nếu bước OTP trước đó xác nhận đúng chính email đăng ký.
            val isEmailVerified = verifiedEmail.isNotBlank() && verifiedEmail.equals(email.trim(), ignoreCase = true)
            val normalizedPhone = phone.trim()

            // Chặn gọi register mà không có kết quả xác minh email hợp lệ.
            if (!isEmailVerified) {
                return Result.failure(Exception("Bạn cần xác thực Email bằng mã OTP trước khi tạo tài khoản."))
            }
            // Firebase Auth vẫn là hệ thống tài khoản đăng nhập chính; Supabase chỉ xác minh OTP trong luồng đăng ký.
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: return Result.failure(Exception("Đăng ký thất bại"))

            // Lưu displayName trực tiếp vào Firebase Auth Profile để không bao giờ mất tên khi đồng bộ lại
            try {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name.trim())
                    .build()
                firebaseUser.updateProfile(profileUpdates).await()
            } catch (_: Exception) {}

            // Chuẩn hóa role RBAC
            val normalizedRole = when (role.uppercase()) {
                "MERCHANT", "MANAGER" -> User.Role.MERCHANT
                "DRIVER", "SHIPPER" -> User.Role.DRIVER
                "ADMIN" -> User.Role.ADMIN
                else -> User.Role.CUSTOMER
            }
            val roleId = when (normalizedRole) {
                User.Role.ADMIN -> 1
                User.Role.MERCHANT -> 3
                User.Role.DRIVER -> 4
                else -> 2
            }

            val assignedStoreId = if (normalizedRole == User.Role.MERCHANT) {
                "store_${firebaseUser.uid.take(8)}"
            } else {
                ""
            }

            // 1. Lưu thông tin tài khoản dùng chung (Bảng 1: users)
            val user = User(
                uid = firebaseUser.uid,
                roleId = roleId,
                role = normalizedRole,
                name = name.trim(),
                email = email.trim(),
                phone = normalizedPhone,
                status = User.Status.ACTIVE,
                isActive = true,
                storeId = assignedStoreId,
                emailVerified = isEmailVerified,
                phoneVerified = false
            )
            database.getReference("users")
                .child(firebaseUser.uid)
                .setValue(user)
                .await()

            // 2. Tạo hồ sơ thực thể chuyên biệt theo từng Actor để không bị gộp chung thành 1 loại
            when (normalizedRole) {
                User.Role.CUSTOMER -> {
                    // Bảng 2: customers — Hồ sơ khách hàng & Điểm uy tín (kèm email, name, phone để Admin quản lý)
                    val customerMap = mapOf(
                        "customerId" to firebaseUser.uid,
                        "uid" to firebaseUser.uid,
                        "name" to name.trim(),
                        "email" to email.trim(),
                        "phone" to normalizedPhone,
                        "trustScore" to 100,
                        "totalOrders" to 0,
                        "completedOrders" to 0,
                        "boomOrders" to 0,
                        "status" to User.Status.ACTIVE
                    )
                    database.getReference("customers")
                        .child(firebaseUser.uid)
                        .setValue(customerMap)
                        .await()
                }
                User.Role.DRIVER -> {
                    // Bảng 10: drivers — Hồ sơ tài xế nội bộ quán
                    val randomCode = "TX-" + (10..99).random()
                    val driver = Driver(
                        driverId = firebaseUser.uid,
                        storeId = assignedStoreId,
                        driverCode = randomCode,
                        name = name.trim(),
                        phone = normalizedPhone,
                        vehiclePlate = "59P1-${(100..999).random()}.${(10..99).random()}",
                        ratingAvg = 5.0f,
                        dutyStatus = Driver.DutyStatus.AVAILABLE
                    )
                    database.getReference("drivers")
                        .child(firebaseUser.uid)
                        .setValue(driver)
                        .await()
                }
                User.Role.MERCHANT -> {
                    // Bảng 5: stores — Hồ sơ cửa hàng của đối tác chủ quán
                    val storeData = com.chuyen_de_2.foodorder.core.data.model.Store(
                        id = assignedStoreId,
                        merchantId = firebaseUser.uid,
                        name = if (name.isNotBlank()) name.trim() else "Quán Ăn Mới",
                        ownerName = name.trim(),
                        phone = normalizedPhone,
                        address = "",
                        district = "",
                        storeStatus = com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.PENDING,
                        isVerified = false,
                        ratingAvg = 5.0f,
                        deliveryTime = "20-30 phút",
                        category = "Món chính",
                        imageUrl = "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=400"
                    )
                    database.getReference("stores")
                        .child(assignedStoreId)
                        .setValue(storeData)
                        .await()
                    database.getReference("stores")
                        .child(assignedStoreId)
                        .child("email")
                        .setValue(email.trim())
                }
            }

            auth.signOut()
            Result.success(firebaseUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lấy thông tin hồ sơ Khách Hàng (Bảng 2: customers) */
    suspend fun getCurrentCustomerData(): Result<Customer> {
        return try {
            val uid = auth.currentUser?.uid
                ?: return Result.failure(Exception("Chưa đăng nhập"))

            val snapshot = database.getReference("customers")
                .child(uid)
                .get()
                .await()

            val customer = snapshot.getValue(Customer::class.java)
                ?: Customer(customerId = uid, trustScore = 100)

            Result.success(customer)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lấy thông tin User hiện tại từ Firebase DB và tự động đồng bộ email/name từ FirebaseAuth */
    suspend fun getCurrentUserData(): Result<User> {
        return try {
            val firebaseUser = auth.currentUser
                ?: return Result.failure(Exception("Chưa đăng nhập"))
            val uid = firebaseUser.uid
            val currentEmail = firebaseUser.email.orEmpty().trim()
            val authDisplayName = firebaseUser.displayName.orEmpty().trim()
            val authPhone = firebaseUser.phoneNumber.orEmpty().trim()

            val userRef = database.getReference("users").child(uid)
            val snapshot = userRef.get().await()

            var user = snapshot.getValue(User::class.java)

            val fallbackRole = when {
                currentEmail.contains("manager", ignoreCase = true) || currentEmail.contains("merchant", ignoreCase = true) -> User.Role.MERCHANT
                currentEmail.contains("shipper", ignoreCase = true) || currentEmail.contains("driver", ignoreCase = true) -> User.Role.DRIVER
                currentEmail.equals("admin@gmail.com", ignoreCase = true) -> User.Role.ADMIN
                else -> User.Role.CUSTOMER
            }
            val fallbackRoleId = when (fallbackRole) {
                User.Role.ADMIN -> 1
                User.Role.MERCHANT -> 3
                User.Role.DRIVER -> 4
                else -> 2
            }

            if (user == null || !snapshot.exists()) {
                val resolvedName = authDisplayName.ifBlank { currentEmail.substringBefore("@").ifBlank { "Khách hàng" } }
                val newUser = User(
                    uid = uid,
                    email = currentEmail,
                    name = resolvedName,
                    phone = authPhone,
                    role = fallbackRole,
                    roleId = fallbackRoleId,
                    status = User.Status.ACTIVE,
                    isActive = true
                )
                try {
                    userRef.setValue(newUser).await()
                    if (fallbackRole == User.Role.CUSTOMER) {
                        database.getReference("customers").child(uid).updateChildren(
                            mapOf(
                                "customerId" to uid,
                                "uid" to uid,
                                "email" to currentEmail,
                                "name" to resolvedName
                            )
                        )
                    }
                } catch (_: Exception) {}
                user = newUser
            } else {
                // Tự động sửa nếu tài khoản tài xế bị nhảy sang CUSTOMER hoặc khách hàng bị gán nhầm storeId
                val (healedRole, healedStoreId) = sanitizeCorruptedCustomerRoleIfNeeded(
                    uid = uid,
                    email = currentEmail,
                    currentRole = user.role,
                    currentStoreId = user.storeId
                )
                if (healedRole != user.role || healedStoreId != user.storeId) {
                    val healedRoleId = when (healedRole) {
                        User.Role.ADMIN -> 1
                        User.Role.MERCHANT, User.Role.MANAGER -> 3
                        User.Role.DRIVER, User.Role.SHIPPER -> 4
                        else -> 2
                    }
                    user = user.copy(
                        role = healedRole,
                        roleId = healedRoleId,
                        storeId = healedStoreId
                    )
                }

                // Kiểm tra nếu bản ghi trên DB bị thiếu Gmail, tên hoặc bị ghi đè bởi dữ liệu mẫu cũ
                val wasCorruptedMock = user.email == "mai.tran@oishifood.vn" && currentEmail.isNotBlank() && !currentEmail.equals("mai.tran@oishifood.vn", ignoreCase = true)
                val needEmailSync = currentEmail.isNotBlank() && (user.email.isBlank() || user.email != currentEmail)
                val needNameSync = user.name.isBlank() || wasCorruptedMock
                val needUidSync = user.uid.isBlank() || user.uid != uid

                if (needEmailSync || needNameSync || needUidSync) {
                    val resolvedName = if (!needNameSync) {
                        user.name
                    } else {
                        authDisplayName.ifBlank { currentEmail.substringBefore("@").ifBlank { user.name } }
                    }
                    val resolvedPhone = if (wasCorruptedMock) authPhone else user.phone.ifBlank { authPhone }
                    val resolvedRole = if (currentEmail.equals("admin@gmail.com", ignoreCase = true)) User.Role.ADMIN else user.role.ifBlank { fallbackRole }
                    val resolvedRoleId = if (currentEmail.equals("admin@gmail.com", ignoreCase = true)) 1 else if (user.roleId > 0) user.roleId else fallbackRoleId

                    val updates = mutableMapOf<String, Any>(
                        "uid" to uid,
                        "role" to resolvedRole,
                        "roleId" to resolvedRoleId
                    )
                    if (currentEmail.isNotBlank()) updates["email"] = currentEmail
                    if (resolvedName.isNotBlank()) updates["name"] = resolvedName
                    if (wasCorruptedMock || resolvedPhone.isNotBlank()) updates["phone"] = resolvedPhone

                    try {
                        userRef.updateChildren(updates).await()
                        if (resolvedRole == User.Role.CUSTOMER) {
                            val custUpdates = mutableMapOf<String, Any>(
                                "customerId" to uid,
                                "uid" to uid
                            )
                            if (currentEmail.isNotBlank()) custUpdates["email"] = currentEmail
                            if (resolvedName.isNotBlank()) custUpdates["name"] = resolvedName
                            if (wasCorruptedMock || resolvedPhone.isNotBlank()) custUpdates["phone"] = resolvedPhone
                            database.getReference("customers").child(uid).updateChildren(custUpdates)
                        }
                    } catch (_: Exception) {}

                    user = user.copy(
                        uid = uid,
                        email = if (currentEmail.isNotBlank()) currentEmail else user.email,
                        name = resolvedName,
                        phone = resolvedPhone,
                        role = resolvedRole,
                        roleId = resolvedRoleId
                    )
                }
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Cập nhật thông tin cá nhân (Họ tên, Số điện thoại, Địa chỉ) cho người dùng hiện tại */
    suspend fun updateUserProfile(
        name: String,
        phone: String,
        avatarUrl: String = "",
        address: String = ""
    ): Result<Unit> {
        return try {
            val firebaseUser = auth.currentUser ?: return Result.failure(Exception("Chưa đăng nhập"))
            val uid = firebaseUser.uid
            val email = firebaseUser.email.orEmpty()

            if (name.isNotBlank()) {
                try {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(name.trim())
                        .build()
                    firebaseUser.updateProfile(profileUpdates).await()
                } catch (_: Exception) {}
            }

            val updates = mutableMapOf<String, Any>(
                "uid" to uid,
                "name" to name.trim(),
                "phone" to phone.trim()
            )
            if (email.isNotBlank()) updates["email"] = email
            if (address.isNotBlank()) updates["address"] = address.trim()
            if (avatarUrl.isNotBlank()) updates["avatarUrl"] = avatarUrl.trim()

            database.getReference("users").child(uid).updateChildren(updates).await()
            database.getReference("customers").child(uid).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lắng nghe thay đổi trạng thái Auth (đăng nhập/đăng xuất) */
    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** Lấy role của user hiện tại (chuẩn hóa RBAC và có tra cứu chéo stores/drivers) */
    suspend fun getUserRole(): String? {
        val uid = auth.currentUser?.uid ?: return null
        val currentEmail = auth.currentUser?.email.orEmpty().trim()
        if (currentEmail.equals("admin@gmail.com", ignoreCase = true)) {
            try {
                database.getReference("users").child(uid).updateChildren(
                    mapOf(
                        "uid" to uid,
                        "email" to currentEmail,
                        "role" to User.Role.ADMIN,
                        "roleId" to 1,
                        "status" to User.Status.ACTIVE,
                        "isActive" to true,
                        "active" to true,
                        "lockReason" to null,
                        "suspendedReason" to null
                    )
                )
            } catch (_: Exception) {}
            return User.Role.ADMIN
        }

        return try {
            val userData = getCurrentUserData().getOrNull()
            val rawRole = userData?.role?.trim()?.uppercase()
            val roleId = userData?.roleId

            // 1. Kiểm tra nếu là Admin hoặc Tài xế
            if (rawRole == "ADMIN" || roleId == 1) return User.Role.ADMIN
            if (rawRole == "DRIVER" || rawRole == "SHIPPER" || roleId == 4) return User.Role.DRIVER

            // 2. Tra cứu bảng drivers trước: Nếu uid có trong bảng drivers thì chắc chắn là Tài xế (khôi phục nếu từng bị nhảy sang CUSTOMER)
            try {
                val driverSnap = database.getReference("drivers").child(uid).get().await()
                if (driverSnap.exists()) {
                    val driverStoreId = driverSnap.child("storeId").getValue(String::class.java).orEmpty()
                    val updates = mutableMapOf<String, Any>(
                        "role" to User.Role.DRIVER,
                        "roleId" to 4
                    )
                    if (driverStoreId.isNotBlank()) updates["storeId"] = driverStoreId
                    database.getReference("users").child(uid).updateChildren(updates).await()
                    try { database.getReference("customers").child(uid).removeValue() } catch (_: Exception) {}
                    return User.Role.DRIVER
                }
            } catch (_: Exception) {}

            // 3. Nếu đang mang role MERCHANT/MANAGER hoặc có storeId -> xác thực quyền sở hữu thực sự trên bảng stores
            if (rawRole == "MERCHANT" || rawRole == "MANAGER" || roleId == 3 || !userData?.storeId.isNullOrBlank()) {
                val (healedRole, _) = sanitizeCorruptedCustomerRoleIfNeeded(
                    uid = uid,
                    email = currentEmail,
                    currentRole = rawRole.orEmpty(),
                    currentStoreId = userData?.storeId.orEmpty()
                )
                return healedRole.ifBlank { User.Role.CUSTOMER }
            }

            // 4. Nếu là CUSTOMER rõ ràng -> trả về CUSTOMER ngay (không tra cứu stores tránh gán nhầm quán)
            if (rawRole == "CUSTOMER" || roleId == 2) return User.Role.CUSTOMER

            // 4. Tra cứu chéo: Chỉ khớp khi merchantId hoặc ownerId của store bằng đúng uid
            try {
                val byMerchant = database.getReference("stores").orderByChild("merchantId").equalTo(uid).get().await()
                for (child in byMerchant.children) {
                    val merchantId = child.child("merchantId").getValue(String::class.java)
                        ?: child.child("ownerId").getValue(String::class.java)
                    if (merchantId == uid) {
                        val storeId = child.key ?: ""
                        if (storeId.isNotBlank()) {
                            database.getReference("users").child(uid).updateChildren(
                                mapOf("role" to User.Role.MERCHANT, "roleId" to 3, "storeId" to storeId)
                            ).await()
                            return User.Role.MERCHANT
                        }
                    }
                }
            } catch (_: Exception) {}

            // 3. Tra cứu chéo: Người dùng có hồ sơ tài xế không?
            try {
                val driverSnap = database.getReference("drivers").child(uid).get().await()
                if (driverSnap.exists()) {
                    database.getReference("users").child(uid).updateChildren(
                        mapOf("role" to User.Role.DRIVER, "roleId" to 4)
                    ).await()
                    return User.Role.DRIVER
                }
            } catch (_: Exception) {}

            // 4. Fallback theo email nếu email có chứa từ khóa
            val email = auth.currentUser?.email
            if (email != null) {
                when {
                    email.contains("manager", ignoreCase = true) || email.contains("merchant", ignoreCase = true) -> return User.Role.MERCHANT
                    email.contains("shipper", ignoreCase = true) || email.contains("driver", ignoreCase = true) -> return User.Role.DRIVER
                    email.equals("admin@gmail.com", ignoreCase = true) -> return User.Role.ADMIN
                }
            }

            userData?.role ?: User.Role.CUSTOMER
        } catch (e: Exception) {
            val email = auth.currentUser?.email ?: return null
            when {
                email.contains("manager", ignoreCase = true) || email.contains("merchant", ignoreCase = true) -> User.Role.MERCHANT
                email.contains("shipper", ignoreCase = true) || email.contains("driver", ignoreCase = true) -> User.Role.DRIVER
                email.equals("admin@gmail.com", ignoreCase = true) -> User.Role.ADMIN
                else -> User.Role.CUSTOMER
            }
        }
    }

    /** Thiết lập hoặc chuyển đổi vai trò cho tài khoản hiện tại */
    suspend fun setUserRole(newRole: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Chưa đăng nhập"))
        return try {
            val normalizedRole = when (newRole.uppercase()) {
                "MERCHANT", "MANAGER" -> User.Role.MERCHANT
                "DRIVER", "SHIPPER" -> User.Role.DRIVER
                "ADMIN" -> User.Role.ADMIN
                else -> User.Role.CUSTOMER
            }
            val roleId = when (normalizedRole) {
                User.Role.ADMIN -> 1
                User.Role.MERCHANT -> 3
                User.Role.DRIVER -> 4
                else -> 2
            }

            val updates = mutableMapOf<String, Any>(
                "role" to normalizedRole,
                "roleId" to roleId
            )

            if (normalizedRole == User.Role.MERCHANT) {
                val byMerchantSnap = database.getReference("stores").orderByChild("merchantId").equalTo(uid).get().await()
                val storeSnap = if (byMerchantSnap.hasChildren()) byMerchantSnap else database.getReference("stores").orderByChild("ownerId").equalTo(uid).get().await()
                var foundStoreId: String? = null
                for (child in storeSnap.children) {
                    val mId = child.child("merchantId").getValue(String::class.java)
                        ?: child.child("ownerId").getValue(String::class.java)
                    if (mId == uid) {
                        foundStoreId = child.key
                        break
                    }
                }

                val finalStoreId = foundStoreId ?: "store_${uid.take(8)}"
                updates["storeId"] = finalStoreId

                if (foundStoreId == null) {
                    val store = com.chuyen_de_2.foodorder.core.data.model.Store(
                        id = finalStoreId,
                        merchantId = uid,
                        name = auth.currentUser?.displayName?.ifBlank { "Quán Ăn Của Tôi" } ?: "Quán Ăn Của Tôi",
                        storeStatus = com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.OPEN
                    )
                    database.getReference("stores").child(finalStoreId).setValue(store).await()
                }
            } else if (normalizedRole == User.Role.DRIVER) {
                val driverSnap = database.getReference("drivers").child(uid).get().await()
                if (driverSnap.exists()) {
                    val dStoreId = driverSnap.child("storeId").getValue(String::class.java).orEmpty()
                    if (dStoreId.isNotBlank()) {
                        updates["storeId"] = dStoreId
                    }
                } else {
                    val driver = Driver(
                        driverId = uid,
                        storeId = "",
                        driverCode = "TX-" + (10..99).random(),
                        name = auth.currentUser?.displayName?.ifBlank { "Tài xế Oishi" } ?: "Tài xế Oishi",
                        dutyStatus = Driver.DutyStatus.AVAILABLE
                    )
                    database.getReference("drivers").child(uid).setValue(driver).await()
                }
                try { database.getReference("customers").child(uid).removeValue() } catch (_: Exception) {}
            }

            database.getReference("users").child(uid).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Cập nhật FCM Token cho user */
    suspend fun updateFcmToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        database.getReference("users")
            .child(uid)
            .child("fcmToken")
            .setValue(token)
            .await()
    }

    /** Đọc trực tiếp trạng thái user từ Firebase RTDB REST API để bỏ qua SQLite Persistence Cache cũ */
    suspend fun fetchFreshUserJsonFromServer(uid: String): org.json.JSONObject? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: return@withContext null
                val urlStr = "${com.chuyen_de_2.foodorder.core.util.Constants.FIREBASE_DATABASE_URL}/users/$uid.json?auth=$token"
                val conn = (java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    useCaches = false
                    setRequestProperty("Cache-Control", "no-cache")
                }
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
                    if (body.isNotEmpty() && body != "null") {
                        return@withContext org.json.JSONObject(body)
                    }
                }
                null
            } catch (_: Exception) {
                null
            }
        }

    /** Kiểm tra xem tài khoản có đang bị khóa (LOCKED / SUSPENDED) trên Firebase Realtime DB không */
    suspend fun checkAccountActiveStatus(uid: String? = currentUser?.uid): Result<Boolean> {
        val targetUid = uid ?: return Result.failure(Exception("Chưa đăng nhập"))
        val currentEmail = auth.currentUser?.email.orEmpty().trim()
        if (currentEmail.equals("admin@gmail.com", ignoreCase = true)) {
            return Result.success(true)
        }
        return try {
            val freshJson = fetchFreshUserJsonFromServer(targetUid)
            if (freshJson != null) {
                val status = freshJson.optString("status", "ACTIVE").uppercase()
                val isActive = when {
                    freshJson.has("isActive") && !freshJson.isNull("isActive") -> freshJson.optBoolean("isActive", true)
                    freshJson.has("active") && !freshJson.isNull("active") -> freshJson.optBoolean("active", true)
                    else -> true
                }
                if (status == "LOCKED" || status == "SUSPENDED" || status == "INACTIVE" || !isActive) {
                    val reason = freshJson.optString("lockReason", "").ifBlank {
                        freshJson.optString("suspendedReason", "").ifBlank { "Tài khoản của bạn đã bị khóa bởi Quản trị viên" }
                    }
                    try { auth.signOut() } catch (_: Exception) {}
                    return Result.failure(Exception(reason))
                } else {
                    // Đồng bộ trạng thái đã mở khóa xuống local cache
                    try {
                        database.getReference("users").child(targetUid).updateChildren(
                            mapOf(
                                "status" to User.Status.ACTIVE,
                                "isActive" to true,
                                "lockReason" to null,
                                "suspendedReason" to null
                            )
                        )
                    } catch (_: Exception) {}
                    return Result.success(true)
                }
            }

            val userSnap = database.getReference("users").child(targetUid).get().await()
            if (userSnap.exists()) {
                val status = userSnap.child("status").getValue(String::class.java)?.uppercase() ?: "ACTIVE"
                val isActive = userSnap.child("isActive").getValue(Boolean::class.java)
                    ?: userSnap.child("active").getValue(Boolean::class.java)
                    ?: true
                if (status == "LOCKED" || status == "SUSPENDED" || status == "INACTIVE" || !isActive) {
                    val reason = userSnap.child("lockReason").getValue(String::class.java)
                        ?: userSnap.child("suspendedReason").getValue(String::class.java)
                        ?: "Tài khoản của bạn đã bị khóa bởi Quản trị viên"
                    try { auth.signOut() } catch (_: Exception) {}
                    return Result.failure(Exception(reason))
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.success(true)
        }
    }

    /** Đăng xuất an toàn tuyệt đối, không ghi đè trạng thái LOCKED trên Firebase */
    fun logout() {
        try {
            auth.signOut()
        } catch (_: Exception) {}
    }

    /**
     * Xóa vĩnh viễn tài khoản hiện tại và cascading xóa sạch dữ liệu liên quan:
     * - Xóa giỏ hàng
     * - Xóa hồ sơ customers/drivers/users
     * - Thu hồi các đơn đang nhận (nếu là shipper)
     * - Xóa tài khoản Firebase Auth
     */
    suspend fun deleteCurrentAccount(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("Chưa đăng nhập"))
        val uid = user.uid

        return try {
            // 1. Xóa giỏ hàng
            try { database.getReference("carts").child(uid).removeValue().await() } catch (_: Exception) {}

            // 2. Xóa hồ sơ chuyên biệt
            try { database.getReference("customers").child(uid).removeValue().await() } catch (_: Exception) {}
            try { database.getReference("drivers").child(uid).removeValue().await() } catch (_: Exception) {}

            // 3. Thu hồi các đơn đang nhận nếu user này là shipper
            try {
                val ordersSnap = database.getReference("orders")
                    .orderByChild("shipperId")
                    .equalTo(uid)
                    .get()
                    .await()

                for (child in ordersSnap.children) {
                    val order = child.getValue(com.chuyen_de_2.foodorder.core.data.model.Order::class.java) ?: continue
                    if (order.status != com.chuyen_de_2.foodorder.core.data.model.Order.Status.COMPLETED &&
                        order.status != com.chuyen_de_2.foodorder.core.data.model.Order.Status.CANCELLED &&
                        order.status != com.chuyen_de_2.foodorder.core.data.model.Order.Status.BOOM
                    ) {
                        val updates = mapOf<String, Any?>(
                            "shipperId" to null,
                            "shipperName" to "",
                            "status" to com.chuyen_de_2.foodorder.core.data.model.Order.Status.READY
                        )
                        database.getReference("orders").child(order.orderId).updateChildren(updates).await()
                        database.getReference("tracking").child(order.orderId).removeValue().await()
                    }
                }
            } catch (_: Exception) {}

            // 4. Xóa khỏi users & sổ địa chỉ
            try { database.getReference("customer_addresses").child(uid).removeValue().await() } catch (_: Exception) {}
            try { database.getReference("users").child(uid).removeValue().await() } catch (_: Exception) {}

            // 5. Xóa tài khoản Firebase Authentication
            user.delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Khôi phục role DRIVER nếu tài khoản có hồ sơ trong bảng drivers,
     * hoặc khôi phục role CUSTOMER nếu tài khoản khách hàng từng bị gán nhầm storeId của quán khác.
     */
    private suspend fun sanitizeCorruptedCustomerRoleIfNeeded(
        uid: String,
        email: String,
        currentRole: String,
        currentStoreId: String
    ): Pair<String, String> {
        val normRole = currentRole.trim().uppercase()
        val sid = currentStoreId.trim()
        return try {
            // 0. Kiểm tra trước xem uid có hồ sơ trong bảng drivers không (kể cả khi từng bị nhảy nhầm sang CUSTOMER)
            val driverSnap = try { database.getReference("drivers").child(uid).get().await() } catch (_: Exception) { null }
            if (driverSnap != null && driverSnap.exists()) {
                val driverStoreId = driverSnap.child("storeId").getValue(String::class.java).orEmpty().ifBlank { sid }
                if (normRole != User.Role.DRIVER || sid != driverStoreId) {
                    val updates = mutableMapOf<String, Any>(
                        "role" to User.Role.DRIVER,
                        "roleId" to 4
                    )
                    if (driverStoreId.isNotBlank()) updates["storeId"] = driverStoreId
                    database.getReference("users").child(uid).updateChildren(updates).await()
                    try { database.getReference("customers").child(uid).removeValue() } catch (_: Exception) {}
                }
                return Pair(User.Role.DRIVER, driverStoreId)
            }

            if (normRole == User.Role.DRIVER || normRole == User.Role.SHIPPER || normRole == User.Role.ADMIN) {
                return Pair(currentRole, currentStoreId)
            }
            if (normRole != User.Role.MERCHANT && normRole != User.Role.MANAGER && sid.isBlank()) {
                return Pair(currentRole, currentStoreId)
            }

            // 1. Kiểm tra nếu storeId hiện tại thực sự thuộc sở hữu của uid (merchantId == uid hoặc ownerId == uid)
            if (sid.isNotBlank()) {
                val storeSnap = database.getReference("stores").child(sid).get().await()
                if (storeSnap.exists()) {
                    val mId = storeSnap.child("merchantId").getValue(String::class.java).orEmpty()
                    val oId = storeSnap.child("ownerId").getValue(String::class.java).orEmpty()
                    if (mId == uid || oId == uid) {
                        return Pair(User.Role.MERCHANT, sid)
                    }
                }
            }

            // 2. Kiểm tra xem uid có thực sự sở hữu bất kỳ store nào trên bảng stores không
            val byMerchant = try {
                database.getReference("stores").orderByChild("merchantId").equalTo(uid).get().await()
            } catch (_: Exception) { null }
            val realOwnedKey = byMerchant?.children?.firstOrNull {
                it.child("merchantId").getValue(String::class.java) == uid ||
                    it.child("ownerId").getValue(String::class.java) == uid
            }?.key
            if (!realOwnedKey.isNullOrBlank()) {
                database.getReference("users").child(uid).updateChildren(
                    mapOf("role" to User.Role.MERCHANT, "roleId" to 3, "storeId" to realOwnedKey)
                ).await()
                return Pair(User.Role.MERCHANT, realOwnedKey)
            }

            // 3. Nếu user có hồ sơ trong bảng customers hoặc email không phải tài khoản quản lý quán -> khôi phục về CUSTOMER
            val custSnap = try { database.getReference("customers").child(uid).get().await() } catch (_: Exception) { null }
            val hasCustomerRecord = custSnap != null && custSnap.exists()
            val isEmailMerchant = email.contains("manager", ignoreCase = true) ||
                email.contains("merchant", ignoreCase = true) ||
                email.startsWith("quan", ignoreCase = true) ||
                email.startsWith("shop", ignoreCase = true)

            if (hasCustomerRecord || !isEmailMerchant) {
                database.getReference("users").child(uid).updateChildren(
                    mapOf(
                        "role" to User.Role.CUSTOMER,
                        "roleId" to 2,
                        "storeId" to ""
                    )
                ).await()
                return Pair(User.Role.CUSTOMER, "")
            }

            Pair(currentRole, currentStoreId)
        } catch (_: Exception) {
            Pair(currentRole, currentStoreId)
        }
    }

    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Kiểm tra đã đăng nhập chưa */
    val isLoggedIn: Boolean get() = auth.currentUser != null
}
