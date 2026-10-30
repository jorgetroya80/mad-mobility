# @jorgetroya80/mad-transit-client

Typed client for the Mad Transit public API, generated with [hey-api](https://heyapi.dev) from the API's OpenAPI contract. Each `api` release publishes a client with the same version; the contract ships in the package as `openapi.json`.

## Install

The package is on GitHub Packages, which asks for a token even for public packages (classic PAT with `read:packages`):

```bash
# .npmrc
@jorgetroya80:registry=https://npm.pkg.github.com
//npm.pkg.github.com/:_authToken=${NODE_AUTH_TOKEN}
```

```bash
pnpm add @jorgetroya80/mad-transit-client
```

## Usage

```ts
import { createClient, createConfig, getStation, listStations } from '@jorgetroya80/mad-transit-client';

const client = createClient(createConfig({ baseUrl: 'https://api.example.com' }));

const nearby = await listStations({
  client,
  query: { near: '40.4168,-3.7038', radius: 500, need: 'bikes' },
});
if (nearby.error) throw new Error(`Request failed with ${nearby.response.status}`);

const station = await getStation({ client, path: { number: '25A' } });
```

Each operation in the contract has one function, named after its `operationId`. The package also exports every schema type (`StationsResponse`, `Problem`, ...) and `Station` as a shorter name for `StationResponse`.

## License

[MIT](LICENSE)
