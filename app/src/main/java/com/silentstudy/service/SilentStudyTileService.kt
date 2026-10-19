package com.silentstudy.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class SilentStudyTileService : TileService() {

    private var isActive = false

    override fun onClick() {
        super.onClick()
        isActive = !isActive
        if (isActive) {
            SilentStudyService.startService(this)
        } else {
            SilentStudyService.stopService(this)
        }
        updateTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        qsTile?.let { tile ->
            tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = if (isActive) "Studying Active" else "Start Study"
            tile.updateTile()
        }
    }
}
