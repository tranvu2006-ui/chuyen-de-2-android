package com.chuyen_de_2.foodorder.customer.ui.tracking

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.data.model.TrackingLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tracking ViewModel — lắng nghe GPS realtime (Section 5 - doc-adr)
 * API Contract: GET tracking/{orderId} (SDK addValueEventListener)
 * Response: Stream trả về liên tục {lat, lng, updatedAt}
 */
@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val firebaseRepository: FirebaseRepository
) : ViewModel() {

    private val _location = MutableLiveData<TrackingLocation?>()
    val location: LiveData<TrackingLocation?> = _location

    private val _order = MutableLiveData<Order?>()
    val order: LiveData<Order?> = _order

    fun startTracking(orderId: String) {
        // Lắng nghe vị trí shipper
        viewModelScope.launch {
            firebaseRepository.observeTracking(orderId).collectLatest { tracking ->
                _location.value = tracking
            }
        }

        // Lắng nghe trạng thái đơn hàng
        viewModelScope.launch {
            firebaseRepository.observeOrder(orderId).collectLatest { order ->
                _order.value = order
            }
        }
    }
}
