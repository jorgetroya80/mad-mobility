package io.github.jorgetroya80.madmobility.modules.bicimad.ports

import io.github.jorgetroya80.madmobility.modules.bicimad.domain.StationSnapshot

/** Source of the current BiciMAD stations. */
interface StationProvider {
    fun snapshot(): StationSnapshot
}
