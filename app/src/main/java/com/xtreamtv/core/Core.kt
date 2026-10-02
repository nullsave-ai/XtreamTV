package com.xtreamtv.core

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val offline: Boolean = false) : UiState<Nothing>
}

enum class ErrorKind { Offline, Auth, Server, Parse }

class XtreamError(val kind: ErrorKind, message: String, cause: Throwable? = null) : Exception(message, cause)

fun Throwable.toUiError(): UiState.Error = when (this) {
    is XtreamError -> UiState.Error(message ?: "حدث خطأ", kind == ErrorKind.Offline)
    else -> UiState.Error(message ?: "حدث خطأ غير متوقع")
}

/** Loads a value for [key]; returns the state and a retry function. */
@Composable
fun <T> loadState(key: Any?, block: suspend () -> T): Pair<UiState<T>, () -> Unit> {
    var attempt by remember(key) { mutableIntStateOf(0) }
    var state by remember(key) { mutableStateOf<UiState<T>>(UiState.Loading) }
    LaunchedEffect(key, attempt) {
        state = UiState.Loading
        state = try {
            UiState.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e.toUiError()
        }
    }
    return state to { attempt++ }
}

object Fmt {
    fun clock(epochSec: Long): String = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(epochSec * 1000))

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.ENGLISH, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.ENGLISH, "%02d:%02d", m, sec)
    }

    fun date(epochSec: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date(epochSec * 1000))
}
