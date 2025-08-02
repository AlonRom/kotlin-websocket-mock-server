package com.example.websocketclient

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.websocketclient.databinding.ItemMessageBinding

class MessagesAdapter : ListAdapter<WebSocketMessage, MessagesAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MessageViewHolder(private val binding: ItemMessageBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: WebSocketMessage) {
            binding.messageText.text = message.message
            binding.timestampText.text = message.getFormattedTime()
            
            // Set icon based on message type using web icons
            when (message.type) {
                MessageType.CLIENT -> binding.messageIcon.text = "👤"
                MessageType.SERVER -> binding.messageIcon.text = "🗄️"
            }
        }
    }

    private class MessageDiffCallback : DiffUtil.ItemCallback<WebSocketMessage>() {
        override fun areItemsTheSame(oldItem: WebSocketMessage, newItem: WebSocketMessage): Boolean {
            return oldItem.timestamp == newItem.timestamp
        }

        override fun areContentsTheSame(oldItem: WebSocketMessage, newItem: WebSocketMessage): Boolean {
            return oldItem == newItem
        }
    }
} 