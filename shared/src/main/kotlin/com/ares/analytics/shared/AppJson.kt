package com.ares.analytics.shared

import kotlinx.serialization.json.Json

/** Lenient reader for versioned robot and dashboard files; unknown fields preserve forward compatibility. */
val AppJson = Json { ignoreUnknownKeys = true }

/** [AppJson] with stable human-readable output for files maintained by users. */
val AppJsonPretty = Json { ignoreUnknownKeys = true; prettyPrint = true }
