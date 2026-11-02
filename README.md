# Liferay DXP Tools Workspace

Liferay Workspace holding reusable, self-contained admin tooling modules for
Liferay DXP. Each module here is independent — no shared code between them and
no dependency on any product codebase.

Target release: `dxp-2026.q3.0` (`liferay.workspace.product` in
`gradle.properties`).

## Modules

| Module | What it does |
| --- | --- |
| [`server-logs-web`](modules/server-logs-web) | Control Panel &rarr; Security &rarr; Server Logs, extended to browse every logging folder under `${liferay.home}/logs` — per company, root level and any sub folder such as `copilot` — through a source dropdown, with preview and download. |

## Build

```
./gradlew :modules:server-logs-web:jar
```

Deploy to the bundle configured by `liferay.workspace.home.dir`:

```
./gradlew :modules:server-logs-web:deploy
```
Change that one line to target a different bundle.
