# Plan: web — "Mi ubicación" vuelve a centrar el mapa

- Created: 2026-10-29
- Status: **approved** (2026-10-29)
- Spec: [SPEC-web-bicimad.md](../specs/SPEC-web-bicimad.md) (regla 1 y SC2; este plan la corrige, no crea una spec nueva)

## Overview

En móvil (Safari y Chrome en iOS), "Mi ubicación" no parecía funcionar. La investigación del 2026-10-29 encontró dos causas:

1. **Ajuste del dispositivo (no es un fallo de la web).** Con la Localización de iOS desactivada para "Sitios web de Safari", el navegador responde `PERMISSION_DENIED` sin mostrar ningún aviso. Tras activarla, Safari ya pide permiso.
2. **Fallo del código.** `useEaseTo` (`shared/ui/base-map/useMapLibre.ts`) solo mueve el mapa cuando cambian `lng` o `lat`. Si el usuario arrastra el mapa y pulsa "Mi ubicación", la respuesta trae la misma posición (`maximumAge` 60 s, o un GPS quieto) y el mapa no vuelve. Lo confirmó el usuario en un iPhone.

Revisando el código aparecieron además dos casos relacionados:

3. **Con una estación abierta**, el foco del mapa es la estación (`StationsMap`: `selected ?? center`), así que "Mi ubicación" tampoco lleva al usuario a su posición.
4. **Con el permiso denegado**, pulsar el botón no cambia nada visible: muestra "Localizando…" un instante y el aviso sigue igual. Ese aviso no distingue entre denegada y no disponible, y no dice cómo activar la ubicación. Fue justo la situación de la causa 1.

## Decisiones de implementación

- **Una rama (`fix/web-locate-recenter`) y un solo PR, con un commit por tarea.** Sin `Co-Authored-By`. Título del PR `fix(web): …`, para que release-please publique un parche de `web`. El PR solo toca `web/` y la spec y el plan de `docs/`.
- **El foco del mapa se libera mientras se pide la ubicación** (`mapCenter` sale `undefined` en `pending`) y la respuesta lo devuelve. Pasar de `undefined` a la posición cuenta como un foco nuevo, así que `useEaseTo` se ejecuta aunque los números sean los mismos. Se descartó una `focusKey` en `BaseMap`/`StationsMap`: oxlint la rechaza (`exhaustive-effect-dependencies`, `max-params`) y además ampliaba la API de `shared` y `widgets` para un problema de la página.
- **La lista no cambia:** sigue usando `center` (la última respuesta), así que no parpadea mientras se pide la ubicación (regla del 2026-10-14).
- **Con una estación abierta, el botón centra el mapa en el usuario sin cerrar el detalle** (Q1). Gana el último foco que pidió el usuario: `MapPage` recuerda qué estación estaba abierta al pulsar "Mi ubicación", y mientras siga abierta el mapa enfoca al usuario. Elegir otra estación devuelve el foco a la estación.
- **Texto del aviso con el permiso denegado** (Q2): "No has dado permiso de ubicación: mostrando estaciones cerca de la Puerta del Sol".
- **Prueba en iPhone en Render después del merge** (Q3). El plan se sube en el mismo PR (Q4).
- **TDD:** primero un test de `MapPage` que falla y después el cambio, como en el prototipo.

## Grafo de dependencias

```
T1 el mapa vuelve al usuario (prototipo hecho, sin commit)
 ├── T2 "Mi ubicación" con una estación abierta
 └── T3 aviso con permiso denegado
        └── T4 spec, prueba en iPhone y cierre
```

T2 y T3 no dependen entre sí, pero las dos tocan `MapPage.test.tsx`, así que se hacen una detrás de otra.

## Task List

- [x] **T1: El mapa vuelve al usuario aunque la posición sea la misma** (S, ~15 min; el código ya está en la rama)
  - Descripción: `useMapLocation` expone `mapCenter`, que es `undefined` mientras el estado es `pending` e igual a `center` cuando hay respuesta. `MapPage` pasa `mapCenter` a `StationsMap`. Commit `fix(web): bring the map back to the user on "Mi ubicación"`.
  - Aceptación:
    - Con la ubicación concedida, pulsar "Mi ubicación" pasa por un foco `undefined` y termina en `USER` (test nuevo en `MapPage.test.tsx`).
    - Mientras se pide la ubicación, la lista y el aviso siguen describiendo la última respuesta y el mapa no se mueve (el test existente "while Mi ubicación asks again" ahora espera `focus` `undefined`).
    - Con la ubicación denegada, pulsar el botón vuelve a centrar en la Puerta del Sol (test nuevo).
  - Verificación: `pnpm --filter web test`, `pnpm --filter web typecheck`, `pnpm --filter web lint`.
  - Archivos: `pages/map/model/useMapLocation.ts`, `pages/map/ui/MapPage.tsx`, `pages/map/ui/MapPage.test.tsx`.
  - Dependencias: ninguna.

- [x] **T2: "Mi ubicación" con una estación abierta** (M, ~45 min)
  - Descripción: pulsar el botón con el detalle abierto centra el mapa en el usuario y deja el detalle abierto. `MapPage` guarda el número de la estación abierta al pulsar. Mientras esa estación siga abierta, `StationsMap` enfoca el centro y no la estación (prop nueva, p. ej. `focusSelected: boolean`); la estación sigue marcada como seleccionada en el mapa.
  - Aceptación:
    - Con `?station=1a`, pulsar "Mi ubicación" deja `?station=1a` y el detalle abiertos, y el foco del mapa termina en `USER`, con `selectedId` `1a`.
    - Después, elegir otra estación (en el mapa o en el buscador) vuelve a enfocar esa estación.
    - Sin estación abierta, el comportamiento de T1 no cambia.
  - Verificación: tests nuevos en `MapPage.test.tsx` y `StationsMap.test.tsx`; `pnpm --filter web test`.
  - Archivos: `pages/map/ui/MapPage.tsx`, `pages/map/ui/MapPanel.tsx` (si el botón necesita avisar a la página), `widgets/stations-map/ui/StationsMap.tsx` (+ test), `MapPage.test.tsx`.
  - Dependencias: T1.

- [x] **T3: Aviso con el permiso denegado** (S, ~30 min)
  - Descripción: con `denied`, el aviso dice que no se ha dado permiso. Con `unavailable` (timeout o sin señal), mantiene el texto actual. Hace falta que `useMapLocation` diga por qué se usa Sol (`denied` o `unavailable`), no solo `isApproximateLocation`.
  - Aceptación:
    - Denegada: el aviso dice "No has dado permiso de ubicación: mostrando estaciones cerca de la Puerta del Sol".
    - No disponible: sigue el texto actual "Sin ubicación: mostrando estaciones cerca de la Puerta del Sol".
    - El aviso es texto, no solo color, y no se anuncia de nuevo en cada reintento que da la misma respuesta.
  - Verificación: tests de `StationList` y `MapPage`; `pnpm --filter web test`.
  - Archivos: `pages/map/model/useMapLocation.ts`, `pages/map/ui/MapPanel.tsx`, `widgets/station-list/ui/StationList.tsx` (+ test), `MapPage.test.tsx`.
  - Dependencias: T1.

### Checkpoint: T1-T3

- [ ] `pnpm --filter web test` (cobertura ≥ 80 %), `typecheck`, `lint` y `oxfmt --check` en verde.
- [ ] `pnpm --filter web dev:mock` en el escritorio: arrastrar el mapa y pulsar "Mi ubicación" lo devuelve; con una estación abierta, lo centra en el usuario sin cerrar el detalle; con el permiso bloqueado en el navegador, se ve el aviso nuevo.
- [ ] Revisión humana antes de T4.

- [ ] **T4: Spec, prueba en iPhone y cierre** (S, ~30 min)
  - Descripción: corregir la regla 1 y SC2 de `SPEC-web-bicimad.md` (el botón vuelve a centrar el mapa aunque la posición no cambie, también con una estación abierta; el texto del aviso con el permiso denegado) y añadir una entrada a su changelog. Commit `docs`, en el mismo PR. Después del merge y del despliegue en Render, probar en un iPhone real con Safari y con Chrome. Plan a **implemented** en un PR `docs` posterior.
  - Aceptación:
    - En el iPhone, arrastrar el mapa y pulsar "Mi ubicación" lo devuelve a la posición del usuario, en Safari y en Chrome.
    - Con la Localización de iOS desactivada, aparece el aviso de T3.
    - La spec y el plan reflejan lo implementado.
  - Verificación: CI en verde en el PR; manual en el iPhone contra https://mad-transit-web.onrender.com.
  - Archivos: `docs/specs/SPEC-web-bicimad.md`, este plan.
  - Dependencias: T1-T3.

### Checkpoint final

- [ ] Aceptación de T1-T4 comprobada; CI en verde.
- [ ] Revisión humana del título del PR (`fix(web): …`) antes del squash merge.

## Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| R1: no se puede probar en el iPhone antes del merge: la geolocalización exige HTTPS, y `vite --host` en la red local es HTTP | Medio | Probar en Render después del merge (Q3); si falla, otro PR `fix` |
| La respuesta llega en el mismo render que `pending` y React agrupa los dos cambios, así que el foco nunca pasa por `undefined` | Bajo | `getCurrentPosition` responde siempre de forma asíncrona, incluso con una posición en caché; el test de `MapPage` lo cubre con un stub asíncrono |
| Tras centrar en el usuario con el detalle abierto (T2), un cambio de padding del bottom sheet vuelve a enfocar la estación | Bajo | El foco depende de la estación recordada, no del padding; test con el sheet expandido |
| iOS no permite distinguir "denegado por la web" de "Localización del sistema desactivada" (las dos dan `PERMISSION_DENIED`) | Bajo | El texto de T3 solo dice que no hay permiso, válido en los dos casos |

## Open Questions

Resueltas el 2026-10-29:

- **Q1:** con una estación abierta, el botón centra el mapa en el usuario sin cerrar el detalle.
- **Q2:** el aviso dice "No has dado permiso de ubicación: mostrando estaciones cerca de la Puerta del Sol".
- **Q3:** la prueba en el iPhone se hace en Render después del merge.
- **Q4:** el plan se sube en el mismo PR.

## Estimación

Unas 2 h en una sesión: T1 (~15 min), T2 (~45 min), T3 (~30 min), T4 (~30 min) y los checkpoints.

## Changelog

- 2026-10-29: versión inicial, a partir de la investigación del fallo en iOS.
- 2026-10-29: Q1-Q4 resueltas; plan aprobado. T2 mantiene el detalle abierto y centra el mapa en el usuario.
- 2026-10-29: T1 implementada. `useMapLocation` expone `mapCenter`, que queda `undefined` mientras se pide la ubicación, y `MapPage` se lo pasa al mapa. Tests nuevos para el usuario (misma posición) y para Sol (denegada otra vez); sin el cambio fallan, igual que el test existente de "while Mi ubicación asks again".
- 2026-10-29: T2 implementada. El hook `useUserFocus` (en `pages/map/model`) recuerda que se pulsó "Mi ubicación" con la estación abierta y lo olvida cuando cambia `?station`. `StationsMap` recibe `focusSelected`: la estación sigue marcada, pero el mapa se centra en el usuario. `MapPanel` no cambia: `MapPage` le pasa un `locate` que primero llama a `focusUser`. Tests: el detalle sigue abierto y el foco va al usuario; elegir otra estación devuelve el foco; al plegar el sheet el foco sigue en el usuario.
- 2026-10-29: T3 implementada. `useMapLocation` expone `isLocationDenied`, sacado de la última respuesta y no del estado en curso, así que el aviso no cambia mientras se reintenta. `StationList` recibe `isLocationDenied` (opcional) y elige entre los dos textos. El stub de `MapPage.test.tsx` acepta ahora `unavailable` (`POSITION_UNAVAILABLE`) para probar el texto "Sin ubicación". Los tests de `MapPage` con el permiso denegado esperan el texto nuevo.
- 2026-10-29: T4, parte del PR: regla 1, SC2 y changelog de `SPEC-web-bicimad.md` actualizados. Queda la prueba en el iPhone contra Render después del merge; con ella, T4 y el plan pasan a **implemented** en un PR `docs`.
