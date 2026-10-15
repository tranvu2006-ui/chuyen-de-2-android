package com.chuyen_de_2.foodorder.internal.ui.ship

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.internal.databinding.FragmentShipperIncomeBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ShipperIncomeFragment : Fragment() {

    private var _binding: FragmentShipperIncomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ShipperViewModel by activityViewModels()
    private val incomeAdapter = ShipperIncomeAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShipperIncomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFilterTabs()
        observeData()
    }

    private fun setupRecyclerView() {
        binding.rvCompletedTrips.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = incomeAdapter
        }
    }

    private fun setupFilterTabs() {
        binding.toggleGroupTimeFilter.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    binding.btnFilterToday.id -> filterTrips("TODAY")
                    binding.btnFilterWeek.id -> filterTrips("WEEK")
                    binding.btnFilterMonth.id -> filterTrips("MONTH")
                }
            }
        }
    }

    private fun observeData() {
        viewModel.assignedOrders.observe(viewLifecycleOwner) { orders ->
            val completed = orders.filter { it.status == Order.Status.COMPLETED }
            updateIncomeUI(completed)
        }
    }

    private fun filterTrips(period: String) {
        val completed = viewModel.assignedOrders.value?.filter { it.status == Order.Status.COMPLETED } ?: emptyList()
        updateIncomeUI(completed)
    }

    private fun updateIncomeUI(completedOrders: List<Order>) {
        if (completedOrders.isNotEmpty()) {
            binding.tvEmptyIncome.visibility = View.GONE
            binding.rvCompletedTrips.visibility = View.VISIBLE
            incomeAdapter.submitList(completedOrders)

            val totalEarn = completedOrders.sumOf { it.shippingFee }
            binding.tvTotalBalance.text = "+${totalEarn.toVndCurrency()}"
            binding.tvTodayIncome.text = "+${totalEarn.toVndCurrency()}"
            binding.tvWeekIncome.text = "+${totalEarn.toVndCurrency()}"
            binding.tvCompletedTripsCount.text = "${completedOrders.size} cuốc"
        } else {
            binding.tvEmptyIncome.visibility = View.VISIBLE
            binding.rvCompletedTrips.visibility = View.GONE
            incomeAdapter.submitList(emptyList())

            binding.tvTotalBalance.text = "0 đ"
            binding.tvTodayIncome.text = "0 đ"
            binding.tvWeekIncome.text = "0 đ"
            binding.tvCompletedTripsCount.text = "0 cuốc"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
