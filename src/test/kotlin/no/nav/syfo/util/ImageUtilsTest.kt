package no.nav.syfo.util

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ComponentColorModel
import java.awt.image.DataBuffer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ImageUtilsTest {

    /**
     * Lager et 16-bit per kanal PNG-bilde. Dette er det problematiske formatet som
     * foraarsaket feilen "Illegal band size: should be 0 < size <= 8" i JPEGFactory,
     * fordi JPEG-enkodingen kun støtter 8-bit kanaler.
     */
    private fun create16BitPngInputStream(): ByteArrayInputStream {
        val colorModel = ComponentColorModel(
            ColorSpace.getInstance(ColorSpace.CS_sRGB),
            intArrayOf(16, 16, 16),
            false,
            false,
            Transparency.OPAQUE,
            DataBuffer.TYPE_USHORT
        )
        val raster = colorModel.createCompatibleWritableRaster(100, 100)
        val image = BufferedImage(colorModel, raster, false, null)
        return ByteArrayOutputStream().use { baos ->
            ImageIO.write(image, "png", baos)
            ByteArrayInputStream(baos.toByteArray())
        }
    }

    @Test
    fun `convertImageToPDF skal haandtere 16-bit PNG uten aa kaste feil`() {
        assertDoesNotThrow {
            convertImageToPDF(create16BitPngInputStream(), ByteArrayOutputStream())
        }
    }
}
