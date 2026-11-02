# server-logs-web

Replaces the Control Panel &rarr; Security &rarr; **Server Logs** experience with
one that can browse **every** logging folder under `${liferay.home}/logs`, not
just the per company directories.

The dropdown is populated at runtime, so a new logging folder (`multinational`,
`common`, ...) shows up on its own without a code change.

## What you get

| Dropdown entry | Directory | Who can see it |
| --- | --- | --- |
| `Liferay (<webId>)` | `logs/companies/<companyId>` | Omniadmins see every company, company admins see their own |
| `Liferay (Server)` | `logs` (files not in a sub folder) | Omniadmins |
| `ModuleA`, and any other sub folder | `logs/<folder>` | Omniadmins |

`companies` is never offered as a folder entry — it is already represented by
the per company rows above it.

Each entry lists its files newest first, with size and modification time, a
`Newest` badge on the most recent one, an in-browser preview and a download.

## Independence

The module has no dependency on on any other module in
this workspace. Its only compile dependency is `release.dxp.api`.

## Upgrade safety

This was the main design constraint. The module deliberately avoids everything
that normally breaks on a Liferay upgrade:

- **No JSP fragment** over `com.liferay.portal.company.log.web`. A
  `Fragment-Host` bundle pins you to that module's version and silently stops
  attaching when Liferay bumps it.
- **No React / npm bundler / `@liferay/*` packages.** The UI is a JSP plus
  `js/server_logs.js`, plain browser JavaScript that needs no frontend build.
  Styling uses Clay CSS classes the theme already ships.
- **Public kernel API only** — `Log4JUtil`, `CompanyLocalService`, `Portal`,
  `PermissionCheckerFactory`, `Language`, `MimeTypes`, `ServletResponseUtil`,
  `JSONFactory`, `BasePanelApp`. Nothing from a Liferay `internal` package.
- **The logs directory is derived, not hardcoded.** It is taken from
  `Log4JUtil.getCompanyLogDirectory(defaultCompanyId)` and walked up two levels,
  so a custom Log4j layout is picked up automatically. If that fails it falls
  back to `${liferay.home}/logs`, and `logsDirectory` in System Settings
  overrides both.

### After a Liferay upgrade, check

1. The module still resolves (`Bundle-SymbolicName com.liferay.server.logs.web`
   in Gogo `lb`). If a kernel package range moved, rebuild with the new
   `liferay.workspace.product`.
2. The Control Panel still shows exactly one **Server Logs** entry — see the
   next section.

## Hiding Liferay's own Control Panel entry

Both this module and `com.liferay.portal.company.log.web` register a panel app
in `control_panel.security` at order 700, so by default you would see two
**Server Logs** entries. Rather than uninstalling or fragmenting Liferay's
module, a single OSGi component is disabled — its portlet and its
`/o/company-log` servlet stay installed and fully functional.

File: `${liferay.home}/osgi/configs/com.liferay.portal.component.blacklist.internal.configuration.ComponentBlacklistConfiguration.config`

Add this entry to the existing array (do not replace entries that are already
there):

```
com.liferay.portal.company.log.web.internal.application.list.PortalCompanyLogPanelApp
```

So the file reads, for example:

```
blacklistComponentNames=["<existing entries...>","com.liferay.portal.company.log.web.internal.application.list.PortalCompanyLogPanelApp"]
```

The change applies immediately, no restart needed.

**To revert**, delete that one string from the array. Liferay's entry comes
back.

**Upgrade behaviour**: if a future Liferay release renames that component, the
blacklist entry simply matches nothing. You get both entries back — visible and
harmless — rather than a broken page. Re-check the component name at that point.

## Endpoints

All read only, all `GET`, all served at `/o/server-logs`.

| Request | Returns |
| --- | --- |
| `/o/server-logs` | `{"sources":[{"key","label","type"}]}` |
| `?sourceKey=folder:ModuleA` | `{"sourceKey","label","files":[{"fileName","fileSize","lastModified"}]}` |
| `?sourceKey=...&fileName=...` | `{"fileName","fileSize","previewSize","truncated","content"}` |
| `?sourceKey=...&fileName=...&action=download` | the file as an `attachment` |

Security:

- Unauthenticated requests get `MustBeAuthenticated`.
- Company sources require company admin on that company; only omniadmins see
  other companies.
- Server wide sources (root and folders) require omniadmin by default — they are
  not scoped to a single company. Relax with `serverSourcesOmniadminOnly` if you
  must.
- `sourceKey` is matched against the list the caller is allowed to see, so an
  excluded or unauthorized folder cannot be reached by guessing a key.
- `fileName` rejects separators and `..`, and the resolved path must still sit
  inside the source directory.

## Configuration

System Settings &rarr; Infrastructure &rarr; **Server Logs**.

| Key | Default | Purpose |
| --- | --- | --- |
| `logsDirectory` | *(blank)* | Absolute path to scan. Blank = derive it, see above. |
| `excludedFolderNames` | `companies` | Folders never offered in the dropdown. |
| `folderLabelOverrides` | *(empty)* | `folderName=Label` pairs, e.g. `ModuleA`. |
| `companyLogsEnabled` | `true` | List the per company directories. |
| `rootLogsEnabled` | `true` | List files sitting directly in `logs`. |
| `serverSourcesOmniadminOnly` | `true` | Restrict root and folder sources to omniadmins. |
| `maxPreviewSize` | `1048576` | Bytes read from the **end** of a file for the preview. |

`maxPreviewSize` is why previewing a 13 MB log is instant here: only the tail is
sent, with a banner pointing at Download for the full file. Liferay's own
viewer reads the whole file into a single `String`.

## Build and deploy

```
./gradlew :modules:server-logs-web:jar
cp modules/server-logs-web/build/libs/com.liferay.server.logs.web-1.0.0.jar $LIFERAY_HOME/deploy/
```
