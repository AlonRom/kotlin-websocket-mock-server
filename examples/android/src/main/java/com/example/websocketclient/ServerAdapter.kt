package com.example.websocketclient

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class ServerAdapter(
    private val onServerClick: (DiscoveredServer) -> Unit
) : ListAdapter<DiscoveredServer, ServerAdapter.ServerViewHolder>(ServerDiffCallback()) {

    override fun submitList(list: List<DiscoveredServer>?) {
        android.util.Log.d("ServerAdapter", "submitList called with ${list?.size ?: 0} servers")
        super.submitList(list)
    }

    private var connectedServerUrl: String? = null

    fun setConnectedServer(url: String?) {
        val oldConnectedUrl = connectedServerUrl
        connectedServerUrl = url
        
        // Only notify if the connected server actually changed
        if (oldConnectedUrl != url) {
            // Find the positions of the old and new connected servers
            val oldPosition = currentList.indexOfFirst { it.wsUrl == oldConnectedUrl }
            val newPosition = currentList.indexOfFirst { it.wsUrl == url }
            
            // Notify only the specific items that changed
            if (oldPosition != -1) {
                notifyItemChanged(oldPosition)
            }
            if (newPosition != -1) {
                notifyItemChanged(newPosition)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server, parent, false)
        return ServerViewHolder(view, onServerClick)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val server = getItem(position)
        android.util.Log.d("ServerAdapter", "Binding server at position $position: ${server.name}")
        holder.bind(server, connectedServerUrl == server.wsUrl)
    }

    class ServerViewHolder(
        itemView: View,
        private val onServerClick: (DiscoveredServer) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val serverUrlText: TextView = itemView.findViewById(R.id.serverUrlText)
        private val serverStatusText: TextView = itemView.findViewById(R.id.serverStatusText)
        private val connectButton: MaterialButton = itemView.findViewById(R.id.connectButton)

        fun bind(server: DiscoveredServer, isConnected: Boolean) {
            serverUrlText.text = server.wsUrl
            serverStatusText.text = if (isConnected) "Connected" else "Available"

            if (isConnected) {
                connectButton.text = "Disconnect"
                connectButton.setBackgroundColor(itemView.context.getColor(R.color.error))
                connectButton.setStrokeColorResource(R.color.error)
                connectButton.setIconTintResource(R.color.white)
            } else {
                connectButton.text = "Connect"
                connectButton.setBackgroundColor(itemView.context.getColor(R.color.accent_orange))
                connectButton.setStrokeColorResource(R.color.accent_orange)
                connectButton.setIconTintResource(R.color.white)
            }

            connectButton.setOnClickListener {
                onServerClick(server)
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
