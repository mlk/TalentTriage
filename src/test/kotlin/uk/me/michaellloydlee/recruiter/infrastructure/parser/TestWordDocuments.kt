package uk.me.michaellloydlee.recruiter.infrastructure.parser

import org.apache.poi.wp.usermodel.HeaderFooterType
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayOutputStream

/**
 * Builds the Word documents the tests work with.
 */
object TestWordDocuments {
    /**
     * A Word document with the given paragraphs.
     *
     * @param paragraphs the paragraphs of the document.
     */
    fun docxWith(paragraphs: List<String>): ByteArray {
        val document = XWPFDocument()
        try {
            paragraphs.forEach { text ->
                val paragraph = document.createParagraph()
                paragraph.createRun().setText(text)
            }
            val bytes = ByteArrayOutputStream()
            document.write(bytes)
            return bytes.toByteArray()
        } finally {
            document.close()
        }
    }

    /**
     * A Word document with a table.
     *
     * @param rows the rows of the table, an empty list creates a document
     *   without a table.
     */
    fun docxWithTable(rows: List<List<String>>): ByteArray {
        val document = XWPFDocument()
        try {
            if (rows.isNotEmpty()) {
                val table = document.createTable(rows.size, rows.first().size)
                rows.forEachIndexed { rowIndex, row ->
                    row.forEachIndexed { cellIndex, text ->
                        table.getRow(rowIndex).getCell(cellIndex).setText(text)
                    }
                }
            }
            val bytes = ByteArrayOutputStream()
            document.write(bytes)
            return bytes.toByteArray()
        } finally {
            document.close()
        }
    }

    /**
     * A Word document with a header, a footer and a body.
     *
     * @param header the text of the header.
     * @param footer the text of the footer.
     * @param body the text of the body.
     */
    fun docxWithHeaderAndFooter(
        header: String,
        footer: String,
        body: String,
    ): ByteArray {
        val document = XWPFDocument()
        try {
            document.createHeader(HeaderFooterType.DEFAULT).apply { createParagraph().createRun().setText(header) }
            document.createFooter(HeaderFooterType.DEFAULT).apply { createParagraph().createRun().setText(footer) }
            document.createParagraph().createRun().setText(body)
            val bytes = ByteArrayOutputStream()
            document.write(bytes)
            return bytes.toByteArray()
        } finally {
            document.close()
        }
    }

    /**
     * A document that is not a Word document at all.
     */
    fun brokenDocx(): ByteArray = "this is not a word document".toByteArray()
}
