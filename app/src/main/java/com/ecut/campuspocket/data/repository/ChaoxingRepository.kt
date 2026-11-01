package com.ecut.campuspocket.data.repository

import com.ecut.campuspocket.data.AppPreferences
import com.ecut.campuspocket.data.remote.chaoxing.ChaoxingClient
import com.ecut.campuspocket.data.remote.chaoxing.ChaoxingSign
import com.ecut.campuspocket.data.remote.chaoxing.ChaoxingSnapshot
import com.ecut.campuspocket.network.CampusEndpoints

class ChaoxingRepository(
    private val client: ChaoxingClient,
    private val authRepository: AuthRepository,
    private val preferences: AppPreferences
) {

    suspend fun hasSession(): Boolean = client.hasSession()

    suspend fun loadSnapshot(): ChaoxingSnapshot = client.fetchSnapshot()

    suspend fun loadSigns(): List<ChaoxingSign> = client.fetchSigns()

    suspend fun logout() {
        authRepository.clearSystem(
            systemId = CampusEndpoints.SYSTEM_CHAOXING,
            hosts = CampusEndpoints.CHAOXING_HOSTS
        )
        preferences.clearNotified(BUCKET_SIGN)
        preferences.clearNotified(BUCKET_TASK)
        preferences.chaoxingTasksSeeded = false
        preferences.chaoxingTasksFetchedAt = 0L
    }

    companion object {
        const val BUCKET_SIGN = "chaoxing_sign"
        const val BUCKET_TASK = "chaoxing_task"
    }
}
