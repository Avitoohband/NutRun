package com.avitoohband.nutrun

const val MAX_TRAINING_PAYLOAD_BYTES = 900 * 1_024

fun requireTrainingPayloadWithinLimit(
    payload: String,
    maxBytes: Int = MAX_TRAINING_PAYLOAD_BYTES
) {
    val byteCount = payload.toByteArray(Charsets.UTF_8).size
    require(byteCount <= maxBytes) {
        "Training history is too large to save safely ($byteCount bytes). " +
            "Export or remove older entries before trying again."
    }
}
