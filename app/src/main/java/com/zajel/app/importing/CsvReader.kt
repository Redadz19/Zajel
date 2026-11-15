package com.zajel.app.importing

/**
 * محلل CSV أولي بسيط. ليس بديلاً عن محلل CSV كامل يدعم الاقتباسات والفواصل داخل الحقول.
 */
object CsvReader {
    fun parseLines(content: String): List<List<String>> =
        content.lineSequence().filter { it.isNotBlank() }.map { line ->
            line.split(',').map(String::trim)
        }.toList()
}
