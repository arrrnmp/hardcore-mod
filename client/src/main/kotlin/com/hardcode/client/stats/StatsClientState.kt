package com.hardcode.client.stats

import com.hardcode.common.model.StatsSnapshot

/** Holds the latest broadcast stats and whether the overlay is currently toggled on. */
object StatsClientState {
    @Volatile
    var visible: Boolean = false

    @Volatile
    var snapshot: StatsSnapshot = StatsSnapshot(emptyList())

    fun toggle() {
        visible = !visible
    }
}
