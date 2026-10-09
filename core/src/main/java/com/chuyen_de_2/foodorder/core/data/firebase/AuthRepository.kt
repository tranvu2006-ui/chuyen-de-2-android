package com.chuyen_de_2.foodorder.core.data.firebase

import com.chuyen_de_2.foodorder.core.data.model.Customer
import com.chuyen_de_2.foodorder.core.data.model.Driver
import com.chuyen_de_2.foodorder.core.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository xử lý xác thực — Firebase Authentication
 * Hỗ trợ: Login Email/Password, Register, Get current user, Get user role
 */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) {
    /** Đăng nhập bằng Email/Password */
    suspend fun login(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            result.user?.let { Result.success(it) }
                ?: Result.failure(Exception("Đăng nhập thất bại"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Đăng ký tài khoản mới và phân loại lưu vào Firebase DB theo Actor */
    suspend fun register(
        name: String,
        email: String,
        password: String,
        role: String = User.Role.CUSTOMER
    ): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: return Result.failure(Exception("Đăng ký thất bại"))

            // Chuẩn hóa role RBAC
            val normalizedRole = when (role.uppercase()) {
                "MERCHANT", "MANAGER" -> User.Role.MERCHANT
                "DRIVER", "SHIPPER" -> User.Role.DRIVER
                "ADMIN" -> User.Role.ADMIN
                else -> User.Role.CUSTOMER
            }

            // 1. Lưu thông tin tài khoản dùng chung (Bảng 1: users)
            val user = User(
                uid = firebaseUser.uid,
                role = normalizedRole,
                name = name,
                email = email,
                phone = "",
                status = User.Status.ACTIVE
            )
            database.getReference("users")
                .child(firebaseUser.uid)
                .setValue(user)
                .await()

            // 2. Tạo hồ sơ thực thể chuyên biệt theo từng Actor để không bị gộp chung thành 1 loại
            when (normalizedRole) {
                User.Role.CUSTOMER -> {
                    // Bảng 2: customers — Hồ sơ khách hàng & Điểm uy tín
                    val customer = Customer(
                        customerId = firebaseUser.uid,
                        trustScore = 100,
                        totalOrders = 0,
                        completedOrders = 0,
                        boomOrders = 0
                    )
                    database.getReference("customers")
                        .child(firebaseUser.uid)
                        .setValue(customer)
                        .await()
                }
                User.Role.DRIVER -> {
                    // Bảng 10: drivers — Hồ sơ tài xế nội bộ quán
                    val randomCode = "TX-" + (10..99).random()
                    val driver = Driver(
                        driverId = firebaseUser.uid,
                        storeId = "shop_01",
                        driverCode = randomCode,
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
                    val storeId = "store_${firebaseUser.uid.take(8)}"
                    val storeData = mapOf(
                        "id" to storeId,
                        "ownerId" to firebaseUser.uid,
                        "name" to "$name Food & Drink",
                        "phone" to "",
                        "address" to "TP. Hồ Chí Minh",
                        "district" to "Quận 1",
                        "storeStatus" to "OPEN",
                        "penaltyTier" to "NONE",
                        "ratingAvg" to 5.0
                    )
                    database.getReference("stores")
                        .child(storeId)
                        .setValue(storeData)
                        .await()
                }
            }

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

    /** Lấy thông tin User hiện tại từ Firebase DB (hỗ trợ tự động tra cứu theo email nếu lệch UID) */
    suspend fun getCurrentUserData(): Result<User> {
        return try {
            val uid = auth.currentUser?.uid
                ?: return Result.failure(Exception("Chưa đăng nhập"))

            val userRef = database.getReference("users").child(uid)
            val snapshot = userRef.get().await()

            var user = snapshot.getValue(User::class.java)

            // Nếu không tìm thấy theo UID (trường hợp tạo tài khoản ảo trên Auth có UID ngẫu nhiên)
            if (user == null || !snapshot.exists()) {
                val currentEmail = auth.currentUser?.email
                if (!currentEmail.isNullOrBlank()) {
                    val emailQuery = database.getReference("users")
                        .orderByChild("email")
                        .equalTo(currentEmail)
                        .get()
                        .await()

                    val matchedChild = emailQuery.children.firstOrNull()
                    if (matchedChild != null) {
                        user = matchedChild.getValue(User::class.java)
                        // Đồng bộ dữ liệu sang users/{uid} hiện tại
                        if (user != null) {
                            val syncedUser = user.copy(uid = uid)
                            userRef.setValue(syncedUser)
                            user = syncedUser
                        }
                    } else {
                        // Tự động gán role chuẩn hóa theo email tài khoản nội bộ nếu chưa có bản ghi
                        val fallbackRole = when {
                            currentEmail.contains("manager", ignoreCase = true) || currentEmail.contains("merchant", ignoreCase = true) -> User.Role.MERCHANT
                            currentEmail.contains("shipper", ignoreCase = true) || currentEmail.contains("driver", ignoreCase = true) -> User.Role.DRIVER
                            currentEmail.contains("admin", ignoreCase = true) -> User.Role.ADMIN
                            else -> User.Role.CUSTOMER
                        }
                        val roleId = when (fallbackRole) {
                            User.Role.ADMIN -> 1
                            User.Role.MERCHANT -> 3
                            User.Role.DRIVER -> 4
                            else -> 2
                        }
                        val newUser = User(
                            uid = uid,
                            email = currentEmail,
                            name = auth.currentUser?.displayName ?: currentEmail.substringBefore("@"),
                            role = fallbackRole,
                            roleId = roleId,
                            status = User.Status.ACTIVE
                        )
                        userRef.setValue(newUser)
                        user = newUser
                    }
                }
            }

            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("Không tìm thấy dữ liệu user"))
            }
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

    /** Lấy role của user hiện tại (chuẩn hóa RBAC và có fallback thông minh) */
    suspend fun getUserRole(): String? {
        return try {
            val userData = getCurrentUserData().getOrNull()
            val rawRole = userData?.role?.trim()?.uppercase()
            val roleId = userData?.roleId

            when {
                rawRole == "MERCHANT" || rawRole == "MANAGER" || roleId == 3 -> User.Role.MERCHANT
                rawRole == "DRIVER" || rawRole == "SHIPPER" || roleId == 4 -> User.Role.DRIVER
                rawRole == "ADMIN" || roleId == 1 -> User.Role.ADMIN
                rawRole == "CUSTOMER" || roleId == 2 -> User.Role.CUSTOMER
                // Fallback theo email nếu DB chưa có role
                auth.currentUser?.email?.contains("manager", ignoreCase = true) == true -> User.Role.MERCHANT
                auth.currentUser?.email?.contains("merchant", ignoreCase = true) == true -> User.Role.MERCHANT
                auth.currentUser?.email?.contains("shipper", ignoreCase = true) == true -> User.Role.DRIVER
                auth.currentUser?.email?.contains("driver", ignoreCase = true) == true -> User.Role.DRIVER
                auth.currentUser?.email?.contains("admin", ignoreCase = true) == true -> User.Role.ADMIN
                else -> userData?.role ?: User.Role.CUSTOMER
            }
        } catch (e: Exception) {
            val email = auth.currentUser?.email ?: return null
            when {
                email.contains("manager", ignoreCase = true) || email.contains("merchant", ignoreCase = true) -> User.Role.MERCHANT
                email.contains("shipper", ignoreCase = true) || email.contains("driver", ignoreCase = true) -> User.Role.DRIVER
                email.contains("admin", ignoreCase = true) -> User.Role.ADMIN
                else -> User.Role.CUSTOMER
            }
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

    /** Đăng xuất */
    fun logout() {
        auth.currentUser?.uid?.let { uid ->
            database.getReference("users")
                .child(uid)
                .child("status")
                .setValue(User.Status.OFFLINE)
        }
        auth.signOut()
    }

    /** User hiện tại (nullable) */
    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Kiểm tra đã đăng nhập chưa */
    val isLoggedIn: Boolean get() = auth.currentUser != null
}
