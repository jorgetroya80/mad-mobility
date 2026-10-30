package io.github.jorgetroya80.madtransit.modules.bus.http

import io.github.jorgetroya80.madtransit.modules.bus.application.FindNearbyStops
import io.github.jorgetroya80.madtransit.modules.bus.application.GetStop
import io.github.jorgetroya80.madtransit.shared.web.ProblemBody
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/v1/bus/stops")
// Public API: no authentication (security: [] in the OpenAPI document)
@SecurityRequirements
@Tag(name = "Bus", description = "EMT bus stops, refreshed from the EMT once a day.")
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
class BusStopsController(
    private val findNearbyStops: FindNearbyStops,
    private val getStop: GetStop,
) {
    @GetMapping
    @Operation(
        operationId = "listBusStops",
        summary = "List bus stops",
        description = "Every stop sorted by number, or only those within `radius` of `near` sorted by distance.",
    )
    @ApiResponse(responseCode = "200", description = "The stops.")
    fun stops(query: BusStopsQuery): BusStopsResponse = BusStopsResponse.from(findNearbyStops(query.area()))

    @GetMapping("/{number}")
    @Operation(
        operationId = "getBusStop",
        summary = "Get a bus stop",
        description = "The stop with its address and its lines, with today's schedule and frequency in Madrid.",
    )
    @ApiResponse(responseCode = "200", description = "The stop.")
    @ApiResponse(
        responseCode = "404",
        description = "No stop with this number.",
        content = [Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemBody::class))],
    )
    fun stop(
        @Parameter(
            description = "Stop number, as shown on the stop pole (`number` in the list).",
            schema = Schema(type = "string", pattern = STOP_NUMBER_PATTERN, example = "72"),
        )
        @PathVariable number: String,
    ): BusStopDetailResponse {
        if (!number.matches(STOP_NUMBER)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "number must match $STOP_NUMBER_PATTERN")
        }
        return BusStopDetailResponse.from(getStop(number))
    }

    private companion object {
        const val STOP_NUMBER_PATTERN = "^[0-9]+$"
        val STOP_NUMBER = Regex(STOP_NUMBER_PATTERN)
    }
}
