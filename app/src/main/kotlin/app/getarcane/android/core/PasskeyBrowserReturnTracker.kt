package app.getarcane.android.core

/**
 * Tracks the Activity/Custom Tab hand-off without retaining callback or ceremony material.
 * A callback can arrive before the launch call returns, so `launched` must never overwrite the
 * completing state and make `onResume` cancel a valid backend finish.
 */
internal class PasskeyBrowserReturnTracker {
    private enum class State { IDLE, PREPARING, WAITING_FOR_RETURN, COMPLETING }

    private var state = State.IDLE

    fun start(): Boolean {
        if (state != State.IDLE) return false
        state = State.PREPARING
        return true
    }

    fun launched() {
        if (state == State.PREPARING) state = State.WAITING_FOR_RETURN
    }

    fun callback(): Boolean = when (state) {
        State.PREPARING, State.WAITING_FOR_RETURN -> {
            state = State.COMPLETING
            true
        }
        State.IDLE, State.COMPLETING -> false
    }

    fun shouldCancelOnResume(): Boolean = state == State.WAITING_FOR_RETURN

    fun reset() {
        state = State.IDLE
    }
}
