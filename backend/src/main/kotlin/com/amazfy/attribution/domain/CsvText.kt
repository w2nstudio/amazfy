package com.amazfy.attribution.domain

/** One row of a CSV file and the line it starts on (1-based), for error messages the seller can find in the file. */
data class CsvRow(
    val line: Int,
    val cells: List<String>,
)

/**
 * Splits CSV text into rows (RFC 4180): quoted cells may hold the delimiter, line breaks and quotes written as "".
 * The delimiter is guessed (comma, semicolon or tab), since spreadsheet programs in some countries save with
 * semicolons. Blank rows are dropped.
 */
object CsvText {
    fun rows(text: String): List<CsvRow> {
        val body = text.removePrefix("﻿") // byte order mark some programs write first
        val delimiter = guessDelimiter(body)
        val rows = mutableListOf<CsvRow>()
        var cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var line = 1
        var rowStart = 1
        var i = 0

        fun endRow() {
            cells += cell.toString()
            cell.clear()
            if (cells.any { it.isNotBlank() }) rows += CsvRow(rowStart, cells)
            cells = mutableListOf()
        }
        while (i < body.length) {
            val c = body[i]
            when {
                quoted && c == '"' && body.getOrNull(i + 1) == '"' -> {
                    cell.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                !quoted && c == delimiter -> {
                    cells += cell.toString()
                    cell.clear()
                }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && body.getOrNull(i + 1) == '\n') i++
                    endRow()
                    line++
                    rowStart = line
                }
                else -> {
                    if (c == '\n') line++ // a line break inside a quoted cell
                    cell.append(c)
                }
            }
            i++
        }
        endRow()
        return rows
    }

    private fun guessDelimiter(text: String): Char {
        val start = text.lineSequence().take(5).joinToString("\n")
        return listOf(',', ';', '\t').maxBy { delimiter -> start.count { it == delimiter } }
    }
}
