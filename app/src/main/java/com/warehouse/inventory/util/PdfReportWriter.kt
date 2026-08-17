package com.warehouse.inventory.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import java.io.OutputStream

/**
 * Renders a right-to-left, Arabic-first tabular PDF using the platform's own
 * [PdfDocument]. No PDF library is pulled in: iText/PDFBox would each add megabytes and
 * their own Arabic font-embedding problem, while the platform renderer already has the
 * device's Arabic fonts and shaper.
 *
 * Arabic is drawn through [StaticLayout] rather than `Canvas.drawText`. That matters:
 * `drawText` shapes glyphs but does not run the bidirectional algorithm, so a cell mixing
 * Arabic words with Latin digits (every quantity and date in these reports) comes out with
 * the runs in the wrong visual order. StaticLayout applies full bidi via
 * [TextDirectionHeuristics.RTL], so "استلام 25 قطعة" reads correctly.
 *
 * Columns are laid out right-to-left: `columns[0]` is the rightmost, matching how the
 * on-screen tables read.
 */
class PdfReportWriter {

    /** One column of the report table. [weight] is a share of the available width. */
    data class Column(val header: String, val weight: Float)

    /** A complete report: heading, one table, and a trailing summary. */
    data class Document(
        val title: String,
        val subtitle: String = "",
        val columns: List<Column>,
        val rows: List<List<String>>,
        val summaryLines: List<String> = emptyList(),
        val footer: String = ""
    )

    /**
     * Writes [document] to [out] as a paginated A4 PDF. The stream is not closed here —
     * the caller owns it (it is usually a SAF `OutputStream`).
     */
    fun write(out: OutputStream, document: Document) {
        val pdf = PdfDocument()
        try {
            val state = RenderState(pdf, document)
            state.render()
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }

    private class RenderState(
        private val pdf: PdfDocument,
        private val document: Document
    ) {
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var cursorY = 0f
        private var pageNumber = 0

        private val titlePaint = textPaint(18f, Color.parseColor("#0B3C6B"), bold = true)
        private val subtitlePaint = textPaint(10f, Color.parseColor("#5A6472"))
        private val headerPaint = textPaint(10f, Color.WHITE, bold = true)
        private val cellPaint = textPaint(9.5f, Color.parseColor("#1B1F27"))
        private val summaryPaint = textPaint(10.5f, Color.parseColor("#0B3C6B"), bold = true)
        private val footerPaint = textPaint(8f, Color.parseColor("#8A93A0"))

        private val headerFill = Paint().apply { color = Color.parseColor("#0B3C6B") }
        private val stripeFill = Paint().apply { color = Color.parseColor("#F2F5F9") }
        private val rulePaint = Paint().apply {
            color = Color.parseColor("#DCE2EA")
            strokeWidth = 0.6f
        }

        private val contentWidth = PAGE_WIDTH - MARGIN * 2
        private val columnWidths: List<Float> = run {
            val totalWeight = document.columns.sumOf { it.weight.toDouble() }.toFloat()
                .takeIf { it > 0f } ?: 1f
            document.columns.map { contentWidth * (it.weight / totalWeight) }
        }

        fun render() {
            startPage()
            drawHeading()
            drawTableHeader()

            document.rows.forEachIndexed { index, row ->
                val height = measureRowHeight(row)
                if (cursorY + height > PAGE_HEIGHT - MARGIN - FOOTER_SPACE) {
                    finishPage()
                    startPage()
                    drawTableHeader()
                }
                drawRow(row, height, striped = index % 2 == 1)
            }

            if (document.summaryLines.isNotEmpty()) {
                cursorY += 14f
                document.summaryLines.forEach { line ->
                    if (cursorY > PAGE_HEIGHT - MARGIN - FOOTER_SPACE) {
                        finishPage()
                        startPage()
                    }
                    cursorY += drawRtl(line, summaryPaint, MARGIN, cursorY, contentWidth, maxLines = 2)
                    cursorY += 4f
                }
            }
            finishPage()
        }

        private fun startPage() {
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(
                PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), pageNumber
            ).create()
            page = pdf.startPage(info)
            canvas = page?.canvas
            cursorY = MARGIN
        }

        private fun finishPage() {
            val c = canvas ?: return
            // Page footer: caption on the right, page number on the left.
            val y = PAGE_HEIGHT - MARGIN - 10f
            if (document.footer.isNotBlank()) {
                drawRtl(document.footer, footerPaint, MARGIN, y, contentWidth * 0.7f, maxLines = 1)
            }
            drawRtl(
                "صفحة $pageNumber", footerPaint,
                MARGIN, y, contentWidth, maxLines = 1, align = Layout.Alignment.ALIGN_OPPOSITE
            )
            c.drawLine(MARGIN, y - 6f, PAGE_WIDTH - MARGIN, y - 6f, rulePaint)

            page?.let { pdf.finishPage(it) }
            page = null
            canvas = null
        }

        private fun drawHeading() {
            cursorY += drawRtl(document.title, titlePaint, MARGIN, cursorY, contentWidth, maxLines = 2)
            if (document.subtitle.isNotBlank()) {
                cursorY += 4f
                cursorY += drawRtl(
                    document.subtitle, subtitlePaint, MARGIN, cursorY, contentWidth, maxLines = 3
                )
            }
            cursorY += 12f
        }

        private fun drawTableHeader() {
            val c = canvas ?: return
            val height = ROW_PADDING * 2 + headerPaint.lineHeight()
            c.drawRect(MARGIN, cursorY, PAGE_WIDTH - MARGIN, cursorY + height, headerFill)
            forEachCellX { index, x ->
                drawRtl(
                    document.columns[index].header, headerPaint,
                    x + CELL_PADDING, cursorY + ROW_PADDING,
                    columnWidths[index] - CELL_PADDING * 2, maxLines = 1
                )
            }
            cursorY += height
        }

        private fun measureRowHeight(row: List<String>): Float {
            var tallest = cellPaint.lineHeight()
            row.forEachIndexed { index, text ->
                if (index >= columnWidths.size) return@forEachIndexed
                val width = (columnWidths[index] - CELL_PADDING * 2).coerceAtLeast(1f)
                tallest = maxOf(tallest, layoutFor(text, cellPaint, width, MAX_CELL_LINES).height.toFloat())
            }
            return tallest + ROW_PADDING * 2
        }

        private fun drawRow(row: List<String>, height: Float, striped: Boolean) {
            val c = canvas ?: return
            if (striped) {
                c.drawRect(MARGIN, cursorY, PAGE_WIDTH - MARGIN, cursorY + height, stripeFill)
            }
            forEachCellX { index, x ->
                val text = row.getOrNull(index).orEmpty()
                drawRtl(
                    text, cellPaint,
                    x + CELL_PADDING, cursorY + ROW_PADDING,
                    columnWidths[index] - CELL_PADDING * 2, maxLines = MAX_CELL_LINES
                )
            }
            cursorY += height
            c.drawLine(MARGIN, cursorY, PAGE_WIDTH - MARGIN, cursorY, rulePaint)
        }

        /**
         * Walks the columns right-to-left, handing each its left edge. `columns[0]` sits
         * against the right margin, so the table reads in the same direction as the text.
         */
        private inline fun forEachCellX(body: (index: Int, left: Float) -> Unit) {
            var right = PAGE_WIDTH - MARGIN
            columnWidths.forEachIndexed { index, width ->
                body(index, right - width)
                right -= width
            }
        }

        /** Draws bidi-correct text and returns the height consumed. */
        private fun drawRtl(
            text: String,
            paint: TextPaint,
            left: Float,
            top: Float,
            width: Float,
            maxLines: Int,
            align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
        ): Float {
            val c = canvas ?: return 0f
            val layout = layoutFor(text, paint, width.coerceAtLeast(1f), maxLines, align)
            c.save()
            c.translate(left, top)
            layout.draw(c)
            c.restore()
            return layout.height.toFloat()
        }

        private fun layoutFor(
            text: String,
            paint: TextPaint,
            width: Float,
            maxLines: Int,
            align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
        ): StaticLayout =
            StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
                .setAlignment(align)
                // The reason this class uses StaticLayout at all: full bidi reordering.
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(false)
                .setLineSpacing(0f, 1f)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()

        private fun TextPaint.lineHeight(): Float = fontMetrics.let { it.descent - it.ascent }

        private companion object {
            /** A4 at 72 dpi, in PostScript points. */
            const val PAGE_WIDTH = 595f
            const val PAGE_HEIGHT = 842f
            const val MARGIN = 32f
            const val ROW_PADDING = 5f
            const val CELL_PADDING = 4f
            const val FOOTER_SPACE = 26f
            const val MAX_CELL_LINES = 3

            fun textPaint(size: Float, color: Int, bold: Boolean = false) = TextPaint().apply {
                isAntiAlias = true
                textSize = size
                this.color = color
                typeface = if (bold) {
                    android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
                    )
                } else {
                    android.graphics.Typeface.DEFAULT
                }
            }
        }
    }
}
