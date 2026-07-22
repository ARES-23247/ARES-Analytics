package com.ares.analytics.service

import com.ares.analytics.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val body: String? = null
)

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class UpdateCheckerService(
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    },
    private val serviceScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    sealed class UpdateState {
        /**

         * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

         *

         */
        object Checking : UpdateState()
        /**

         * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

         *

         */
        object UpToDate : UpdateState()
        /**

         * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

         *

         */
        data class UpdateAvailable(val latestVersion: String, val downloadUrl: String, val releaseNotes: String?) : UpdateState()
        /**

         * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

         *

         */
        data class Error(val message: String) : UpdateState()
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.UpToDate)
    val updateState: StateFlow<UpdateState> = _updateState

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun checkForUpdates() {
        serviceScope.launch {
            _updateState.value = UpdateState.Checking
            try {
                httpClient.prepareGet("https://api.github.com/repos/ares-robotics/ares-analytics/releases/latest") {
                    header(HttpHeaders.UserAgent, "ares-analytics-app")
                }.execute { response ->
                    if (response.status == HttpStatusCode.OK) {
                        val release = response.body<GitHubRelease>()
                        if (isNewerVersion(BuildConfig.VERSION, release.tagName)) {
                            _updateState.value = UpdateState.UpdateAvailable(
                                latestVersion = release.tagName,
                                downloadUrl = release.htmlUrl,
                                releaseNotes = release.body
                            )
                        } else {
                            _updateState.value = UpdateState.UpToDate
                        }
                    } else {
                        _updateState.value = UpdateState.Error("API returned status ${response.status}")
                    }
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "Unknown error checking updates")
            }
        }
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun dispose() {
        serviceScope.coroutineContext.cancelChildren()
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val cleanCurrent = current.removePrefix("v").trim()
        val cleanLatest = latest.removePrefix("v").trim()
        if (cleanCurrent == cleanLatest) return false
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val maxLength = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLength) {
            val currVal = currentParts.getOrElse(i) { 0 }
            val latVal = latestParts.getOrElse(i) { 0 }
            if (latVal > currVal) return true
            if (currVal > latVal) return false
        }
        return false
    }
}
