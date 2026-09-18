package com.hardcode.client.freeze

/** Holds the last freeze state received from the server, read by [FreezeHud]. */
object FreezeClientState {
    @Volatile
    var frozen: Boolean = false

    @Volatile
    var waitingForPlayerName: String = ""

    fun update(payload: FreezeStatePayload) {
        frozen = payload.frozen
        waitingForPlayerName = payload.waitingForPlayerName
    }
}
