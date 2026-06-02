package com.example.unmarkeddetector.detection

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlateValidatorTest {

    @Test
    fun `extracts clean polish plate without leading garbage`() {
        val plates = PlateValidator.extractPlates("xPO 25Y7P")

        assertThat(plates).containsExactly("PO25Y7P")
    }

    @Test
    fun `extracts valid plate from longer compact token`() {
        val plates = PlateValidator.extractPlates("ABCRZE2A63XYZ")

        assertThat(plates).contains("RZE2A63")
    }

    @Test
    fun `rejects random words and invalid prefixes`() {
        assertThat(PlateValidator.extractPlates("tablica policyjna")).isEmpty()
        assertThat(PlateValidator.isValidPolishPlate("XX12345")).isFalse()
    }

    @Test
    fun `accepts lowercase OCR output`() {
        assertThat(PlateValidator.extractPlates("po 25y7p")).containsExactly("PO25Y7P")
    }

    @Test
    fun `repairs zero misread inside letter prefix`() {
        assertThat(PlateValidator.extractPlates("P0 25Y7P")).containsExactly("PO25Y7P")
    }

    @Test
    fun `repairs cropped first letter of county prefix`() {
        assertThat(PlateValidator.extractPlates("OOT F038")).containsExactly("WOTF038")
    }
}
