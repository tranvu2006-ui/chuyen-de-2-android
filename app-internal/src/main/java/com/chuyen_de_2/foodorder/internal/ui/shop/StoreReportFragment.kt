package com.chuyen_de_2.foodorder.internal.ui.shop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.internal.databinding.FragmentStoreReportBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * Báo Cáo Doanh Số & Hiệu Quả Bán Hàng theo Quán/7.html
 * - Thống kê doanh thu theo thời gian: Hôm nay, Tuần này, Tháng này
 * - Số lượng đơn hoàn tất & đơn hủy/bùng
 * - Top các món bán chạy nhất của quán
 * - Tổng hợp đánh giá và độ uy tín sao ⭐
 */
@AndroidEntryPoint
class StoreReportFragment : Fragment() {

    private var _binding: FragmentStoreReportBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    @Inject
    lateinit var authRepository: AuthRepository

    private val bestSellerAdapter = BestSellerDishAdapter()
    private var allStoreOrders: List<Order> = emptyList()
    private var currentPeriod = "TODAY"
    private var currentStoreId = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStoreReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupTimeFilterTabs()
        loadStoreAndObserveData()
    }

    private fun setupRecyclerView() {
        binding.rvBestSellerDishes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = bestSellerAdapter
        }
    }

    private fun setupTimeFilterTabs() {
        binding.toggleGroupTimeFilter.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentPeriod = when (checkedId) {
                    binding.btnFilterToday.id -> "TODAY"
                    binding.btnFilterWeek.id -> "WEEK"
                    binding.btnFilterMonth.id -> "MONTH"
                    else -> "TODAY"
                }
                computeAndDisplayMetrics()
            }
        }
    }

    private fun loadStoreAndObserveData() {
        val uid = authRepository.currentUser?.uid.orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            currentStoreId = firebaseRepository.resolveStoreIdForUser(uid)

            // Lắng nghe thông tin quán để hiển thị đánh giá sao ⭐
            if (currentStoreId.isNotBlank()) {
                launch {
                    firebaseRepository.observeStore(currentStoreId).collectLatest { store ->
                        if (store != null) {
                            val rating = if (store.ratingAvg > 0) store.ratingAvg else 4.9f
                            binding.tvRatingScore.text = String.format("%.1f", rating)
                        }
                    }
                }
            }

            // Lắng nghe toàn bộ đơn hàng của quán
            launch {
                firebaseRepository.observeAllOrders().collectLatest { orders ->
                    allStoreOrders = if (currentStoreId.isNotBlank()) {
                        orders.filter { it.shopId == currentStoreId }
                    } else {
                        orders
                    }
                    computeAndDisplayMetrics()
                }
            }
        }
    }

    private fun computeAndDisplayMetrics() {
        val now = Calendar.getInstance()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfWeek = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val filteredOrders = when (currentPeriod) {
            "TODAY" -> allStoreOrders.filter {
                val t = if (it.createdAt > 0) it.createdAt else it.timestamp
                t >= startOfToday || t == 0L
            }
            "WEEK" -> allStoreOrders.filter {
                val t = if (it.createdAt > 0) it.createdAt else it.timestamp
                t >= startOfWeek || t == 0L
            }
            "MONTH" -> allStoreOrders.filter {
                val t = if (it.createdAt > 0) it.createdAt else it.timestamp
                t >= startOfMonth || t == 0L
            }
            else -> allStoreOrders
        }

        val completedOrders = filteredOrders.filter { it.status == Order.Status.COMPLETED }
        val cancelledOrders = filteredOrders.filter {
            it.status == Order.Status.CANCELLED || it.status == Order.Status.BOOM
        }

        // Tính doanh thu
        val totalRevenue = completedOrders.sumOf {
            if (it.totalPayment > 0) it.totalPayment else if (it.total > 0) it.total else 0L
        }
        val avgOrderValue = if (completedOrders.isNotEmpty()) totalRevenue / completedOrders.size else 0L

        binding.tvRevenueAmount.text = totalRevenue.toVndCurrency()
        binding.tvCompletedOrdersCount.text = "${completedOrders.size} đơn"
        binding.tvCancelledOrdersCount.text = "${cancelledOrders.size} đơn"
        binding.tvAverageOrderValue.text = avgOrderValue.toVndCurrency()

        // Tính top món bán chạy nhất
        val dishSalesMap = mutableMapOf<String, Pair<Int, Long>>() // name -> (totalQty, price)
        completedOrders.forEach { order ->
            order.items.values.forEach { item ->
                val existing = dishSalesMap[item.dishName]
                val currentQty = existing?.first ?: 0
                val price = if (item.price > 0) item.price else 50000L
                dishSalesMap[item.dishName] = Pair(currentQty + item.quantity, price)
            }
        }

        if (dishSalesMap.isNotEmpty()) {
            val sortedList = dishSalesMap.entries
                .sortedByDescending { it.value.first }
                .mapIndexed { index, entry ->
                    BestSellerItem(
                        rank = index + 1,
                        dishName = entry.key,
                        soldQuantity = entry.value.first,
                        price = entry.value.second
                    )
                }

            binding.tvEmptyBestSellers.visibility = View.GONE
            binding.rvBestSellerDishes.visibility = View.VISIBLE
            bestSellerAdapter.submitList(sortedList)
        } else {
            binding.tvEmptyBestSellers.visibility = View.VISIBLE
            binding.rvBestSellerDishes.visibility = View.GONE
            bestSellerAdapter.submitList(emptyList())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
