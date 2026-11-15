package com.chuyen_de_2.foodorder.internal.ui.chef

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.core.data.model.Chef
import com.chuyen_de_2.foodorder.internal.databinding.ItemChefManageBinding

class ChefAdapter(
    private val onCallClick: (String) -> Unit,
    private val onDeleteClick: (Chef) -> Unit
) : ListAdapter<Chef, ChefAdapter.ChefViewHolder>(ChefDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChefViewHolder {
        val binding = ItemChefManageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChefViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChefViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChefViewHolder(
        private val binding: ItemChefManageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chef: Chef) {
            binding.tvChefName.text = chef.name.ifBlank { "Đầu Bếp" }
            binding.tvChefTitleBadge.text = chef.title.ifBlank { "Bếp" }
            binding.tvChefPhone.text = "📞 ${chef.phone.ifBlank { "Chưa cập nhật" }}"
            binding.tvChefEmail.text = "✉ ${chef.email.ifBlank { "Chưa cập nhật" }}"

            val count = chef.activeOrdersCount
            binding.tvChefCookingSummary.text = if (count > 0) "🔥 Đang nấu: $count đơn" else "✓ Sẵn sàng nhận nấu"

            binding.btnCallChef.setOnClickListener {
                if (chef.phone.isNotBlank()) {
                    onCallClick(chef.phone)
                }
            }

            binding.btnDeleteChef.setOnClickListener {
                onDeleteClick(chef)
            }
        }
    }

    class ChefDiffCallback : DiffUtil.ItemCallback<Chef>() {
        override fun areItemsTheSame(oldItem: Chef, newItem: Chef): Boolean {
            return oldItem.chefId == newItem.chefId
        }

        override fun areContentsTheSame(oldItem: Chef, newItem: Chef): Boolean {
            return oldItem == newItem
        }
    }
}
