# Object External Database Storage

## Status

**Design and module scaffold only. No external database storage type is
registered by this module yet.** The configuration and workflows below are a
proposed contract, not instructions for an operational connector. Do not deploy
this module or use it for production data in its current state.

The target is Liferay DXP **2026.Q4.0**, installation
`E:\Project\Liferay-dxp-Q4-2026\liferay-dxp`. The inspected bundle identifies
itself with Git hash `8910063ab71ef01b28f53ffb6eacc8d0d7bc0a41`. The
workspace currently targets DXP 2026.Q3.0 and a different Liferay home; the
module must be built and verified against Q4 without changing the existing
workspace target for unrelated modules.

The scaffold can be built for Q4 without deploying it:

```powershell
.\gradlew.bat :modules:object-external-db-storage:jar `
  '-Pliferay.workspace.product=dxp-2026.q4.0' `
  '-Pliferay.workspace.home.dir=E:\Project\Liferay-dxp-Q4-2026\liferay-dxp' `
  '-Papp.server.tomcat.dir=E:\Project\Liferay-dxp-Q4-2026\liferay-dxp\tomcat' `
  --no-daemon --console=plain
```

The explicit Tomcat directory is needed for this workspace plugin and the
unversioned Tomcat directory in the Q4 bundle. Dependency resolution may
require access to the configured Liferay artifact repository. Building an
empty scaffold does not verify connector behavior or database compatibility.

This module is intended to add **external SQL database storage for proxy
Objects**, alongside Liferay's default storage and its external-system
integrations such as Salesforce and SugarCRM. It does not replace those
integrations. Multiple Object definitions could use the same configured
database alias, while different definitions could select different aliases.

## Liferay storage types and the proposed addition

| Storage type | Entry storage | Availability |
| --- | --- | --- |
| Default | The portal database | Existing Liferay behavior; remains the default. |
| Salesforce | Salesforce through its integration | Bundled in the inspected Q4 installation; configuration and availability depend on the instance. |
| SugarCRM | SugarCRM through its integration | Bundled in the inspected Q4 installation; configuration and availability depend on the instance. |
| Function-based manager | An external service invoked by Liferay | Existing extension mechanism; a configured service implements storage. |
| External database alias | A configured external SQL database | **Proposed by this module; not implemented.** |

Q4's Object creation UI has a storage selector gated by feature flag
`LPS-135430`. Its values come from registered `ObjectEntryManager` services,
not directly from JDBC properties. A database alias would need a registered
manager with a stable storage type and label before it could appear there.
Visibility also depends on the active feature flag, OSGi services and instance
configuration. Do not assume a bundled integration is active merely because
its JAR is present.

## Boundaries and confirmed proxy restrictions

The proposed connector is a **non-default storage type**, similar in
architecture to the Salesforce Object entry manager. It does not give the
Object full native persistence behavior.

- Object definitions, fields, configuration and related Liferay metadata stay
  in the portal database. The Q4 Object publishing code also creates
  Liferay-managed dynamic tables through its Object-definition persistence
  data source; this design does not eliminate those portal tables.
- Q4 validation allows categorization, comments, friendly-URL customization
  and Object entry history only with the default storage type. Liferay's
  Encrypted Object field type is also restricted to default storage.
- Several Q4 entry endpoints require `DefaultObjectEntryManager`, including
  numeric-ID entry operations, version-specific operations, subscription and
  expiration endpoints, and certain folder, validation and action endpoints.
  An ordinary custom `ObjectEntryManager` does not provide these operations.
- At least one Object relationship selection flow filters out non-default
  storage definitions. Relationships, workflow, Object actions, attachments,
  search, import/export, staging and other platform integrations must be
  verified individually; this document does not promise parity.
- The standard external-reference-code CRUD and list methods are extension
  points. Whether they work for an external database depends on the custom
  manager implementing and validating them.
- Placing entry values in an external database is not complete data isolation.
  Depending on enabled integrations, values or related information can also
  appear in search indexes, attachments, logs, caches, audit records, exports,
  backups or external systems.

These observations come from the installed Q4 bundle's storage manager
interface, selector source, Object-definition and Object-field validators, and
entry REST routing. They are not an exhaustive compatibility certification.

| Feature | Default Object | Salesforce or proposed SQL proxy Object |
| --- | --- | --- |
| Object definition and metadata in portal DB | Yes | Yes |
| Entry values in selected external system | No | Salesforce: integration-supported; SQL: proposed |
| Liferay-managed Object tables in portal DB | Yes | Q4 publishing still creates Liferay-managed tables |
| Categorization and comments | Available when configured | Rejected for non-default storage |
| Entry history | Available when configured | Rejected for non-default storage |
| Friendly-URL customization | Available when configured | Rejected for non-default storage |
| Liferay Encrypted field | Available when enabled and configured | Rejected for non-default storage |
| Basic CRUD by external reference code | Supported | Delegated to the registered manager; SQL implementation pending |
| Numeric-ID CRUD | Supported | Ordinary proxy manager does not satisfy the default-manager requirement |
| Relationships, workflow and platform integrations | Feature-specific | Do not assume parity; verify each supported operation |

The Q4 `ObjectEntryManager` interface also receives aggregation, filter,
search, sort and pagination parameters. Their presence in the interface is
not proof that every external provider implements all combinations.

## Proposed architecture

```text
Object definition -> selected stable storage type
                  -> registered database entry manager
                  -> configured database alias and managed connection pool
                  -> Object entry tables in the external database

Default Object definition -> existing Liferay manager -> portal database
```

The connector should maintain one explicitly registered storage type per
enabled database alias. Object definitions using the same alias form a
database group by configuration; an Object folder does not automatically
enforce that all its Objects use the same alias. Storage identity must be
stable across restarts and nodes, and selection should not be changed after
publishing without an explicit migration.

The requested physical layout is **native-style relational storage**: custom
Object fields are stored in corresponding database columns, not serialized
together into a single JSON or text column. The design must account for
Liferay's main, extension and, where applicable, localization table structures
and field types. Exact table names, indexes, key semantics, system columns
and SQL types must be mapped and verified against Q4 and the target database;
the proxy connector must not claim byte-for-byte native schema parity until
that work is complete.

The registry must expose only administrator-approved aliases, not every
arbitrary JDBC property. Requests must fail if an alias is missing,
misconfigured, unauthorized or unavailable. They must never silently use the
portal database as a fallback.

### Proposed properties

The following is an **illustrative module-specific namespace**, not a
pre-existing Liferay feature and not yet implemented:

```properties
object.external.db.aliases=finance,hr
object.external.db.finance.label=Finance Database
object.external.db.finance.jdbc.driverClassName=<installed-driver-class>
object.external.db.finance.jdbc.url=<external-jdbc-url>
object.external.db.finance.jdbc.username=<database-user>
object.external.db.finance.jdbc.password=<resolved-secret>
object.external.db.hr.label=HR Database
object.external.db.hr.jdbc.driverClassName=<installed-driver-class>
object.external.db.hr.jdbc.url=<external-jdbc-url>
object.external.db.hr.jdbc.username=<database-user>
object.external.db.hr.jdbc.password=<resolved-secret>
```

Once implemented, the module may read these properties through Liferay's
effective portal property configuration, which can include `portal-ext`
and `portal-setup-wizard` properties. The standard setup wizard does not
automatically create this custom alias list. Operators should use protected
configuration, a secret manager or suitable JNDI data sources instead of
committing passwords. Do not expose connection details or secrets in the
storage selector, Object definitions, logs or API responses.

Alias validation, property names, pooling, driver visibility, secret
resolution, changes across cluster nodes and lifecycle handling remain
implementation decisions until the connector is built and reviewed. Existing
`jdbc.default.*` must not be interpreted as a selectable external alias.

## Database compatibility

The requested scope is **all database engines supported by the target Liferay
release**. This is a target, not a claim that the scaffold supports any
engine. Compatibility must be established separately for the exact Q4
support matrix, database versions, JDBC drivers and deployed environment.
Liferay's internal database-type list is not by itself a product support
matrix; development-only databases must not be advertised as production
supported merely because a driver exists.

Each supported engine needs verified SQL generation, identifier quoting and
length limits, value and null mappings, Unicode and large text handling,
pagination, sorting, indexes, schema inspection, DDL behavior, transactions
and error translation. Using a generic JDBC connection alone does not ensure
portable SQL. The connector must detect unsupported database products or
versions and fail explicitly rather than executing an assumed dialect.

The module must not download JDBC drivers automatically. An administrator
must provision the correct supported driver or data source.

The Liferay database-type enumeration in upstream source includes DB2,
Hypersonic, MariaDB, MySQL, Oracle, PostgreSQL and SQL Server. This is a
useful engineering inventory, **not** a statement that all of them are
supported production targets for DXP 2026.Q4.0, or that this module supports
any of them. Confirm the licensed Q4 support matrix and exact version and
driver combinations before implementing the final support matrix.

## Object and schema lifecycle requirements

`ObjectEntryManager` exposes entry CRUD and list operations, not a complete
schema-lifecycle API. Table creation and schema changes therefore require a
separate, version-specific integration design. At minimum it must cover:

1. Validate the selected alias and supported field definitions before
   publication; create the external schema reliably and idempotently.
2. Detect and coordinate field addition, change and deletion, indexes and
   any supported localized or relationship data. Avoid destructive changes
   without an explicit policy and recovery plan.
3. Keep a durable definition-to-database/table mapping and schema version.
   Handle concurrent publishing on multiple nodes, partial DDL, retries,
   failed portal transactions, restart and reconciliation.
4. On an explicit Liferay Object deletion, automatically delete that
   Object's connector-owned external tables and their data. This is an
   **irreversible, destructive operation**; operators must arrange backups
   before deletion. Unpublishing, disabling the module or temporarily losing
   an alias must not by itself delete external data.
5. Treat switching an existing Object to a different database as a migration,
   not a property update. Provide an auditable copy, verification, cutover
   and rollback procedure before allowing it.

For a future user workflow, the administrator configures and approves an
alias first. The Object creator then selects that alias as the storage type,
adds supported fields and publishes the definition. Publication must not be
reported as successful unless the implementation's documented provisioning
and recovery requirements are met. On subsequent entry operations the manager
resolves the stored alias, not a database URL supplied by the request.

The portal database and external database do not automatically share an
atomic transaction. DDL may also commit independently, depending on the
database. The implementation must document its consistency guarantees and
make partial failures visible; a simple service wrapper or model listener
must not be treated as proof of atomicity.

Automatic deletion requires a durable, verified ownership mapping from the
Object definition to its tables and database. It must not accept a table name
from an API request or drop a shared, native Liferay or unrelated table.
Deletion must enforce the Liferay Object deletion permission and remain
idempotent under retries. A missing database or an uncertain mapping must
produce a visible failure and a recoverable cleanup state, not silent success.
The eventual implementation must define and test the order of portal and
external deletion, crash recovery and reconciliation; it must not claim an
atomic rollback across the two databases.

## Entry operation requirements

- Implement create, list, read, update and delete through the Q4
  `ObjectEntryManager` contract. Respect the definition, company and site
  scope, external reference code, and Liferay permission model on every path.
- Use parameterized values and validated, generated identifiers. Enforce
  uniqueness and concurrency in the database, and avoid returning success
  after a failed write.
- Support only field types and Object behaviors that have been implemented
  and validated. Reject unsupported fields, filters, sorts, aggregations or
  operations explicitly rather than ignoring them or returning partial data.
- Define stable filtering, sorting, pagination and search semantics, including
  count accuracy and access control. Authorization must be applied before
  pagination and counts.
- Avoid exposing credentials, raw SQL values, or sensitive entry contents in
  error messages or telemetry.
- Do not assume a single database transaction can cover portal metadata,
  external entry changes and external side effects. Document retry and
  idempotency behavior.

## Security and operations

Use narrowly scoped database identities, TLS where available, separate
credentials and connection pools per alias, and explicit access control for
who may create Objects with each storage type. Validate tenant and site
boundaries independently of database selection. Avoid providing a generic
SQL endpoint or allowing arbitrary client-supplied JDBC URLs, table names or
SQL identifiers.

Plan external-database monitoring, pool limits, credential rotation,
backups and restores coordinated with the portal database, disaster recovery,
data retention and deletion, audits, and cluster configuration consistency.
A storage alias must remain bound to the same intended database; silently
repointing it could expose or lose data.

## Development and acceptance

Implementation must preserve default Object behavior and the Salesforce,
SugarCRM and function-based integrations. Build and automated tests should
target the actual Q4 APIs without changing unrelated modules' Q3 target.
Offline unit and contract tests should cover configuration, alias security,
tenant isolation, schema planning, dialect generation, CRUD, filtering,
concurrency and explicit failures. They are not proof that a database driver
or a particular server version is compatible.

Before any production claim, separately approve and perform integration tests
against each supported database and a Q4 Liferay instance, including creation,
field changes, external data placement, failure recovery, permissions and
restore. Deployment and live validation are deliberately deferred until
explicitly requested.

## Evidence and remaining decisions

The Q4 installation contains `com.liferay.object.rest.api.jar`,
`com.liferay.object.rest.impl.jar`, `com.liferay.object.service.jar`,
`com.liferay.object.storage.salesforce.jar` and
`com.liferay.object.storage.sugarcrm.jar` under `osgi\portal`. Its
`ObjectEntryManager` interface defines entry operations and storage labels;
the registered default, Salesforce, SugarCRM and function-based components
are separate implementations. The Object-definition service's table creation
calls use its persistence data source, and native Object entry code obtains
connections from Object-entry persistence.

The Q4 source commit recorded by the local bundle was not available at the
public source URL during this investigation. The Q4-specific observations
above were therefore checked against the installed JAR metadata, bundled UI
source and Java bytecode. No deployment, database connection, Object
publication or live instance validation was performed.

Before this design is an implementable production contract, resolve and
document: the exact Q4 database support matrix; alias and company permissions;
the schema lifecycle integration and recovery protocol; physical field and
table mapping; supported Object field types; filter and sort semantics;
cross-database consistency; and an upgrade strategy for Q4 platform changes.
