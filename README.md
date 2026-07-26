# United Minecraft

An accessibility mod for Minecraft, by NibbleNerds.

Built on the [Fabric](https://fabricmc.net/) mod loader.

## Features

- Narration is routed through [NVDA](https://www.nvaccess.org/) when it's running,
  instead of Windows SAPI, for better screen reader output. See
  [`src/client/resources/nvda/README.md`](src/client/resources/nvda/README.md) for
  how that's wired up. Falls back to Minecraft's normal narrator automatically when
  NVDA isn't running.

## Setup

For IDE setup instructions, see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up).

## License

CC0-1.0 — see [LICENSE](LICENSE).
