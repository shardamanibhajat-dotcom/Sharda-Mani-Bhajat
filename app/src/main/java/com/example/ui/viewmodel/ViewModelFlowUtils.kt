package com.example.ui.viewmodel

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Unwraps nested exceptions from repository flows before `.stateIn(...)`,
 * re-throwing genuine coroutine [CancellationException] while safely
 * recovering from transient [FirebaseFirestoreException] or stream failures.
 */
fun <T> Flow<T>.catchFirestoreErrors(
    tag: String,
    fallback: T
): Flow<T> = catch { error ->
    val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) {
        throw error
    }
    Log.w(tag, "Stream error recovered with fallback", firestoreError ?: error)
    emit(fallback)
}
