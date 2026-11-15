package com.chuyen_de_2.foodorder.internal.ui.chef

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ChefViewModel @Inject constructor(
    private val firebaseRepository: FirebaseRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _chefStore = MutableLiveData<Pair<String, String>>() // storeId, storeName
    val chefStore: LiveData<Pair<String, String>> = _chefStore

    private val _pendingOrders = MutableLiveData<List<Order>>()
    val pendingOrders: LiveData<List<Order>> = _pendingOrders

    private val _activeOrders = MutableLiveData<List<Order>>()
    val activeOrders: LiveData<List<Order>> = _activeOrders

    private val _todayDoneCount = MutableLiveData<Int>(0)
    val todayDoneCount: LiveData<Int> = _todayDoneCount

    private val _chefName = MutableLiveData<String>()
    val chefName: LiveData<String> = _chefName

    private var cachedChefPhone: String = ""

    init {
        loadChefStoreAndOrders()
    }

    fun refresh() {
        loadChefStoreAndOrders()
    }

    private fun loadChefStoreAndOrders() {
        val chefId = auth.currentUser?.uid.orEmpty()
        viewModelScope.launch {
            if (chefId.isNotBlank()) {
                try {
                    val userSnap = firebaseRepository.readCacheFirst(
                        firebaseRepository.database.getReference("users").child(chefId)
                    )
                    val name = userSnap?.child("name")?.getValue(String::class.java).orEmpty().trim()
                    cachedChefPhone = userSnap?.child("phone")?.getValue(String::class.java).orEmpty().trim()
                    _chefName.value = if (name.isNotBlank()) name else (auth.currentUser?.displayName ?: "Đầu Bếp")
                } catch (_: Exception) {
                    _chefName.value = auth.currentUser?.displayName ?: "Đầu Bếp"
                }
            }

            val storeInfo = firebaseRepository.resolveStoreForChef(chefId)
            _chefStore.value = storeInfo
            observeChefOrders(storeInfo.first, chefId)
        }
    }

    private fun observeChefOrders(storeId: String, chefId: String) {
        viewModelScope.launch {
            firebaseRepository.observeAllOrders().collectLatest { allOrders ->
                // Lọc đơn theo storeId của đầu bếp
                val storeOrders = if (storeId.isNotBlank()) {
                    allOrders.filter { it.storeId == storeId }
                } else {
                    allOrders
                }

                // 1. Đơn chờ bếp nhận: Trạng thái PREPARING và chưa có chefId
                val pending = storeOrders.filter { order ->
                    order.status == Order.Status.PREPARING &&
                            (order.chefId.isNullOrBlank() || order.chefId == "null")
                }
                _pendingOrders.value = pending

                // 2. Đơn tôi đang nấu: Trạng thái PREPARING và chefId là của tôi
                val active = storeOrders.filter { order ->
                    order.status == Order.Status.PREPARING && order.chefId == chefId
                }
                _activeOrders.value = active

                // 3. Đơn tôi đã nấu xong hôm nay
                val doneToday = storeOrders.count { order ->
                    order.chefId == chefId &&
                            order.status != Order.Status.PENDING &&
                            order.status != Order.Status.PREPARING &&
                            order.status != Order.Status.CANCELLED
                }
                _todayDoneCount.value = doneToday
            }
        }
    }

    /** Đầu bếp bấm nhận nấu đơn */
    fun acceptOrder(orderId: String, onResult: (Boolean, String) -> Unit) {
        val chefId = auth.currentUser?.uid.orEmpty()
        if (chefId.isBlank()) {
            onResult(false, "Vui lòng đăng nhập lại tài khoản đầu bếp!")
            return
        }

        val name = _chefName.value ?: "Đầu Bếp"
        firebaseRepository.assignChef(orderId, chefId, name, cachedChefPhone) { success, msg ->
            onResult(success, msg)
        }
    }

    /** Đầu bếp bấm hủy nhận nấu đơn */
    fun cancelOrder(orderId: String, onResult: (Boolean, String) -> Unit) {
        firebaseRepository.unassignChef(orderId) { success, msg ->
            onResult(success, msg)
        }
    }

    /** Đầu bếp xác nhận đã nấu xong món */
    fun markCookingDone(orderId: String, onResult: (Boolean, String) -> Unit) {
        val chefId = auth.currentUser?.uid.orEmpty()
        if (chefId.isBlank()) {
            onResult(false, "Vui lòng đăng nhập lại tài khoản đầu bếp!")
            return
        }

        firebaseRepository.markCookingDoneByChef(orderId, chefId) { success, msg ->
            onResult(success, msg)
        }
    }
}
