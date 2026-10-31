# Spec: web-design-tokens

- Created: 2026-10-31
- Status: **approved** (2026-10-31)
- Plan: [PLAN-web-design-tokens.md](../plans/PLAN-web-design-tokens.md)

Módulo `web-design-tokens` del [Capability Map](CAPABILITY-MAP.md), el primero del rediseño de la UI. Es la fase 1 del documento «Mad Transit — Especificación de UI» (revisión 32) y del lienzo «Mad Transit · rediseño», que son privados. Sustituye la paleta por defecto de Tailwind por el tema del rediseño, sin cambiar la estructura de ningún componente.

## Objective

Que toda la web saque sus colores, radios, sombras, foco y fuente de un solo tema, para que los módulos siguientes del rediseño solo compongan pantallas.

- Como usuario, veo la app con los colores, la fuente y el anillo de foco del rediseño, y todo funciona igual que antes.
- Como desarrollador, cambio un color en `web/src/app/styles/index.css` y cambia en la lista, el detalle y el mapa.
- Como desarrollador, no encuentro ningún hex ni clase de color de la paleta por defecto (`slate`, `sky`, `amber`, `red`) fuera de `index.css`.

**Fuera de alcance:** cambiar la estructura o el texto de los componentes (cabecera, tarjetas, bottom sheet de tres alturas…); el nivel `unconfirmed` en el código (llega con `web-data-state`); los tokens de bus (llegan con `web-bus`); modo oscuro.

## Decisiones (2026-10-31, del usuario)

1. **Inter.** Se añade `@fontsource-variable/inter` y se carga solo el subconjunto latin (unos 48 KB por usuario; cubre todo el español).
2. **Nivel `off`.** Las estaciones `NO_SERVICE` mantienen su token actual, `--color-availability-off` (`#4b5563`, texto blanco), para distinguirse del gris de `unconfirmed`.
3. **Marcadores.** Siguen siendo capas de MapLibre; solo cambian sus colores.
4. **El diseño manda en lo visual; el repo, en el código.** Los nombres de tokens y la tabla de migración salen del documento de diseño; la arquitectura y las reglas, de [web/AGENTS.md](../../web/AGENTS.md).

## Assumptions

1. **El tema.** El `@theme static` de `index.css` pasa a ser el bloque «index.css objetivo» del documento de diseño, sin el grupo «Bus (web-bus)» y con `--color-primary-foreground` (supuesto 3). Los nombres `availability-*` y `map-user-location` se mantienen, así que `AvailabilityBadge` y `mapColors.ts` siguen funcionando con valores nuevos. Los tokens que este módulo aún no usa (avisos, `unconfirmed`, `ease-sheet`…) entran ya, porque `static` los deja en el CSS y así el tema está completo en un solo PR.
2. **La fuente.** `@fontsource-variable/inter` no publica un CSS por subconjunto. `index.css` declara su propio `@font-face` con el fichero `inter-latin-wght-normal.woff2` del paquete, el `unicode-range` de latin y `font-display: swap`. La familia se llama `Inter Variable` (no `Inter`, como pone el documento): `--font-sans: 'Inter Variable', ui-sans-serif, system-ui, sans-serif`.
3. **Migración de clases.** Se aplica la tabla «Migración de clases actuales» del documento. Hoy hay 13 `outline-sky-700`, 11 `rounded-md`, 9 `bg-white`, 7 `rounded`, 7 `border-slate-300`, 7 `bg-slate-50` y una veintena de clases sueltas más. `bg-slate-50` pasa a `bg-canvas` o a `hover:bg-chip` según el uso, y `rounded` y `rounded-md` a `rounded-control` o `rounded-chip`. Dos casos que la tabla no recoge:
   - `text-white` sobre `bg-sky-700` (botón pulsado de `NeedToggle` y opción elegida de `SourcePicker`): el tema añade `--color-primary-foreground: #ffffff` y pasa a `text-primary-foreground`. El aspecto de la píldora del diseño (fondo `primary-soft` con anillo) llega con `web-redesign-desktop`.
   - `rounded-t-2xl` del `BottomSheet` pasa a `rounded-t-sheet`. Su transición (`duration-300 ease-out`) no cambia aquí: la curva `ease-sheet` llega con las tres alturas en `web-redesign-mobile`.
4. **`empty` sin relleno.** En la lista, `empty` pasa a fondo blanco con `border-2 border-dashed border-availability-empty-line` y número rojo. En el mapa, MapLibre no dibuja bordes discontinuos: relleno blanco, borde sólido de 2 px `availability-empty-line` y número `availability-empty-foreground`. El número y la etiqueta siguen diciendo «0», así que el color no es la única pista.
5. **Bordes de los puntos del mapa.** `points.ts` deja de declarar `#ffffff` y `#030712`. `MapColors` añade `pointStroke`, `pointSelectedStroke` y `emptyStroke`, leídos con `token()`. El punto seleccionado pasa a tener borde `primary` (`#0b5cad`).
6. **Foco.** Todo elemento interactivo usa `focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary`. Donde hoy no hay foco visible, se añade.
7. **Sin cambios de texto ni de estructura.** «Bicis / Anclajes» pasa a «Sacar bici / Dejar bici» en `web-redesign-desktop`, no aquí.

## Tech Stack

El de [web-bicimad](SPEC-web-bicimad.md#tech-stack) más `@fontsource-variable/inter` en `dependencies`, con versión exacta (hoy 5.3.0, publicada el 2026-07-19, así que cumple `minimumReleaseAge`).

## Commands

```bash
pnpm --filter web add @fontsource-variable/inter@5.3.0 --save-exact
pnpm --filter web dev:mock
pnpm --filter web test
pnpm --filter web test:coverage
pnpm --filter web depcruise
pnpm --filter web build
pnpm check:task
grep -rnE "#[0-9a-fA-F]{3,8}\b" web/src --include=*.ts --include=*.tsx | grep -v "\.test\."   # vacío
grep -rnE "\b(bg|text|border|outline|divide|ring|from|to|fill|stroke)-(slate|sky|amber|red|gray)-[0-9]+" web/src   # vacío
```

Manual: `dev:mock` en escritorio y en móvil (DevTools), recorrer lista, detalle, buscador y mapa con Tab y con el ratón.

## Project Structure

```
web/
  package.json                          + @fontsource-variable/inter (exacta)
  src/app/styles/index.css              @font-face latin + @theme static completo
  src/shared/ui/base-map/
    mapColors.ts                        + pointStroke, pointSelectedStroke, emptyStroke
    points.ts                           sin hex; bordes desde MapColors; empty con borde
  src/entities/station/ui/AvailabilityBadge.tsx   empty con borde discontinuo
  src/**/*.tsx                          clases migradas según la tabla
```

## Code Style

El de [web-bicimad](SPEC-web-bicimad.md#code-style). Las clases se escriben enteras para que Tailwind las encuentre:

```tsx
// Full class names (not built from `level`) so Tailwind finds them in the source.
const TONES = {
  good: 'bg-availability-good text-availability-good-foreground',
  low: 'bg-availability-low text-availability-low-foreground',
  empty:
    'bg-availability-empty text-availability-empty-foreground border-2 border-dashed border-availability-empty-line',
  off: 'bg-availability-off text-availability-off-foreground',
} as const satisfies Record<AvailabilityLevel, string>;
```

- Ningún hex fuera de `index.css` (los tests pueden comparar valores).
- MapLibre no lee variables CSS: todo color del mapa pasa por `readMapColors()`.

## Testing Strategy

- Vitest y Testing Library, como hoy. Los tests que comparan clases se actualizan a los tokens nuevos sin quitar aserciones.
- `mapColors`: devuelve `pointStroke`, `pointSelectedStroke` y `emptyStroke` desde las variables CSS.
- `points`: los bordes salen de `MapColors`; `empty` tiene borde y el seleccionado usa `pointSelectedStroke`.
- `AvailabilityBadge`: `empty` lleva borde discontinuo y sigue mostrando el número y el texto para lectores de pantalla.
- Cobertura ≥ 80 % por capa; `pnpm --filter web depcruise` sin violaciones.
- Los tests de accesibilidad con `vitest-axe` empiezan en `web-redesign-desktop`, junto con los componentes nuevos.

## Boundaries

- **Siempre:** clases de tokens; color nunca solo; foco visible en todo elemento interactivo.
- **Preguntar antes:** cualquier dependencia más allá de `@fontsource-variable/inter`; cambiar texto, estructura o comportamiento.
- **Nunca:** hex fuera de `index.css`; clases de la paleta por defecto para color; `outline: none` sin sustituto; cargar fuentes de Google Fonts.

## Success Criteria

1. SC1: `index.css` contiene el tema completo del documento de diseño (sin el grupo de bus) y la fuente Inter latin.
2. SC2: los dos `grep` de Commands no devuelven nada.
3. SC3: el mapa pinta los puntos con los colores nuevos, `empty` en blanco con borde rojo y el seleccionado con borde `#0b5cad`.
4. SC4: Tab muestra el anillo de foco `primary` en botones, enlaces y campos de lista, detalle, buscador y mapa.
5. SC5: en `web/dist`, el CSS solo referencia el `.woff2` latin de Inter.
6. SC6: ninguna funcionalidad cambia: los tests existentes pasan sin quitar aserciones.
7. SC7: `pnpm check:task` pasa.

## Open Questions

Ninguna.

## Changelog

- 2026-10-31: borrador a partir del documento de diseño (revisión 32) y del lienzo, con las decisiones 1 a 4 del usuario.
- 2026-10-31: revisión tras los cambios del lienzo (leyenda con «Fuera de servicio» y «Sin confirmar»; límite de consultas sin hora fija). No cambian el tema: la leyenda usa tokens que ya están y los textos son de `web-redesign-desktop` y `web-bus`. Se añaden al supuesto 3 dos clases que la tabla de migración no recogía (`text-white` y `rounded-t-2xl`) y el token `--color-primary-foreground`.
- 2026-10-31: aprobada por el usuario.
