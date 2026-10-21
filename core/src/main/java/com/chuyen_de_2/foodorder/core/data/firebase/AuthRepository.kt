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
    /** Đăng nhập bằng Email/Password (kiểm tra khóa tài khoản từ Admin & tự động đồng bộ Gmail/Hồ sơ lên Realtime DB) */
    suspend fun login(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: return Result.failure(Exception("Đăng nhập thất bại"))

            val userRef = database.getReference("users").child(firebaseUser.uid)
            val userSnap = userRef.get().await()
            if (userSnap.exists()) {
                val status = userSnap.child("status").getValue(String::class.java)?.uppercase() ?: "ACTIVE"
                val isActive = userSnap.child("isActive").getValue(Boolean::class.java)
                    ?: userSnap.child("active").getValue(Boolean::class.java)
                    ?: true
                if (status == "LOCKED" || status == "SUSPENDED" || !isActive) {
                    val reason = userSnap.child("lockReason").getValue(String::class.java)
                        ?: userSnap.child("suspendedReason").getValue(String::class.java)
                        ?: "Vi phạm quy định nền tảng"
                    auth.signOut()
                    return Result.failure(Exception("Tài khoản đã bị khóa bởi Admin: $reason"))
                }
            }

            // Đồng bộ Gmail & thông tin định danh từ Firebase Auth vào users/{uid} để Admin Dashboard nhận diện chính xác
            try {
                val actualEmail = firebaseUser.email?.takeIf { it.isNotBlank() } ?: email.trim()
                val dbEmail = userSnap.child("email").getValue(String::class.java).orEmpty()
                val dbName = userSnap.child("name").getValue(String::class.java).orEmpty()
                val dbPhone = userSnap.child("phone").getValue(String::class.java).orEmpty()
                val dbRole = userSnap.child("role").getValue(String::class.java).orEmpty()
                val dbStoreId = userSnap.child("storeId").getValue(String::class.java).orEmpty()

                val wasCorruptedMock = dbEmail == "mai.tran@oishifood.vn" && !actualEmail.equals("mai.tran@oishifood.vn", ignoreCase = true)
                val resolvedRole = when {
                    dbRole.isNotBlank() -> dbRole.uppercase()
                    actualEmail.contains("manager", ignoreCase = true) || actualEmail.contains("merchant", ignoreCase = true) -> User.Role.MERCHANT
                    actualEmail.contains("shipper", ignoreCase = true) || actualEmail.contains("driver", ignoreCase = true) -> User.Role.DRIVER
                    actualEmail.contains("admin", ignoreCase = true) -> User.Role.ADMIN
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

                val existingStatus = userSnap.child("status").getValue(String::class.java)?.uppercase() ?: User.Status.ACTIVE
                val existingIsActive = userSnap.child("isActive").getValue(Boolean::class.java)
                    ?: userSnap.child("active").getValue(Boolean::class.java)
                    ?: true

                val syncUpdates = mutableMapOf<String, Any>(
                    "uid" to firebaseUser.uid,
                    "email" to actualEmail,
                    "name" to resolvedName,
                    "phone" to resolvedPhone,
                    "role" to resolvedRole,
                    "roleId" to resolvedRoleId,
                    "status" to existingStatus,
                    "isActive" to existingIsActive,
                    "active" to existingIsActive
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

            Result.success(firebaseUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Đăng ký tài khoản mới và phân loại lưu vào Firebase DB theo Actor */
    suspend fun register(
        name: String,
        email: String,
        password: String,
        role: String = User.Role.CUSTOMER,
        phone: String = ""
    ): Result<FirebaseUser> {
        return try {
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
            } else if (normalizedRole == User.Role.DRIVER) {
                "shop_01"
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
                phone = phone.trim(),
                status = User.Status.ACTIVE,
                isActive = true,
                storeId = assignedStoreId
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
                        "phone" to phone.trim(),
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
                        storeId = assignedStoreId.ifBlank { "shop_01" },
                        driverCode = randomCode,
                        name = name.trim(),
                        phone = phone.trim(),
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
                        name = if (name.isNotBlank()) "$name Food" else "Quán Ăn Mới",
                        ownerName = name.trim(),
                        phone = phone.trim(),
                        address = "123 Nguyễn Trãi, Q.1, TP.HCM",
                        district = "Quận 1",
                        storeStatus = com.chuyen_de_2.foodorder.core.data.model.Store.StoreStatus.OPEN,
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
                currentEmail.contains("admin", ignoreCase = true) -> User.Role.ADMIN
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
                    val resolvedRole = user.role.ifBlank { fallbackRole }
                    val resolvedRoleId = if (user.roleId > 0) user.roleId else fallbackRoleId

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
    suspend fun updateUserProfile(name: String, phone: String, address: String = ""): Result<Unit> {
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

    /** Kiểm tra xem tài khoản có đang bị khóa (LOCKED / SUSPENDED) trên Firebase Realtime DB không */
    suspend fun checkAccountActiveStatus(uid: String? = currentUser?.uid): Result<Boolean> {
        val targetUid = uid ?: return Result.failure(Exception("Chưa đăng nhập"))
        return try {
            val userSnap = database.getReference("users").child(targetUid).get().await()
            if (userSnap.exists()) {
                val status = userSnap.child("status").getValue(String::class.java)?.uppercase() ?: "ACTIVE"
                val isActive = userSnap.child("isActive").getValue(Boolean::class.java)
                    ?: userSnap.child("active").getValue(Boolean::class.java)
                    ?: true
                if (status == "LOCKED" || status == "SUSPENDED" || !isActive) {
                    val reason = userSnap.child("lockReason").getValue(String::class.java)
                        ?: userSnap.child("suspendedReason").getValue(String::class.java)
                        ?: "Tài khoản của bạn đã bị khóa bởi Quản trị viên"
                    logout()
                    return Result.failure(Exception(reason))
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.success(true)
        }
    }

    /** Đăng xuất an toàn tuyệt đối, tránh crash do Firebase permission */
    fun logout() {
        try {
            auth.currentUser?.uid?.let { uid ->
                database.getReference("users")
                    .child(uid)
                    .child("status")
                    .setValue(User.Status.OFFLINE)
            }
        } catch (_: Exception) {}
        try {
            auth.signOut()
        } catch (_: Exception) {}
    }

    /** User hiện tại (nullable) */
    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Kiểm tra đã đăng nhập chưa */
    val isLoggedIn: Boolean get() = auth.currentUser != null
}
