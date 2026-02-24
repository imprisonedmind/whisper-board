package com.walkietalkie.dictationime.model

import android.content.Context
import com.walkietalkie.dictationime.openai.OpenAiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ModelInfo(
    val id: String,
    val path: String,
    val sizeBytes: Long
)

interface ModelManager {
    suspend fun ensureModelReady(modelId: String): Result<ModelInfo>
    fun currentModel(): ModelInfo
}

class ModelUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

class RemoteModelManager(
    private val context: Context,
    private val config: OpenAiConfig = OpenAiConfig
) : ModelManager {
    private val lock = Mutex()
    @Volatile
    private var current: ModelInfo = ModelInfo(
        id = DEFAULT_MODEL_ID,
        path = DEFAULT_MODEL_ID,
        sizeBytes = 0L
    )

    override suspend fun ensureModelReady(modelId: String): Result<ModelInfo> {
        return lock.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (!config.isConfigured(context)) {
                        throw ModelUnavailableException("OpenAI API key missing")
                    }

                    val modelInfo = ModelInfo(
                        id = modelId,
                        path = modelId,
                        sizeBytes = 0L
                    )
                    current = modelInfo
                    modelInfo
                }
            }
        }
    }

    override fun currentModel(): ModelInfo = current
}
