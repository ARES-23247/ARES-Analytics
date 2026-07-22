package com.ares.analytics.service.nt4

import kotlinx.serialization.Serializable

@Serializable
data class Nt4Topic(
    val id: Int,
    val name: String,
    val type: String,
    val properties: Map<String, String> = emptyMap()
)
