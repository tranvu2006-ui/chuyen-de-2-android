package com.chuyen_de_2.foodorder.internal.ui.auth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chuyen_de_2.foodorder.core.R
import com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion

class AddressSuggestionAdapter(
    private val onSuggestionClick: (AddressSuggestion) -> Unit
) : ListAdapter<AddressSuggestion, AddressSuggestionAdapter.SuggestionViewHolder>(SuggestionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_address_suggestion, parent, false)
        return SuggestionViewHolder(view, onSuggestionClick)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SuggestionViewHolder(
        itemView: View,
        private val onClick: (AddressSuggestion) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivSuggestionIcon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvSuggestionTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSuggestionSubtitle)

        fun bind(item: AddressSuggestion) {
            tvTitle.text = item.title
            tvSubtitle.text = item.subtitle
            itemView.setOnClickListener {
                onClick(item)
            }
        }
    }

    class SuggestionDiffCallback : DiffUtil.ItemCallback<AddressSuggestion>() {
        override fun areItemsTheSame(oldItem: AddressSuggestion, newItem: AddressSuggestion): Boolean {
            return oldItem.fullAddress == newItem.fullAddress
        }

        override fun areContentsTheSame(oldItem: AddressSuggestion, newItem: AddressSuggestion): Boolean {
            return oldItem == newItem
        }
    }
}
