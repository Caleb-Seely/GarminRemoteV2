package com.garmin.android.apps.camera.click.comm.utils

import android.content.Context
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo
import com.garmin.android.apps.camera.click.comm.utils.AccessibilityUtils

object CameraAppCandidateStore {
    // Map: packageName -> List of candidates
    val candidatesByApp: MutableMap<String, List<ShutterButtonInfo>> = mutableMapOf()

    fun updateCandidatesForApp(context: Context, packageName: String, candidates: List<ShutterButtonInfo>) {
        candidatesByApp[packageName] = candidates.sortedByDescending { it.confidenceScore }
        AccessibilityUtils.saveAllCandidateLists(context, candidatesByApp)
    }

    fun loadAllFromPrefs(context: Context) {
        val loaded = AccessibilityUtils.loadAllCandidateLists(context)
        candidatesByApp.clear()
        candidatesByApp.putAll(loaded)
    }
} 