package com.chuyen_de_2.foodorder.admin.ui.complaint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Complaint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ComplaintFilter {
    ALL, PENDING, RESOLVED, REJECTED
}

data class ComplaintCounts(
    val total: Int = 0,
    val pending: Int = 0,
    val resolved: Int = 0,
    val rejected: Int = 0
)

@HiltViewModel
class AdminComplaintViewModel @Inject constructor(
    private val firebaseRepo: FirebaseRepository
) : ViewModel() {

    private val _rawComplaints = MutableStateFlow<List<Complaint>>(emptyList())
    private val _filter = MutableStateFlow(ComplaintFilter.ALL)

    private val _filteredComplaints = MutableStateFlow<List<Complaint>>(emptyList())
    val filteredComplaints: StateFlow<List<Complaint>> = _filteredComplaints.asStateFlow()

    private val _counts = MutableStateFlow(ComplaintCounts())
    val counts: StateFlow<ComplaintCounts> = _counts.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        observeComplaints()
    }

    private fun observeComplaints() {
        viewModelScope.launch {
            firebaseRepo.observeAllComplaints().collect { list ->
                _rawComplaints.value = list
                updateCounts(list)
                applyFilter()
            }
        }
    }

    private fun updateCounts(list: List<Complaint>) {
        val total = list.size
        val pending = list.count { it.status.equals(Complaint.ComplaintStatus.PENDING, ignoreCase = true) }
        val resolved = list.count { it.status.equals(Complaint.ComplaintStatus.RESOLVED, ignoreCase = true) }
        val rejected = list.count { it.status.equals(Complaint.ComplaintStatus.REJECTED, ignoreCase = true) }
        _counts.value = ComplaintCounts(total, pending, resolved, rejected)
    }

    fun setFilter(filter: ComplaintFilter) {
        _filter.value = filter
        applyFilter()
    }

    private fun applyFilter() {
        val list = _rawComplaints.value
        val filter = _filter.value

        _filteredComplaints.value = list.filter { complaint ->
            when (filter) {
                ComplaintFilter.ALL -> true
                ComplaintFilter.PENDING -> complaint.status.equals(Complaint.ComplaintStatus.PENDING, ignoreCase = true)
                ComplaintFilter.RESOLVED -> complaint.status.equals(Complaint.ComplaintStatus.RESOLVED, ignoreCase = true)
                ComplaintFilter.REJECTED -> complaint.status.equals(Complaint.ComplaintStatus.REJECTED, ignoreCase = true)
            }
        }
    }

    fun resolveComplaint(
        complaintId: String,
        status: String,
        penaltyTier: String,
        adminNote: String,
        storeId: String
    ) {
        viewModelScope.launch {
            val result = firebaseRepo.resolveComplaint(
                complaintId = complaintId,
                status = status,
                penaltyApplied = penaltyTier,
                adminNote = adminNote,
                storeId = storeId
            )
            if (result.isSuccess) {
                _message.value = "Đã cập nhật quyết định xử lý khiếu nại thành công!"
            } else {
                _message.value = "Xử lý thất bại: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
