package app.eduroam.geteduroam.di.repository

import app.eduroam.geteduroam.di.api.GetEduroamApi
import app.eduroam.geteduroam.models.DiscoveryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response

/**
 * Coalesces concurrent calls to discover(): if a call is already in flight when a second
 * one comes in, the second just awaits the same result instead of starting its own
 * multi-MB download of discovery.json. Once a call finishes, the next one always hits the
 * network again - this never serves an old result, it only merges calls that genuinely
 * overlap in time (e.g. the organization search screen and an auto-navigated profile
 * screen both loading at once).
 */
class DiscoveryRepository(private val api: GetEduroamApi) {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var inFlight: Deferred<Response<DiscoveryResult>>? = null

    suspend fun discover(): Response<DiscoveryResult> {
        val deferred = mutex.withLock {
            inFlight?.takeIf { it.isActive } ?: repositoryScope.async { api.discover() }.also { inFlight = it }
        }
        return deferred.await()
    }
}
