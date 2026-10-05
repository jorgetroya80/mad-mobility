package io.github.jorgetroya80.madmobility.modules.bicimad.http

import io.github.jorgetroya80.madmobility.modules.bicimad.application.FindNearbyStations
import io.github.jorgetroya80.madmobility.modules.bicimad.application.GetStation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/bicimad/stations")
class StationsController(
    private val findNearbyStations: FindNearbyStations,
    private val getStation: GetStation,
) {
    @GetMapping
    fun stations(query: StationsQuery): StationsResponse = StationsResponse.from(findNearbyStations(query.area(), query.need()))

    @GetMapping("/{id}")
    fun station(
        @PathVariable id: Int,
    ): StationDetailResponse = StationDetailResponse.from(getStation(id))
}
