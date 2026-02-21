package com.walkietalkie.dictationime.audio

import kotlin.math.sqrt

data class AudioFeatures(
    val rms: Float,
    val zcr: Float
)

fun extractAudioFeatures(pcm16Mono: ShortArray): AudioFeatures {
    if (pcm16Mono.isEmpty()) {
        return AudioFeatures(0f, 0f)
    }

    var sumSq = 0.0
    var zeroCrossings = 0
    var prev = pcm16Mono[0].toInt()
    val scale = 1.0 / 32768.0

    for (i in pcm16Mono.indices) {
        val sample = pcm16Mono[i].toInt()
        val normalized = sample * scale
        sumSq += normalized * normalized
        if (i > 0) {
            if ((sample >= 0 && prev < 0) || (sample < 0 && prev >= 0)) {
                zeroCrossings++
            }
            prev = sample
        }
    }

    val rms = sqrt(sumSq / pcm16Mono.size).toFloat().coerceIn(0f, 1f)
    val zcr = if (pcm16Mono.size > 1) {
        zeroCrossings.toFloat() / (pcm16Mono.size - 1).toFloat()
    } else {
        0f
    }

    return AudioFeatures(rms = rms, zcr = zcr.coerceIn(0f, 1f))
}
