package io.github.yzjdev.expressapp.log

object LogBuffer {

    private val buffer = mutableListOf<QueryLog>()
    private const val MAX_SIZE = 200

    fun add(log: QueryLog) {
        synchronized(buffer) {
            buffer.add(0, log)
            if (buffer.size > MAX_SIZE) buffer.removeAt(buffer.lastIndex)
        }
    }

    fun getAll(): List<QueryLog> = synchronized(buffer) { buffer.toList() }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
    }
}
