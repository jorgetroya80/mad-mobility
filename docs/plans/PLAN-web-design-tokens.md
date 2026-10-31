# Plan: web-design-tokens

- Created: 2026-10-31
- Status: **approved** (2026-10-31)
- Spec: [SPEC-web-design-tokens.md](../specs/SPEC-web-design-tokens.md)

## Overview

Sustituir la paleta por defecto de Tailwind por el tema del rediseño: el `@theme static` completo en `index.css`, la fuente Inter (solo latin), los colores del mapa leídos desde los tokens y unas 80 clases migradas en 25 archivos de `web/src`. No cambia ningún texto, estructura ni comportamiento.

## Decisiones de implementación

- **Rama `feat/web-design-tokens`, un PR y un commit por tarea.** Sin `Co-Authored-By`. El PR toca `web/` y `docs/`. Título `feat(web): apply the Mad Transit design theme`: el cambio es visible para el usuario, así que publica una release de la web.
- **Primero lo que puede fallar (T1).** Cargar el `.woff2` latin desde `index.css` depende de cómo Vite resuelve un paquete dentro de `url()`. Si no lo resuelve, se usa la ruta relativa a `node_modules` o un `import … ?url` en `app/main.tsx`; se decide en T1 y se apunta aquí.
- **El mapa antes que las clases (T2).** Es la única pieza con lógica nueva (el borde de `empty`); el resto es sustitución mecánica.
- **La migración, por capa FSD.** Cada tarea deja la app funcionando con una mezcla de clases viejas y tokens; el `grep` de SC2 solo tiene que salir vacío al final (T7).
- **Tests que comparan clases o colores.** Se actualizan al token nuevo en la misma tarea, sin quitar aserciones. Hoy solo `mapColors.test.ts` compara valores; los demás buscan por rol.
- **Foco.** Cada tarea revisa los elementos interactivos de sus archivos y deja `focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary` en todos (supuesto 6).

## Grafo de dependencias

```
T1 tema + Inter
   │
   ├─► T2 mapa (mapColors, points)
   │
   └─► T3 entities + features pequeñas ─► T4 search-by-number ─► T5 shared/ui ─► T6 station-detail ─► T7 station-list + cierre
```

T2 a T6 solo dependen de T1 y no comparten archivos; van en serie para tener un commit revisable por tarea.

## Task List

### Fase 1: Base

- [x] **T1: Tema e Inter** (S, ~45 min)
  - Descripción: instalar `@fontsource-variable/inter@5.3.0` exacta. Sustituir el `@theme static` de `index.css` por el bloque objetivo del diseño (sin bus) más `--color-primary-foreground`. Añadir el `@font-face` de `Inter Variable` con el fichero `inter-latin-wght-normal.woff2`, el `unicode-range` de latin y `font-display: swap`.
  - Aceptación:
    - `pnpm --filter web build` en verde y el CSS de `web/dist` solo referencia el `.woff2` latin de Inter (SC5).
    - Los tests existentes pasan sin cambios: los nombres de los tokens que ya existían se mantienen.
    - En `dev:mock` el texto se ve en Inter (DevTools › Computed › Rendered fonts).
  - Verificación: `pnpm --filter web build && ls web/dist/assets | grep -i inter`; `pnpm --filter web test`.
  - Archivos: `web/package.json`, `pnpm-lock.yaml`, `web/src/app/styles/index.css`.
  - Dependencias: ninguna.

- [ ] **T2: Colores del mapa desde los tokens** (S, ~1 h)
  - Descripción: `MapColors` añade `pointStroke`, `pointSelectedStroke` y `emptyStroke`, leídos con `token()`. `points.ts` quita `POINT_STROKE_COLOR` y `SELECTED_POINT_STROKE_COLOR`. El borde del círculo es `pointSelectedStroke` si está seleccionado, `emptyStroke` (2 px) si el tono es `empty` y `pointStroke` en otro caso. El punto de ubicación usa `pointStroke`.
  - Aceptación:
    - `readMapColors()` devuelve los tres colores nuevos (test con las variables CSS).
    - `pointLayers()`: el borde de `empty` sale de `emptyStroke` y el del seleccionado de `pointSelectedStroke` (test sobre las expresiones).
    - `points.ts` y `mapColors.ts` no contienen ningún hex.
  - Verificación: `pnpm --filter web exec vitest run src/shared/ui/base-map`.
  - Archivos: `shared/ui/base-map/{mapColors.ts,mapColors.test.ts,points.ts,points.test.ts}`.
  - Dependencias: T1.

### Checkpoint: Fase 1

- [ ] `pnpm check:task` en verde.
- [ ] `dev:mock`: el mapa pinta los colores nuevos, `empty` en blanco con borde rojo y el seleccionado con borde azul (SC3).

### Fase 2: Migración de clases

- [ ] **T3: `entities/station` y features pequeñas** (M, ~1 h)
  - Descripción: `AvailabilityBadge` con `empty` en blanco y `border-2 border-dashed border-availability-empty-line`. Migrar `StationRow`, `NeedToggle` (`aria-pressed:bg-primary aria-pressed:text-primary-foreground`), `FavoriteButton` y `LocateButton` según la tabla.
  - Aceptación:
    - El test de `AvailabilityBadge` comprueba el borde discontinuo de `empty` y sigue encontrando el texto para lectores de pantalla.
    - Ninguno de los cinco archivos contiene clases `slate`, `sky`, `amber`, `red`, `bg-white`, `text-white`, `rounded` ni `rounded-md`.
  - Verificación: `pnpm --filter web exec vitest run src/entities src/features/filter-by-need src/features/toggle-favorite src/features/locate-user`.
  - Archivos: `entities/station/ui/{AvailabilityBadge.tsx,AvailabilityBadge.test.tsx,StationRow.tsx}`, `features/filter-by-need/ui/NeedToggle.tsx`, `features/toggle-favorite/ui/FavoriteButton.tsx`, `features/locate-user/ui/LocateButton.tsx`.
  - Dependencias: T1.

- [ ] **T4: Buscador por número** (S, ~45 min)
  - Descripción: migrar `SearchInput` (borde `border-input-line`), `ResultList`, `SearchByNumber` y `SourcePicker` (`peer-checked:bg-primary peer-checked:text-primary-foreground`).
  - Aceptación:
    - Los cuatro archivos sin clases de la paleta por defecto.
    - El foco del campo y de cada resultado muestra `outline-primary`.
  - Verificación: `pnpm --filter web exec vitest run src/features/search-by-number`.
  - Archivos: `features/search-by-number/ui/{SearchInput,ResultList,SearchByNumber,SourcePicker}.tsx`.
  - Dependencias: T1.

- [ ] **T5: `shared/ui`** (M, ~1 h)
  - Descripción: migrar `PanelNotice` (`bg-notice-stale`, `border-notice-stale-line`, `text-notice-stale-icon`), `StateMessage` (error con `text-notice-error-foreground`; carga con `primary`), `UpdatedAt`, `SidePanel`, `DataAttribution` y `BottomSheet` (`rounded-t-sheet`, `shadow-sheet`, `bg-surface`, asa `bg-handle`; su transición no cambia).
  - Aceptación:
    - Los seis archivos sin clases de la paleta por defecto ni sombras escritas a mano.
    - Los tests de `shared/ui` pasan sin quitar aserciones.
  - Verificación: `pnpm --filter web exec vitest run src/shared/ui`.
  - Archivos: `shared/ui/{panel-notice/PanelNotice,state-message/StateMessage,data-freshness/UpdatedAt,side-panel/SidePanel,data-attribution/DataAttribution,bottom-sheet/BottomSheet}.tsx`.
  - Dependencias: T1.

### Checkpoint: Fase 2

- [ ] `pnpm check:task` en verde.
- [ ] `dev:mock`: buscador, filtro, favoritos, avisos y bottom sheet con los colores nuevos; Tab muestra el anillo `primary`. Revisión humana antes de la fase 3.

### Fase 3: Widgets y cierre

- [ ] **T6: Detalle de estación** (M, ~45 min)
  - Descripción: migrar `BackButton`, `DetailField`, `DetailTitle`, `MessageAction` y `StationFields`.
  - Aceptación:
    - Los cinco archivos sin clases de la paleta por defecto.
    - Los tests de `station-detail` pasan sin quitar aserciones.
  - Verificación: `pnpm --filter web exec vitest run src/widgets/station-detail`.
  - Archivos: `widgets/station-detail/ui/{BackButton,DetailField,DetailTitle,MessageAction,StationFields}.tsx`.
  - Dependencias: T1.

- [ ] **T7: Lista de estaciones y cierre** (S, ~45 min)
  - Descripción: migrar `NearbyStations`, `StationRows` y `StationSection` (`divide-line`). Ejecutar los dos `grep` de la spec. Spec y capability map a **implemented** después del merge; changelog de este plan.
  - Aceptación:
    - SC2: los dos `grep` de la spec no devuelven nada.
    - SC6 y SC7: `pnpm check:task` en verde sin aserciones quitadas (lo comprueba el floor guard).
  - Verificación: `pnpm check:task`; los dos `grep` de Commands en la spec.
  - Archivos: `widgets/station-list/ui/{NearbyStations,StationRows,StationSection}.tsx`, este plan.
  - Dependencias: T2-T6.

### Checkpoint final

- [ ] SC1-SC7 comprobados; CI en verde.
- [ ] Recorrido manual con Tab en escritorio y en móvil (DevTools) en `dev:mock` (SC4).
- [ ] Pedir permiso antes de push, abrir el PR y hacer merge.
- [ ] Después del despliegue: comprobar la fuente y los colores en Render, y marcar spec y capability map como **implemented**.

## Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| Vite no resuelve `@fontsource-variable/inter/files/…` dentro de `url()` en `index.css` | Medio | Va en T1, la primera tarea; alternativas: ruta relativa o `import … ?url` en `main.tsx` |
| Un color nuevo baja de 4,5:1 en un sitio que el diseño no dibujó (por ejemplo, `fg-muted` sobre `chip`) | Medio | Los pares del tema cumplen AA; revisar con DevTools › Contrast en el checkpoint de la fase 2 |
| `empty` en blanco se confunde con el fondo del mapa | Bajo | Borde rojo de 2 px y número rojo; comprobarlo en el checkpoint de la fase 1 |
| La cobertura de líneas cambiadas baja por cambios de clase sin test | Bajo | Las clases están en componentes ya cubiertos; el cambio de línea cuenta como cubierto si el componente se renderiza en un test |

## Paralelización

T2 a T6 dependen solo de T1 y no comparten archivos, así que podrían ir en paralelo. Se hacen en serie para que cada commit sea pequeño y revisable.

## Estimación

Unas 6 h en total. Fase 1 ~1 h 45 min, fase 2 ~2 h 45 min y fase 3 ~1 h 30 min.

## Changelog

- 2026-10-31: borrador a partir de la spec aprobada.
- 2026-10-31: aprobado por el usuario.
- 2026-10-31: T1 hecha. Vite resuelve la ruta del paquete dentro de `url()` en `index.css`, así que no hacen falta las alternativas. `web/dist` solo lleva `inter-latin-wght-normal.woff2` (48 KB). `check:task` en verde, 368 tests sin cambios.
