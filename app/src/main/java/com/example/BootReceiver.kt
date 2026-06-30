package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            // System manages accessibility service, but this receiver tells the OS
            // that we have a background component, which often fixes battery optimization 
            // menus on custom ROMs like MIUI/HyperOS.
        }
    }
}
