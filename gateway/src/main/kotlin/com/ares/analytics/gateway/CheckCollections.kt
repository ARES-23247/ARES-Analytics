import com.google.cloud.firestore.FirestoreOptions

fun main() {
    try {
        val db = FirestoreOptions.getDefaultInstance().service
        println("Collections:")
        db.listCollections().forEach { println(it.id) }
    } catch (e: Exception) {
        println("Error: ${e.message}")
    }
}
