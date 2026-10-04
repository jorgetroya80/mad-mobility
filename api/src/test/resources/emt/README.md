# EMT fixtures

Real EMT MobilityLabs responses captured on 2026-10-04 with `curl`, used by the tests as the
EMT contract. Personal data and the token were replaced with fake values; everything else is verbatim.

| File                         | Request                                                      | HTTP | `code` | Meaning                                                          |
| ---------------------------- | ------------------------------------------------------------ | ---- | ------ | ---------------------------------------------------------------- |
| `login-ok.json`              | `GET /v2/mobilitylabs/user/login/` with `email` + `password` | 200  | `01`   | Valid token (existing one extended); `00` is also success        |
| `login-bad-credentials.json` | Same, wrong password                                         | 200  | `89`   | Invalid user or password                                         |
| `bicimad-stations.json`      | `GET /v1/transport/bicimad/stations/` with `accessToken`     | 200  | `00`   | 678 stations, trimmed to 3 (normal, `light=3`, `no_available=1`) |
| `token-invalid.json`         | Same, unknown `accessToken` (also returned with no token)    | 401  | `80`   | Token not found in cache: log in again                           |

## Findings

- Errors are not only signalled by HTTP status: bad credentials return HTTP 200 with `code` `89`, so the
  client must always check `code`.
- An invalid token returns HTTP 401 with a JSON body (`code` `80`); the client must read it instead of
  treating every 4xx as final.
- The login response includes `apiCounter` (`current`, `dailyUse` 20000) and `tokenSecExpiration` (86399 s).
- `datetime` has no time zone offset (Madrid local time).
- **Not verified:** the response when the daily quota is exhausted. It cannot be provoked safely and is not
  documented reliably; `QuotaTracker` stops calling the EMT before reaching it.
