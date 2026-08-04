package com.qaxlabs.openphotos.data

import kotlinx.serialization.Serializable

/**
 * Top-level vault index document (PRD §7).
 *
 * Written as [INDEX_FILENAME] ("openphotos_index_v1.json") into a Telegram
 * Document message in Saved Messages. [version] lets future clients detect
 * schema incompatibilities and offer a migration path.
 */
@Serializable
data class VaultIndex(
    val version: Int = 1,
    val files: List<VaultIndexEntry> = emptyList(),
) {
    companion object {
        const val INDEX_FILENAME = "openphotos_index_v1.json"
        const val INDEX_CAPTION  = "#openphotos_index"
    }
}
