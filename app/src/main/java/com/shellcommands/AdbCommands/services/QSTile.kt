package com.shellcommands.AdbCommands.services

import android.content.Intent
import android.service.quicksettings.TileService
import com.shellcommands.AdbCommands.views.MainActivity

class QSTile : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(intent)
    }
}