package com.zayne.hamix

object RootUtils {
    fun hasRootAccess(): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            exitCode == 0 && output.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }
}
