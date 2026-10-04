package io.github.jorgetroya80.madmobility

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModularityTest {
    private val modules = ApplicationModules.of(MadMobilityApplication::class.java)

    @Test
    fun `modules respect their boundaries`() {
        modules.verify()
    }

    @Test
    fun `shared does not depend on any module`() {
        val shared = modules.getModuleByName("shared").orElseThrow()

        assertThat(shared.getDirectDependencies(modules).uniqueModules()).isEmpty()
    }
}
