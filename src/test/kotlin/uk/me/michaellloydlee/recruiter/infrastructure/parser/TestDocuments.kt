package uk.me.michaellloydlee.recruiter.infrastructure.parser

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream

/**
 * Builds the PDF documents the tests work with.
 */
object TestDocuments {
    /**
     * A PDF with the given lines of text.
     *
     * @param lines the lines of the page.
     */
    fun pdfWith(lines: List<String>): ByteArray {
        val document = PDDocument()
        try {
            val page = PDPage()
            document.addPage(page)
            val content = PDPageContentStream(document, page)
            try {
                content.beginText()
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                var y = 700f
                lines.forEach { line ->
                    content.newLineAtOffset(100f, y)
                    content.showText(line)
                    y -= 20f
                }
                content.endText()
            } finally {
                content.close()
            }
            val bytes = ByteArrayOutputStream()
            document.save(bytes)
            return bytes.toByteArray()
        } finally {
            document.close()
        }
    }

    /**
     * A PDF that is not a PDF at all.
     */
    fun brokenPdf(): ByteArray = "this is not a pdf".toByteArray()

    /**
     * A PDF that is protected with a password.
     */
    fun protectedPdf(): ByteArray {
        val document = PDDocument()
        try {
            val page = PDPage()
            document.addPage(page)
            document.protect(
                StandardProtectionPolicy("owner-password", "user-password", AccessPermission()),
            )
            val bytes = ByteArrayOutputStream()
            document.save(bytes)
            return bytes.toByteArray()
        } finally {
            document.close()
        }
    }
}
