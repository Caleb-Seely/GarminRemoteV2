package com.garmin.android.apps.camera.click.comm.utils

import android.content.Context
import com.garmin.android.apps.camera.click.comm.detection.ButtonLocationRepository
import com.garmin.android.apps.camera.click.comm.model.ShutterButtonInfo

/**
 * CameraAppCandidateStore - In-memory storage for shutter button candidates
 *
 * This is a singleton that maintains an in-memory cache of shutter button candidates.
 * It uses ButtonLocationRepository for persistence.
 *
 * Note: This still uses a static singleton pattern but delegates persistence to the repository.
 * A future refactoring could make this fully injectable, but that would require changes
 * throughout the accessibility service which is out of scope for the current MVVM migration.
 */
object CameraAppCandidateStore {
    // Map: packageName -> List of candidates
    val candidatesByApp: MutableMap<String, List<ShutterButtonInfo>> = mutableMapOf()

    // Repository instance - set by CameraClickApplication
    private var repository: ButtonLocationRepository? = null

    fun initialize(buttonLocationRepository: ButtonLocationRepository) {
        repository = buttonLocationRepository
    }

    fun updateCandidatesForApp(context: Context, packageName: String, candidates: List<ShutterButtonInfo>) {
        candidatesByApp[packageName] = candidates.sortedByDescending { it.confidenceScore }
        repository?.saveAllCandidateLists(candidatesByApp)
    }

    fun loadAllFromPrefs(context: Context) {
        val loaded = repository?.loadAllCandidateLists() ?: emptyMap()
        candidatesByApp.clear()
        candidatesByApp.putAll(loaded)
    }
} 