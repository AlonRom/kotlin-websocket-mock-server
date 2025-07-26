package com.example.websocketclient

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.websocketclient.databinding.ItemServerBinding

class ServerAdapter(
    private val onServerConnect: (DiscoveredServer) -> Unit
) : ListAdapter<DiscoveredServer, ServerAdapter.ServerViewHolder>(ServerDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val binding = ItemServerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ServerViewHolder(binding, onServerConnect)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ServerViewHolder(
        private val binding: ItemServerBinding,
        private val onServerConnect: (DiscoveredServer) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(server: DiscoveredServer) {
            binding.serverNameText.text = server.name
            binding.serverUrlText.text = server.wsUrl
            
            binding.connectButton.setOnClickListener {
                onServerConnect(server)
            }
        }
    }

    private class ServerDiffCallback : DiffUtil.ItemCallback<DiscoveredServer>() {
        override fun areItemsTheSame(oldItem: DiscoveredServer, newItem: DiscoveredServer): Boolean {
            return oldItem.wsUrl == newItem.wsUrl
        }

        override fun areContentsTheSame(oldItem: DiscoveredServer, newItem: DiscoveredServer): Boolean {
            return oldItem == newItem
        }
    }
}
