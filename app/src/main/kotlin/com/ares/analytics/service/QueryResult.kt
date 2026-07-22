package com.ares.analytics.service

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class QueryResult(
    val columns: List<String>,
    val rows: List<List<String>>
)
