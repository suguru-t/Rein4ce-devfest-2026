package com.rein4ce.roadcollector.data

import com.rein4ce.roadcollector.core.model.Part
import java.util.UUID

/** Only newly captured GPS tracks are verified walks. Old UUIDs cannot distinguish demos from walks. */
enum class PartOrigin(val prefix: String) {
    WALK("walk-"), DEMO("demo-"), FILE("file-");
    fun newId(): String = prefix + UUID.randomUUID()

    companion object {
        fun fromPlayback(label: String?): PartOrigin = when (label) {
            null -> WALK
            "デモ散歩" -> DEMO
            else -> FILE
        }
    }
}

data class WalkDistance(val meters: Long, val excluded: Int)

/** Count the frozen session (including fallen pieces), not whatever is in the collection now. */
fun walkDistance(parts: List<Part>): WalkDistance {
    val unique = parts.distinctBy { it.id }
    val walks = unique.filter { it.id.startsWith(PartOrigin.WALK.prefix) }
    return WalkDistance(walks.sumOf { it.distanceM.coerceAtLeast(0).toLong() }, unique.size - walks.size)
}
