package com.aaronmompie.client.stats

import com.aaronmompie.common.model.StatsSnapshot

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
