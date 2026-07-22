package com.ares.analytics.shared

import kotlinx.serialization.json.Json
val AppJson = Json { ignoreUnknownKeys = true }
val AppJsonPretty = Json { ignoreUnknownKeys = true; prettyPrint = true }
