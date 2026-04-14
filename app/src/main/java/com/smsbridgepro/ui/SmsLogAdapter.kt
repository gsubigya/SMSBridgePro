package com.smsbridgepro.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smsbridgepro.R
import com.smsbridgepro.databinding.ItemSmsLogBinding
import com.smsbridgepro.model.SmsLogItem
import java.text.SimpleDateFormat
import java.util.*

class SmsLogAdapter : ListAdapter<SmsLogItem, SmsLogAdapter.LogViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemSmsLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LogViewHolder(private val binding: ItemSmsLogBinding) : RecyclerView.ViewHolder(binding.root) {
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        fun bind(item: SmsLogItem) {
            binding.tvLogPhone.text = item.phone
            binding.tvLogText.text = item.text
            binding.tvLogTime.text = timeFormat.format(Date(item.timestamp))
            
            if (item.isSuccess) {
                binding.tvLogStatus.text = "SENT"
                binding.tvLogStatus.setTextColor(itemView.context.getColor(R.color.accent_cyan))
                binding.tvLogError.visibility = View.GONE
            } else {
                binding.tvLogStatus.text = "FAILED"
                binding.tvLogStatus.setTextColor(0xFFFF5252.toInt())
                binding.tvLogError.text = item.errorMessage
                binding.tvLogError.visibility = View.VISIBLE
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SmsLogItem>() {
        override fun areItemsTheSame(oldItem: SmsLogItem, newItem: SmsLogItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SmsLogItem, newItem: SmsLogItem) = oldItem == newItem
    }
}
