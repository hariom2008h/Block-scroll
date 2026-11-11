package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // When Stealth Mode is enabled, block launching from Home Screen / App Drawer.
        // The user can still open MainActivity directly via Phone Settings -> Accessibility -> Shorts Blocker -> Settings.
        if (StealthModeManager.isStealthEnabled(this)) {
            Toast.makeText(
                this,
                "Stealth Mode ON: Open via Settings → Accessibility → Shorts Blocker",
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
