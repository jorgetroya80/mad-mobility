package io.github.jorgetroya80.madmobility.modules.bicimad.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.data.Percentage
import org.junit.jupiter.api.Test

class GeoPointTest {
    @Test
    fun `distance from Sol to Cibeles is within 1 percent of the reference`() {
        assertThat(SOL.distanceTo(CIBELES)).isCloseTo(SOL_TO_CIBELES_METERS, Percentage.withPercentage(1.0))
    }

    @Test
    fun `distance is symmetric`() {
        assertThat(CIBELES.distanceTo(SOL)).isEqualTo(SOL.distanceTo(CIBELES))
    }

    @Test
    fun `distance to the same point is zero`() {
        assertThat(SOL.distanceTo(SOL)).isZero()
    }

    @Test
    fun `range limits are valid coordinates`() {
        assertThat(GeoPoint(-90.0, -180.0).lat).isEqualTo(-90.0)
        assertThat(GeoPoint(90.0, 180.0).lon).isEqualTo(180.0)
    }

    @Test
    fun `latitude out of range is rejected`() {
        assertThatThrownBy { GeoPoint(91.0, 0.0) }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("lat")
    }

    @Test
    fun `longitude out of range is rejected`() {
        assertThatThrownBy { GeoPoint(0.0, -180.5) }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("lon")
    }

    @Test
    fun `not a number is rejected`() {
        assertThatThrownBy { GeoPoint(Double.NaN, 0.0) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private companion object {
        val SOL = GeoPoint(40.4168, -3.7038)
        val CIBELES = GeoPoint(40.4193, -3.6931)

        // Spherical law of cosines with the mean Earth radius (6,371,008.8 m), computed outside this code
        const val SOL_TO_CIBELES_METERS = 947.5
    }
}
