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

    private var connectedServerUrl: String? = null

    fun setConnectedServer(url: String?) {
        connectedServerUrl = url
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server, parent, false)
        return ServerViewHolder(view, onServerClick)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val server = getItem(position)
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
                connectButton.setBackgroundColor(itemView.context.getColor(R.color.success))
                connectButton.setStrokeColorResource(R.color.success)
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
