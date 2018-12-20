package org.wordpress.android.ui.sitecreation.creation

import kotlinx.coroutines.experimental.delay
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.SiteActionBuilder
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.store.SiteStore
import org.wordpress.android.fluxc.store.SiteStore.OnSiteChanged
import javax.inject.Inject
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

private const val FETCH_SITE_BASE_RETRY_DELAY_IN_MILLIS = 1000

/**
 * Transforms FETCH_SITE -> UPDATE_SITE fluxC request-response pair to a coroutine.
 */
class FetchWpComSiteUseCase @Inject constructor(
    private val dispatcher: Dispatcher,
    @Suppress("unused") private val siteStore: SiteStore
) {
    private var continuation: Continuation<OnSiteChanged>? = null

    suspend fun fetchSite(remoteSiteId: Long, numberOfRetries: Int): OnSiteChanged {
        repeat(numberOfRetries) { attemptNumber ->
            val onSiteFetched = fetchSite(remoteSiteId)
            if (!onSiteFetched.isError) {
                // return only when the request succeeds
                return onSiteFetched
            }
            // linear backoff
            delay((attemptNumber + 1) * FETCH_SITE_BASE_RETRY_DELAY_IN_MILLIS) // +1 -> starts from 0
        }
        // return the last attempt no matter the result (success/error)
        return fetchSite(remoteSiteId)
    }

    private suspend fun fetchSite(siteId: Long): OnSiteChanged {
        return suspendCoroutine { cont ->
            continuation = cont
            dispatcher.dispatch(SiteActionBuilder.newFetchSiteAction(createSiteModel(siteId)))
        }
    }

    private fun createSiteModel(siteId: Long): SiteModel {
        val siteModel = SiteModel()
        siteModel.siteId = siteId
        siteModel.setIsWPCom(true)
        return siteModel
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    @SuppressWarnings("unused")
    fun onSiteFetched(event: OnSiteChanged) {
        continuation?.resume(event)
        continuation = null
    }
}
