package com.chuyen_de_2.foodorder.internal.ui.ship

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.internal.databinding.FragmentShipperBinding
import com.chuyen_de_2.foodorder.internal.service.LocationForegroundService
import dagger.hilt.android.AndroidEntryPoint

/**
 * ShipperFragment — Nhận cuốc xe, cập nhật trạng thái, toggle GPS (Section 3.3 - doc-adr)
 */
@AndroidEntryPoint
class ShipperFragment : Fragment() {

    private var _binding: FragmentShipperBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ShipperViewModel by viewModels()
    private var isServiceRunning = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocation) {
            startLocationService()
        } else {
            Toast.makeText(requireContext(), "Cần quyền vị trí để giao hàng", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShipperBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGPSToggle()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupGPSToggle() {
        binding.switchGps.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkAndRequestLocationPermission()
            } else {
                stopLocationService()
            }
        }
    }

    private fun setupRecyclerView() {
        binding.rvAssignedOrders.apply {
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun checkAndRequestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                startLocationService()
            }
            else -> {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun startLocationService() {
        val orderId = viewModel.currentDeliveryOrderId.value
        if (orderId == null) {
            Toast.makeText(requireContext(), "Chưa có đơn đang giao", Toast.LENGTH_SHORT).show()
            binding.switchGps.isChecked = false
            return
        }

        val intent = Intent(requireContext(), LocationForegroundService::class.java).apply {
            putExtra(LocationForegroundService.EXTRA_ORDER_ID, orderId)
        }
        ContextCompat.startForegroundService(requireContext(), intent)
        isServiceRunning = true
        binding.tvGpsStatus.text = "📍 GPS đang hoạt động"
    }

    private fun stopLocationService() {
        requireContext().stopService(Intent(requireContext(), LocationForegroundService::class.java))
        isServiceRunning = false
        binding.tvGpsStatus.text = "GPS đã tắt"
    }

    private fun observeViewModel() {
        viewModel.assignedOrders.observe(viewLifecycleOwner) { orders ->
            binding.tvEmpty.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
            // Update adapter
        }

        viewModel.currentDeliveryOrderId.observe(viewLifecycleOwner) { orderId ->
            if (orderId != null) {
                binding.tvCurrentOrder.text = "Đơn hàng #${orderId.takeLast(6).uppercase()}"
                binding.tvCurrentOrderTag.visibility = View.VISIBLE
            } else {
                binding.tvCurrentOrder.text = "Chưa có đơn đang giao"
                binding.tvCurrentOrderTag.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
