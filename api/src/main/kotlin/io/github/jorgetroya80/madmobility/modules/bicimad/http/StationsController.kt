package io.github.jorgetroya80.madmobility.modules.bicimad.http

import io.github.jorgetroya80.madmobility.modules.bicimad.application.FindNearbyStations
import io.github.jorgetroya80.madmobility.modules.bicimad.application.GetStation
import io.github.jorgetroya80.madmobility.shared.web.ProblemBody
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/bicimad/stations")
// Public API: no authentication (security: [] in the OpenAPI document)
@SecurityRequirements
@Tag(name = "BiciMAD", description = "BiciMAD stations, refreshed from the EMT every minute.")
@ApiResponse(
    responseCode = "400",
    description = "Invalid parameter.",
    content = [Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemBody::class))],
)
@ApiResponse(
    responseCode = "429",
    description = "Too many requests from this client.",
    headers = [Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait.", schema = Schema(type = "integer"))],
    content = [Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemBody::class))],
)
@ApiResponse(
    responseCode = "502",
    description = "EMT Madrid returned an unexpected response and there is no cached copy.",
    content = [Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemBody::class))],
)
@ApiResponse(
    responseCode = "503",
    description = "EMT Madrid is unavailable or its daily quota is exhausted, and there is no cached copy.",
    headers = [Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait, when known.", schema = Schema(type = "integer"))],
    content = [Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemBody::class))],
)
@ApiResponse(
    responseCode = "504",
    description = "EMT Madrid did not answer in time and there is no cached copy.",
    content = [Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemBody::class))],
)
class StationsController(
    private val findNearbyStations: FindNearbyStations,
    private val getStation: GetStation,
) {
    @GetMapping
    @Operation(
        operationId = "listStations",
        summary = "List stations",
        description =
            "Every station, or only those within `radius` of `near` sorted by distance. " +
                "With `need`, stations that can serve it come first.",
    )
    @ApiResponse(responseCode = "200", description = "The stations.")
    fun stations(query: StationsQuery): StationsResponse = StationsResponse.from(findNearbyStations(query.area(), query.need()))

    @GetMapping("/{id}")
    @Operation(operationId = "getStation", summary = "Get a station")
    @ApiResponse(responseCode = "200", description = "The station.")
    @ApiResponse(
        responseCode = "404",
        description = "No station with this id.",
        content = [Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemBody::class))],
    )
    fun station(
        @Parameter(description = "Station id (`id` in the list).") @PathVariable id: Int,
    ): StationDetailResponse = StationDetailResponse.from(getStation(id))
}
