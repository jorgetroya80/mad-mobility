// Layer and boundary rules of docs/specs/SPEC-web-shell.md and SPEC-web-bicimad.md (Feature-Sliced
// Design): a layer only imports from the layers below it, and a slice never imports a sibling slice.
const LAYERS = ['shared', 'entities', 'features', 'widgets', 'pages', 'app'];

const upwardImports = LAYERS.slice(0, -1).map((layer, index) => ({
  name: `no-upward-import-from-${layer}`,
  comment: `${layer} only imports from the layers below it.`,
  severity: 'error',
  from: { path: `^src/${layer}/` },
  to: { path: `^src/(${LAYERS.slice(index + 1).join('|')})/` },
}));

const siblingSlices = ['entities', 'features', 'widgets', 'pages'].map((layer) => ({
  name: `no-sibling-slice-in-${layer}`,
  comment: `A ${layer} slice does not import another ${layer} slice.`,
  severity: 'error',
  from: { path: `^src/${layer}/([^/]+)/` },
  to: { path: `^src/${layer}/([^/]+)/`, pathNot: `^src/${layer}/$1/` },
}));

/** @type {import('dependency-cruiser').IConfiguration} */
module.exports = {
  forbidden: [
    ...upwardImports,
    ...siblingSlices,
    {
      name: 'maplibre-only-in-base-map',
      comment: 'maplibre-gl stays behind shared/ui/base-map (SPEC-web-bicimad, Never).',
      severity: 'error',
      from: { path: '^src/', pathNot: '^src/shared/ui/base-map/' },
      to: { path: 'node_modules/maplibre-gl/' },
    },
    {
      name: 'search-by-number-knows-no-stations',
      comment:
        'features/search-by-number only knows SearchItem and SearchSource (SPEC-web-bicimad, Never).',
      severity: 'error',
      from: { path: '^src/features/search-by-number/' },
      to: { path: '^src/entities/' },
    },
    {
      name: 'no-circular',
      comment: 'No runtime cycles. A cycle closed only by `import type` vanishes at build time.',
      severity: 'error',
      from: {},
      to: { circular: true, viaOnly: { dependencyTypesNot: ['type-only'] } },
    },
  ],
  options: {
    doNotFollow: { path: 'node_modules' },
    tsConfig: { fileName: 'tsconfig.json' },
    tsPreCompilationDeps: true,
    enhancedResolveOptions: {
      exportsFields: ['exports'],
      conditionNames: ['import', 'require', 'node', 'default'],
    },
  },
};
