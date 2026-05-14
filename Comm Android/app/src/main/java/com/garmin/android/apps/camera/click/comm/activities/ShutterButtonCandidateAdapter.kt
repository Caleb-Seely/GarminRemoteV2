package com.garmin.android.apps.camera.click.comm.activities

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.garmin.android.apps.camera.click.comm.R
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo

class ShutterButtonCandidateAdapter(
    private val candidates: List<ShutterButtonInfo>,
    private val initialSelectedIndex: Int = 0,
    private val onItemSelected: (Int) -> Unit
) : RecyclerView.Adapter<ShutterButtonCandidateAdapter.ViewHolder>() {

    private var selectedIndex: Int = initialSelectedIndex

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val desc: TextView = itemView.findViewById(R.id.candidate_content_desc)
        val resId: TextView = itemView.findViewById(R.id.candidate_resource_id)
        val className: TextView = itemView.findViewById(R.id.candidate_class_name)
        val position: TextView = itemView.findViewById(R.id.candidate_position)
        val score: TextView = itemView.findViewById(R.id.candidate_confidence_score)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        Log.d("CandidateAdapter", "onCreateViewHolder called — parent h=${parent.height} w=${parent.width}")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shutter_candidate, parent, false)
        Log.d("CandidateAdapter", "item inflated — h=${view.layoutParams?.height} w=${view.layoutParams?.width}")
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = candidates.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val info = candidates[position]
        holder.desc.text = info.contentDescription ?: "(No description)"
        holder.resId.text = info.resourceId ?: "(No resource ID)"
        holder.className.text = info.className ?: "(No class name)"
        
        // Calculate position on screen if not already set (fallback for existing data)
        val positionText = info.positionOnScreen ?: info.calculatePositionOnScreen(1080, 1920) // Default screen dimensions
        holder.position.text = positionText
        
        holder.score.text = holder.itemView.context.getString(R.string.confidence_score_label, info.confidenceScore)
        
        // Set selected state for the container and all text views
        val isSelected = position == selectedIndex
        holder.itemView.isSelected = isSelected
        holder.desc.isSelected = isSelected
        holder.resId.isSelected = isSelected
        holder.className.isSelected = isSelected
        holder.position.isSelected = isSelected
        holder.score.isSelected = isSelected
        
        holder.itemView.setOnClickListener {
            val prev = selectedIndex
            selectedIndex = position
            notifyItemChanged(prev)
            notifyItemChanged(selectedIndex)
            onItemSelected(position)
        }
    }
} 