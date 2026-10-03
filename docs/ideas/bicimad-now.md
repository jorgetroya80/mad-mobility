# BiciMAD Now

## Problem Statement

¿Cómo podríamos saber en menos de 5 segundos si habrá bici, o un anclaje libre, cerca de donde estoy, con una arquitectura limpia que sirva como proyecto de portfolio y que se pueda extender a autobuses EMT?

## Recommended Direction

Una API propia como producto (BFF) que aísla la API de EMT MobilityLabs, normaliza sus datos y los sirve desde caché. El frontend en React es un cliente más de esa API y consulta su estado cada 60 segundos.

No se usa tiempo real (WebSocket/SSE) ni notificaciones push. La EMT solo ofrece REST, y una prueba de 15 minutos (2026-10-03) mostró que los datos de BiciMAD se actualizan en bloques cada ~2 minutos (150-180 de 677 estaciones por bloque). Consultar más a menudo no da datos más frescos.

El valor de portfolio está en lo que no se ve: monolito modular con arquitectura hexagonal por módulo, capa anticorrupción frente a la EMT, caché con fallback a datos antiguos, contrato OpenAPI generado desde el código (springdoc) y resiliencia (timeout, reintento, circuit breaker).

## Architecture (Backend)

**Stack:** Kotlin + Spring Boot 4.0.5 (Java 24 Temurin) sobre JVM estándar (sin GraalVM native image), Spring Modulith, Resilience4j, Caffeine, springdoc-openapi.

```
api/
  shared/emt/          infraestructura común, sin dominio
    EmtAuth            login, caché del token (24 h), single-flight relogin
    EmtHttpClient      timeout 5 s, 1 reintento con backoff, circuit breaker, chequeo de "code"
    CacheService       TTL por módulo, stale-on-error, single-flight
    QuotaTracker       consumo de cupo por módulo
  modules/
    bicimad/           MVP
      domain/          Station, StationStatus
      application/     FindNearbyStations, GetStation
      ports/           StationProvider
      adapters/emt/    EmtStationProvider (traduce EMT -> dominio)
      http/            /v1/bicimad/stations
    bus/               futuro, misma forma
```

**Patrones:**

- Monolito modular: BiciMAD y Bus son dominios separados, sin modelo común forzado.
- Hexagonal (puertos y adaptadores) dentro de cada módulo: el dominio no conoce la EMT.
- Capa anticorrupción en cada adaptador: `light`, `activate`, `no_available` y `estimateArrive=999999` se traducen en el borde.
- Shared kernel solo de infraestructura (token, cliente HTTP, caché, cupo).
- Abierto/cerrado: añadir Bus es añadir un módulo; BiciMAD no cambia.
- Spring Modulith verifica en tests que los módulos no se importan entre sí.
- No hay abstracción genérica `TransportProvider<T>`. Si se quiere "todo lo cercano", se usará un módulo de composición (`nearby/`) que llame a los casos de uso de cada módulo.

**Contrato:**

- `GET /v1/bicimad/stations?near=lat,lon&radius=500&need=bikes|docks`
- `GET /v1/bicimad/stations/{id}`
- JSON plano con `lat`/`lon` (no GeoJSON). Identificador: `id` de la EMT; `number` se muestra como etiqueta.
- Errores con RFC 9457 (`application/problem+json`).
- Cada respuesta incluye `updatedAt`, `stale` y `source: "EMT Madrid MobilityLabs"` (exigido por la licencia).

**Caché y frescura:**

- Las 677 estaciones en memoria con TTL de 60 s (~1.440 llamadas/día, el 7 % del cupo de 20k).
- El filtro de cercanía (haversine) se calcula en el backend sobre la caché, sin llamar a `arroundxy` de la EMT.
- ETag + `Cache-Control: max-age` hacia el cliente, para que las consultas periódicas devuelvan `304`.
- Si la EMT falla, se sirve el último dato bueno con `stale: true`.

**Diferencias previstas para el módulo Bus:** las llegadas se piden por parada (caché por parada, TTL 20-30 s, con límite de cupo propio); paradas, líneas y rutas son casi estáticas (caché de horas o días). Por eso la caché y el cupo se configuran por módulo.

**Operación:** credenciales en variables de entorno; `/health/live` y `/health/ready`; métricas de cache hit, latencia de EMT, errores y cupo (`apiCounter`); logs JSON con request id; rate limit por IP; CORS para el dominio del frontend.

## Architecture (Frontend)

**Stack:** Vite + React + TypeScript (SPA estática), TanStack Query, `openapi-typescript` + `openapi-fetch`, MapLibre GL con teselas de OpenFreeMap, Tailwind CSS.

```
web/src/
  features/
    bicimad/        StationsMap, StationList, StationDetail, useNearbyStations
    bus/            futuro, misma forma
  shared/
    api/            cliente tipado generado desde el OpenAPI
    map/            mapa base reutilizable (MapLibre)
    geo/            useGeolocation (fallback a Puerta del Sol)
    ui/             componentes base, estados vacío/error, indicador "hace X"
    storage/        useFavorites (localStorage)
```

**Principios:**

- Organización por feature, espejo de los módulos del backend. `features/bicimad` no importa de `features/bus`; lo compartido va en `shared/`.
- Estado de servidor solo en TanStack Query: `refetchInterval` de 60 s (igual que el TTL del backend), sin consultas con la pestaña oculta, refetch al recuperar el foco, reintentos automáticos.
- Sin store global: estado de UI local, favoritos en `localStorage`.
- Estado en la URL (`?station=1409&need=docks`): enlaces compartibles y botón atrás funcional.
- Tipos generados desde el OpenAPI: si el contrato cambia, el build de TypeScript falla.

**Responsive (mobile-first):**

- Móvil: mapa a pantalla completa con la lista de estaciones en un panel inferior deslizable (bottom sheet); detalle de estación en el mismo panel.
- Tablet/escritorio (`md:` en adelante): lista y detalle en un panel lateral fijo, mapa ocupando el resto.
- Controles táctiles de al menos 44 px; filtro bicis/anclajes accesible con el pulgar.
- Probar en anchos de 360 px, 768 px y 1280 px.

**Estados de UI:**

- Cargando, vacío ("ninguna estación en 500 m"), error.
- Datos antiguos: si la API devuelve `stale: true`, aviso "datos de hace X min, la EMT no responde".
- Ubicación denegada: mapa centrado en Puerta del Sol con buscador.
- Accesibilidad: disponibilidad indicada con color y con número o icono (daltonismo).

**Tests:** Vitest + Testing Library para componentes; MSW simula la API a partir del OpenAPI, para desarrollar sin backend y sin gastar cupo. Sin tests end-to-end (Playwright fuera del alcance).

**Atribución:** "Datos: EMT Madrid MobilityLabs" y la atribución de OpenStreetMap/OpenFreeMap visibles en el mapa. Revisar los términos de uso de las teselas antes de publicar.

## Repository Layout (Monorepo)

```
emt-demo/
  api/              Kotlin + Spring Boot (Gradle)
  web/              React + Vite (pnpm)
  openapi/          bicimad.json, contrato generado por springdoc (versionado en git)
  docs/
  .github/workflows/
```

- Builds independientes: Gradle en `api/`, pnpm en `web/`. Sin orquestación cruzada (no se usa el plugin de Node para Gradle).
- Código primero: los controladores Spring son la fuente de verdad; springdoc exporta `openapi/bicimad.json` en el build y se commitea. React genera sus tipos con `openapi-typescript`. CI falla si el archivo commiteado difiere del regenerado.
- Desarrollo local: el proxy de Vite redirige `/api` a `localhost:8080` (sin CORS en local).
- CI con filtros por ruta: `api/**` ejecuta el job del backend, `web/**` el del frontend y `openapi/**` ambos.
- `api/` y `web/` se despliegan por separado (plataforma por definir).

## Hallazgos de la API EMT (2026-10-03)

- Base URL `https://openapi.emtmadrid.es/`. Login `GET /v2/mobilitylabs/user/login/` con cabeceras `email` y `password` (o `X-ClientId` + `passKey`). Token de 86.399 s. Cupo 20.000 llamadas/día con login genérico.
- `GET /v1/transport/bicimad/stations/` devuelve 677 estaciones en ~0,55 s (máximo observado 1,76 s). 1 de 31 llamadas no respondió en 60 s.
- El formato real difiere de la documentación: hay campos nuevos (`geofenced_capacity`, `tipo_estacionPBSC`, `virtualDelete`, `image`).
- `light`: 0 = baja, 2 = media, 1 = alta, 3 = no disponible (orden no intuitivo).
- La API tiene CORS abierto, pero las credenciales nunca deben llegar al navegador.

## Key Assumptions to Validate

- [x] Una llamada devuelve todas las estaciones rápido: 677 en ~0,55 s.
- [x] Los datos cambian lo bastante: bloques cada ~2 min.
- [x] El endpoint sigue vivo; el formato difiere de la documentación y lo absorbe la capa anticorrupción.
- [ ] El `id` de estación es estable entre días: comparar capturas de días distintos.
- [ ] El cupo de 20k alcanza cuando se añada Bus: medir con `QuotaTracker`.

## MVP Scope

**Dentro:**

- Módulo `bicimad` con la infraestructura compartida `shared/emt`.
- Los dos endpoints, OpenAPI y health checks.
- UI responsive en React: mapa y lista de estaciones cercanas, filtro bicis/anclajes.
- Consulta cada 60 s, pausada con la pestaña oculta; indicador "actualizado hace X".
- Favoritos en `localStorage`.

**Fuera:** todo lo de la lista siguiente.

## Not Doing (and Why)

- WebSocket, SSE y push: la EMT solo ofrece REST y actualiza cada ~2 min; no aportan valor.
- Base de datos e historial: no hacen falta para el MVP. Son la fase 2 (predicción por hora).
- BiciMAD GO y BiciPARK: GO probablemente cerrado; BiciPARK no es parte del problema principal.
- Cuentas de usuario: los favoritos van en `localStorage`.
- Abstracción genérica bici/bus: son dominios distintos; si hace falta, se compondrá.
- Varias instancias y Redis: una instancia basta.
- Tests end-to-end (Playwright): fuera del alcance; bastan Vitest, Testing Library y MSW.
- PWA / modo offline: no aporta al objetivo del MVP.

## Deployment

- Plataforma: _por definir_ (requisito: gratuita).
- Credenciales: email/contraseña en local; registrar la app en MobilityLabs (`X-ClientId` + `passKey`) antes de publicar.

## Open Questions

- Plataforma de despliegue (no prioritaria por ahora).

