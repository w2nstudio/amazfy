package com.amazfy.attribution.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CsvTextTest {
    @Test
    fun `quoted cells keep commas, quotes and line breaks, and rows keep their line numbers`() {
        val rows = CsvText.rows("﻿Name,Note\r\n\"Filter, 40L\",\"says \"\"quiet\"\"\"\r\n\r\n\"two\nlines\",x\r\nlast,row")
        assertEquals(listOf("Name", "Note"), rows[0].cells)
        assertEquals(listOf("Filter, 40L", "says \"quiet\""), rows[1].cells)
        assertEquals(listOf("two\nlines", "x"), rows[2].cells)
        assertEquals(listOf(1, 2, 4, 6), rows.map { it.line }) // the blank line 3 is dropped
    }

    @Test
    fun `tabs and semicolons work as delimiters too`() {
        assertEquals(listOf("a", "b"), CsvText.rows("a\tb\n1\t2")[0].cells)
        assertEquals(listOf("1,5", "2"), CsvText.rows("a;b\n1,5;2")[1].cells)
    }
}
