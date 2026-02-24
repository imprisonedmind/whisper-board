package com.walkietalkie.dictationime.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.walkietalkie.dictationime.R
import com.walkietalkie.dictationime.auth.AuthStore
import com.walkietalkie.dictationime.auth.LoginEmailActivity
import com.walkietalkie.dictationime.model.TranscriptionModels
import kotlinx.coroutines.launch

class ModelSelectionActivity : AppCompatActivity() {
    private lateinit var listContainer: LinearLayout
    private var selectedModelId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthStore.isSignedIn(this)) {
            startActivity(Intent(this, LoginEmailActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_model_selection)
        findViewById<ImageButton>(R.id.modelSelectionBackButton).setOnClickListener { finish() }
        listContainer = findViewById(R.id.modelSelectionListContainer)
        selectedModelId = TranscriptionModelStore.getCached(this)
        renderModels()

        lifecycleScope.launch {
            runCatching {
                TranscriptionModelStore.fetchRemote(this@ModelSelectionActivity)
            }.onSuccess { remoteModelId ->
                selectedModelId = remoteModelId
                renderModels()
            }
        }
    }

    private fun renderModels() {
        listContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        TranscriptionModels.options.forEach { option ->
            val row = inflater.inflate(R.layout.item_model_option, listContainer, false)
            row.findViewById<TextView>(R.id.modelOptionTitle).text = option.label
            row.findViewById<TextView>(R.id.modelOptionSubtitle).text = option.subtitle
            val selected = option.id == selectedModelId
            row.findViewById<TextView>(R.id.modelOptionStatus).text = getString(
                if (selected) R.string.model_option_selected else R.string.model_option_available
            )
            row.setOnClickListener {
                if (selectedModelId == option.id) return@setOnClickListener
                val previous = selectedModelId
                selectedModelId = option.id
                renderModels()
                lifecycleScope.launch {
                    runCatching {
                        TranscriptionModelStore.setSelectedModel(this@ModelSelectionActivity, option.id)
                    }.onSuccess {
                        Toast.makeText(
                            this@ModelSelectionActivity,
                            R.string.model_selection_save_success,
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure {
                        selectedModelId = previous
                        renderModels()
                        Toast.makeText(
                            this@ModelSelectionActivity,
                            R.string.model_selection_save_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            listContainer.addView(row)
        }
    }
}
