# api

Spring Boot API (Kotlin) that serves BiciMAD station data from the [EMT Madrid MobilityLabs](https://mobilitylabs.emtmadrid.es/) API.

## What it does

- **Lists BiciMAD stations**: all of them, or only those near a point, sorted by distance. It can put first the stations that have what you need: a bike or a free dock.
- **Looks up a single station** by the number shown on it.
- **Caches the EMT data** and refreshes it every minute. If the EMT fails, it serves the last good copy.
- **Publishes a typed client**: each release publishes `@jorgetroya80/bicimad-client` (in [`client/`](client)), generated from the API's OpenAPI contract, for the web to consume.

Data powered by [EMT de Madrid](https://www.emtmadrid.es).
