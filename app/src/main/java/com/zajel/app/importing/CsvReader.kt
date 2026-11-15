package com.zajel.app.importing

/**
 * Small CSV parser supporting quoted fields, escaped quotes and commas/newlines inside quotes.
 */
object CsvReader {
    fun parse(content: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0

        while (i < content.length) {
            val ch = content[i]
            when {
                ch == '"' && quoted && i + 1 < content.length && content[i + 1] == '"' -> {
                    cell.append('"')
                    i++
                }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> {
                    row += cell.toString().trim()
                    cell.clear()
                }
                (ch == '\n' || ch == '\r') && !quoted -> {
                    row += cell.toString().trim()
                    cell.clear()
                    if (row.any { it.isNotBlank() }) rows += row.toList()
                    row.clear()
                    if (ch == '\r' && i + 1 < content.length && content[i + 1] == '\n') i++
                }
                else -> cell.append(ch)
            }
            i++
        }

        row += cell.toString().trim()
        if (row.any { it.isNotBlank() }) rows += row.toList()
        return rows
    }

    fun recipientRows(content: String): List<Pair<String?, String>> {
        val rows = parse(content)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.lowercase().replace(" ", "") }
        val phoneHeaders = setOf("phone", "mobile", "telephone", "tel", "number", "رقم", "الهاتف", "رقمالهاتف", "الجوال")
        val nameHeaders = setOf("name", "fullname", "nom", "الاسم", "الاسم الكامل")
        val phoneIndex = header.indexOfFirst { it in phoneHeaders }.let { if (it >= 0) it else 0 }
        val nameIndex = header.indexOfFirst { it in nameHeaders }
        val hasHeader = header.any { it in phoneHeaders || it in nameHeaders }
        return rows.drop(if (hasHeader) 1 else 0).mapNotNull { cells ->
            val phone = cells.getOrNull(phoneIndex)?.trim().orEmpty()
            if (phone.length < 7) null
            else (if (nameIndex >= 0) cells.getOrNull(nameIndex)?.takeIf { it.isNotBlank() } else null) to phone
        }
    }
}
