package com.walkietalkie.dictationime.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.walkietalkie.dictationime.settings.MainActivity

class AuthGateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (AuthStore.isSignedIn(this)) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(this, LoginEmailActivity::class.java))
        }
        finish()
    }
}
