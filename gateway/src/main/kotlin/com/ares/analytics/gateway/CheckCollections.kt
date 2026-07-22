import com.google.cloud.firestore.FirestoreOptions

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun main() {
    try {
        val db = FirestoreOptions.getDefaultInstance().service
        println("Collections:")
        db.listCollections().forEach { println(it.id) }
    } catch (e: Exception) {
        println("Error: ${e.message}")
    }
}
