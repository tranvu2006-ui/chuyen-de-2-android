package com.chuyen_de_2.foodorder.admin.ui.complaint

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.admin.R
import com.chuyen_de_2.foodorder.admin.databinding.FragmentAdminComplaintListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminComplaintListFragment : Fragment() {

    private var _binding: FragmentAdminComplaintListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminComplaintViewModel by viewModels()
    private lateinit var adapter: AdminComplaintAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminComplaintListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = AdminComplaintAdapter { complaint ->
            val dialog = ResolveComplaintDialogFragment(complaint) { cId, status, penalty, note, storeId ->
                viewModel.resolveComplaint(cId, status, penalty, note, storeId)
            }
            dialog.show(childFragmentManager, ResolveComplaintDialogFragment.TAG)
        }

        binding.rvComplaints.layoutManager = LinearLayoutManager(requireContext())
        binding.rvComplaints.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefreshComplaint.setOnRefreshListener {
            binding.swipeRefreshComplaint.isRefreshing = false
        }

        binding.chipGroupComplaintFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chipPendingComplaints -> viewModel.setFilter(ComplaintFilter.PENDING)
                R.id.chipResolvedComplaints -> viewModel.setFilter(ComplaintFilter.RESOLVED)
                R.id.chipRejectedComplaints -> viewModel.setFilter(ComplaintFilter.REJECTED)
                else -> viewModel.setFilter(ComplaintFilter.ALL)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.filteredComplaints.collect { list ->
                        adapter.submitList(list)
                        binding.tvEmptyComplaint.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.counts.collect { c ->
                        binding.chipAllComplaints.text = "Tất cả (${c.total})"
                        binding.chipPendingComplaints.text = "Chờ xử lý (${c.pending})"
                        binding.chipResolvedComplaints.text = "Đã giải quyết (${c.resolved})"
                        binding.chipRejectedComplaints.text = "Đã bác bỏ (${c.rejected})"
                    }
                }

                launch {
                    viewModel.message.collect { msg ->
                        if (!msg.isNullOrBlank()) {
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                            viewModel.clearMessage()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
