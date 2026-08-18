package com.warehouse.inventory.util

/**
 * Builds RFC-4180 CSV text.
 *
 * Two details matter for Arabic exports:
 *
 * - The output is prefixed with a UTF-8 byte-order mark. Excel on Windows assumes the
 *   system ANSI codepage for `.csv` unless it sees a BOM, which is what turns Arabic column
 *   values into mojibake. LibreOffice and Sheets detect UTF-8 either way; the BOM costs
 *   nothing and fixes Excel.
 * - Fields are quoted whenever they contain a delimiter, quote or newline, and embedded
 *   quotes are doubled — so a product note containing a comma cannot shift every following
 *   column.
 */
class CsvWriter {

    private val builder = StringBuilder()

    fun row(vararg cells: Any?): CsvWriter = row(cells.toList())

    fun row(cells: List<Any?>): CsvWriter {
        builder.append(cells.joinToString(",") { escape(it) })
        builder.append(LINE_SEPARATOR)
        return this
    }

    /** A blank separator line, for stacking several tables in one file. */
    fun blankLine(): CsvWriter {
        builder.append(LINE_SEPARATOR)
        return this
    }

    /** The finished document, BOM included. Ready to write to an OutputStream as UTF-8. */
    fun build(): String = BOM + builder.toString()

    fun toByteArray(): ByteArray = build().toByteArray(Charsets.UTF_8)

    private fun escape(value: Any?): String {
        val text = when (value) {
            null -> ""
            is Double -> formatDecimal(value)
            is Float -> formatDecimal(value.toDouble())
            else -> value.toString()
        }
        val needsQuoting = text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) "\"" + text.replace("\"", "\"\"") + "\"" else text
    }

    private fun formatDecimal(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else String.format("%.2f", value)

    private companion object {
        const val BOM = "﻿"

        /** CRLF, as RFC 4180 specifies and Excel expects. */
        const val LINE_SEPARATOR = "\r\n"
    }
}
