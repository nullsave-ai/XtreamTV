package com.xtreamtv.data

import com.xtreamtv.core.ErrorKind
import com.xtreamtv.core.XtreamError
import com.xtreamtv.domain.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val UA = "XtreamTV/1.0 (Android TV)"

class XtreamApi(private val http: OkHttpClient) {

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun apiUrl(a: Account, action: String? = null, params: Map<String, String> = emptyMap()): String {
        val b = StringBuilder(a.server).append("/player_api.php?username=").append(enc(a.username))
            .append("&password=").append(enc(a.password))
        if (action != null) b.append("&action=").append(action)
        params.forEach { (k, v) -> b.append('&').append(k).append('=').append(enc(v)) }
        return b.toString()
    }

    fun liveUrl(a: Account, id: Int, ext: String) = "${a.server}/live/${a.username}/${a.password}/$id.$ext"
    fun movieUrl(a: Account, id: Int, ext: String) = "${a.server}/movie/${a.username}/${a.password}/$id.$ext"
    fun episodeUrl(a: Account, epId: String, ext: String) = "${a.server}/series/${a.username}/${a.password}/$epId.$ext"

    suspend fun fetch(a: Account, action: String?, params: Map<String, String> = emptyMap()): String {
        try {
            val req = Request.Builder().url(apiUrl(a, action, params)).header("User-Agent", UA).build()
            http.newCall(req).await().use { r ->
                if (!r.isSuccessful) {
                    val kind = if (r.code == 401 || r.code == 403) ErrorKind.Auth else ErrorKind.Server
                    throw XtreamError(kind, "السيرفر أعاد الخطأ ${r.code}")
                }
                return withContext(Dispatchers.IO) { r.body?.string() ?: "" }
            }
        } catch (e: IOException) {
            throw XtreamError(ErrorKind.Offline, "تعذر الوصول إلى السيرفر. تحقق من الاتصال بالإنترنت.", e)
        } catch (e: IllegalArgumentException) {
            throw XtreamError(ErrorKind.Server, "رابط السيرفر غير صالح", e)
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { cont.resume(response) }
    })
}
