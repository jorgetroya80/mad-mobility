# EMT fixtures

Real EMT MobilityLabs responses captured with `curl` on 2026-10-04 (login, BiciMAD) and 2026-10-30
(bus), used by the tests as the EMT contract. Personal data and the token were replaced with fake
values; everything else is verbatim.

| File                             | Request                                                      | HTTP | `code` | Meaning                                                                                |
| -------------------------------- | ------------------------------------------------------------ | ---- | ------ | -------------------------------------------------------------------------------------- |
| `login-ok.json`                  | `GET /v2/mobilitylabs/user/login/` with `email` + `password` | 200  | `01`   | Valid token (existing one extended); `00` is also success                              |
| `login-bad-credentials.json`     | Same, wrong password                                         | 200  | `89`   | Invalid user or password                                                               |
| `bicimad-stations.json`          | `GET /v1/transport/bicimad/stations/` with `accessToken`     | 200  | `00`   | 678 stations, trimmed to 3 (normal, `light=3`, `no_available=1`)                       |
| `token-invalid.json`             | Same, unknown `accessToken` (also returned with no token)    | 401  | `80`   | Token not found in cache: log in again                                                 |
| `bus-stops-list.json`            | `POST /v1/transport/busemtmad/stops/list/` with no body      | 200  | `00`   | 4935 stops, trimmed to 72, 2115, 2768, 4025 and 1; stop 1 got the unknown code `999/1` |
| `bus-lines-info.json`            | `GET /v1/transport/busemtmad/lines/info/20261030/`           | 200  | `00`   | 236 lines, trimmed to the 20 codes of the kept stops (no `999`)                        |
| `bus-stop-detail.json`           | `GET /v1/transport/busemtmad/stops/2768/detail/`             | 200  | `00`   | Verbatim: `73` with `LA`, `N3` with `VI`                                               |
| `bus-stop-detail-72.json`        | `GET /v1/transport/busemtmad/stops/72/detail/`               | 200  | `00`   | 14 lines, trimmed to `5`, `14` (`LA`), `N1`, `N25` (`VI`, past midnight)               |
| `bus-arrivals.json`              | `POST /v2/transport/busemtmad/stops/72/arrives/`             | 200  | `00`   | 16 arrivals, trimmed to 6: `0` s, `55`, `168`, `1383`, `1553`, `999999`                |
| `bus-arrivals-missing-stop.json` | Same, stop `999999`                                          | 200  | `80`   | Verbatim: stop disabled or not found, same `code` as a bad token                       |
| `bus-token-invalid.json`         | `POST …/stops/list/` with an unknown `accessToken`           | 200  | `80`   | Verbatim: token not found in cache, but HTTP 200                                       |

## Findings

- Errors are not only signalled by HTTP status: bad credentials return HTTP 200 with `code` `89`, so the
  client must always check `code`.
- On BiciMAD, an invalid token returns HTTP 401 with a JSON body (`code` `80`); the client must read it instead of
  treating every 4xx as final.
- The login response includes `apiCounter` (`current`, `dailyUse` 20000) and `tokenSecExpiration` (86399 s).
- `datetime` has no time zone offset (Madrid local time).
- Bus stop list needs `POST` with no body: `{}` or `{"stopId": []}` gives `code` `90`, and `GET`
  gives HTTP 405.
- Stop `lines` are internal codes `"<line>/<n>"` (`"603/2"`); the line catalog translates `line` to the
  public `label` (`603` → `M3`). The catalog has no `datetime`. The list `description` still says 4935
  records after the trim.
- Stop detail returns one `dayType` per line, already today's: on Friday 2026-10-30, `LA` for day lines
  and `VI` for night lines, which cross midnight (`23:30` → `05:30`). Frequencies are strings (`"22"`).
- In arrivals, `line` is already the label; `estimateArrive` `999999` means no estimate, and real
  estimates above 20 min exist (`1553` s here).
- An invalid token gives `code` `80` with HTTP 200 on bus list, catalog and detail, but HTTP 401 on
  BiciMAD and bus arrivals.
- A missing stop with a valid token gives HTTP 200 with `code` `80` on arrivals (like a bad token) and
  `code` `90` on detail.
- **Not verified:** the response when the daily quota is exhausted. It cannot be provoked safely and is not
  documented reliably; `QuotaTracker` stops calling the EMT before reaching it.
