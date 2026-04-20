package no.nav.syfo.util

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.util.Matrix
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.AffineTransformOp.TYPE_BILINEAR
import java.awt.image.BufferedImage
import java.io.*
import javax.imageio.ImageIO

fun convertImageToPDF(imageStream: InputStream, outputStream: OutputStream) {
    PDDocument().use { document ->
        val page = PDPage(PDRectangle.A4)
        document.addPage(page)
        val image = toPortait(toRgb(ImageIO.read(imageStream)))

        val quality = 1.0f

        val pdImage = JPEGFactory.createFromImage(document, image, quality)
        val imageSize = scale(pdImage, page)

        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, false).use {
            it.drawImage(pdImage, Matrix(imageSize.width, 0f, 0f, imageSize.height, 0f, 0f))
        }

        document.save(outputStream)
    }
}

/**
 * Konverterer et [BufferedImage] til fargemodellen TYPE_INT_RGB (standard 8-bit RGB).
 *
 * Noen bilder som mottas som vedlegg kan ha uvanlige fargemodeller, for eksempel:
 * - CMYK (brukt i trykksaker)
 * - 16-bit per kanal (høy fargdybde)
 * - Bilder med alfakanal (gjennomsiktighet)
 *
 * JPEG-enkodingen i PDFBox (`JPEGFactory.createFromImage`) støtter kun bilder med
 * 8-bit per kanal i RGB-format. Hvis bildet ikke er i riktig format, kastes feilen:
 * "Illegal band size: should be 0 < size <= 8"
 *
 * Denne funksjonen tegner bildet inn i et nytt, tomt TYPE_INT_RGB-bilde slik at alle
 * kanaler normaliseres til 8-bit RGB, uansett hva originalformatet var.
 */
private fun toRgb(image: BufferedImage): BufferedImage {
    if (image.type == BufferedImage.TYPE_INT_RGB) {
        return image
    }
    val rgbImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
    rgbImage.createGraphics().apply {
        drawImage(image, 0, 0, null)
        dispose()
    }
    return rgbImage
}

private fun toPortait(image: BufferedImage): BufferedImage {
    if (image.height >= image.width) {
        return image
    }

    val rotateTransform = AffineTransform.getRotateInstance(
        Math.toRadians(90.0),
        (image.height / 2f).toDouble(),
        (image.height / 2f).toDouble()
    )

    return AffineTransformOp(rotateTransform, TYPE_BILINEAR)
        .filter(image, BufferedImage(image.height, image.width, image.type))
}

data class ImageSize(val width: Float, val height: Float)

private fun scale(image: PDImageXObject, page: PDPage): ImageSize {
    var width = image.width.toFloat()
    var height = image.height.toFloat()

    if (width > page.cropBox.width) {
        width = page.cropBox.width
        height = width * image.height.toFloat() / image.width.toFloat()
    }

    if (height > page.cropBox.height) {
        height = page.cropBox.height
        width = height * image.width.toFloat() / image.height.toFloat()
    }

    return ImageSize(width, height)
}
