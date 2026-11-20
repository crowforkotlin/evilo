package crow.evilo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue

/** AnimatedContent hashes its state keys; only this holder's identity participates. */
internal class EviloTransitionPayload<T>(initialValue: T, val key: Any?) {
    var value: T by mutableStateOf(initialValue, referentialEqualityPolicy())
        private set

    fun update(value: T) {
        // Resource lookup may allocate an equal title on each recomposition.
        // Do not repeatedly invalidate that content; never compare full page models.
        if (this.value !== value && !(value is String && value == this.value)) this.value = value
    }
}

/** Same-page updates refresh the content without restarting the page's animation. */
internal class EviloTransitionPayloadCache<T>(initialValue: T, private val contentKey: (T) -> Any?) {
    private var current = EviloTransitionPayload(initialValue, contentKey(initialValue))

    fun target(value: T): EviloTransitionPayload<T> {
        val key = contentKey(value)
        if (current.key != key) current = EviloTransitionPayload(value, key)
        else current.update(value)
        return current
    }

    /** Keep the preview destination mounted when the store publishes the completed Back. */
    fun adopt(target: EviloTransitionPayload<T>) {
        current = target
    }
}
