# Code Conventions

Rules for this repository — Java (Spring Boot) and Angular (TypeScript). Written for developers and
code generators alike. Read it before changing code; the rules are applied actively, not recalled.

---

## General

### Deliberate exceptions — `// ok: <reason>`

A line that knowingly breaks a convention carries a trailing `// ok: <reason>`. The comment records
that the deviation was a decision, not an oversight. Reviewers and linters accept annotated lines.

```java
return; // ok: early exit — nothing to do without a configured token
```

A bare `// ok` without a reason is not valid.

### Suppressing warnings is a human decision

Anything that silences a compiler, linter or inspection — `@SuppressWarnings`, `// noinspection`,
`eslint-disable`, `@ts-ignore`, `NOSONAR` — may only be added by a person, never by a code generator.
A generated change that trips a warning is reported, not muted: fix the cause, or leave the warning
standing and mention it, so a human decides whether the suppression is warranted. Test code is the
exception. An existing suppression is never removed just because it is in the way.

### Prefer the positive branch

Use an early return only when it genuinely reduces nesting or handles a distinct abort case.

```java
// ✗
if (isNull(value)) {
  return;
}
doSomething(value);

// ✓
if (nonNull(value)) {
  doSomething(value);
}
```

### Comments explain why

The code says what it does. A comment earns its place by saying why — a decision, a trade-off, a trap
someone will otherwise walk into a second time. Comments that restate the next line are deleted.

Keep them short and rare. A file where every second line is a comment is not better documented, it is
harder to read: the reader stops seeing the code. One or two lines for the reason, not a paragraph.

---

## Java

### Style

- `final` on every local variable and parameter that is not reassigned.
- Wrap at 180 characters.
- `this.` on every access to an instance field or instance method: `this.service.foo()`, never
  `service.foo()`. Static members are exempt.
- Null checks through `java.util.Objects` — `nonNull(x)`, `isNull(x)`, `requireNonNullElse(x, y)` —
  never `== null`. Import statically.
- Blank strings through `VcUtil` — `nonBlank(x)`, `isBlank(x)`.
- Configuration values through `VcUtil.isConfigured(x)`: it treats the literal `USE_ENV` as absent.
  Without that check a component believes it is configured and sends `USE_ENV` upstream as a token.
- `@Nonnull` / `@Nullable` (`jakarta.annotation`) on every method signature and field. Controllers are
  exempt — nullability there is expressed on DTO fields through Jakarta Validation. MapStruct abstract
  methods are exempt too; their `default` methods are not.
- String building with `.formatted(...)`, never `+`.
- Unused lambda parameters use `_`.
- Method naming: `create*` persists, `build*` constructs in memory, `get*` throws when absent, `find*`
  returns `Optional` and never throws.

### Layers

```
Controller → Facade → Service → Repository
```

| Layer | Responsibility |
|---|---|
| Controller | HTTP in and out. DTOs only, no entities, no business logic. |
| Facade | Orchestration across services. Owns the transaction for an HTTP entry point. |
| Job | Scheduled or event driven entry. Owns the transaction for background work. |
| Service | One entity's lifecycle. Transaction free. |
| Repository | Database access, Spring Data interfaces only. |

The entry point owns the transaction; services participate in it. `@Transactional` therefore belongs on
the facade or the job, not on the service — `@Transactional(rollbackFor = Throwable.class)`, always with
the `rollbackFor`, because Spring otherwise rolls back only on `RuntimeException` and silently swallows
checked ones. Read-only methods add `readOnly = true`.

`@Scheduled` methods live in the `job` package in a class suffixed `Job`, never on a service.

A facade method that lists by a foreign id **reads that entity first**. An unknown or soft deleted id has
to answer 404; an empty list would tell the reader that nothing is there, which is a different and wrong
statement. The same holds for a derived view: it reads its root before assembling.

Where two paths can write the same state, the invariant belongs on **both**. A guard that only sits on
the newer path leaves the older one producing exactly the state the guard declares impossible.

### Packages

```
one.epicsoft.vc
├── config/          Spring configuration
├── controller/      REST controllers, paths under api/v1
├── facade/          orchestration, transaction boundary
├── job/             @Scheduled workers
├── service/         single entity logic
├── dto/             HTTP request and response objects
├── data/            internal records, never serialized
├── jpa/entity/      JPA entities
├── jpa/repository/  Spring Data repositories
├── enums/
├── exception/
├── i18n/
└── util/
```

Records and internal value types live in `data`, one public type per file — not nested inside the class
that happens to use them. `dto` crosses the HTTP boundary, `data` never does.

### Entities

Fixed field order: id, scalar fields, relations, timestamps, `@PrePersist`/`@PreUpdate`, helpers,
custom getters and setters.

- Every field carries `@Nonnull` or `@Nullable`, matching the database constraint. Never mix
  `@Nullable` with `nullable = false`.
- Do **not** initialize non-null fields inline (`= false`, `= SomeEnum.DEFAULT`). Hibernate may not see
  a field as dirty that was never set through its setter; set defaults in `@PrePersist`.
- No Jackson annotations on entities. What appears in JSON is the DTO's business.
- `@OneToMany` collections are left null, not initialized inline.
- Never cache an entity. A cached entity is detached: its lazy relations either throw or freeze, and
  every thread gets the same instance. Cache the mapped DTO, or nothing.

### DTOs

Plain `@Data` classes, not records. Every field `private`, with `@JsonProperty` and `@Nonnull` or
`@Nullable`. A DTO that MapStruct writes into needs an explicit `@NoArgsConstructor`: `@Data` generates
a constructor over all `@Nonnull` fields and MapStruct would build through it, passing null for every
ignored field. Request DTOs carry Jakarta Validation annotations — `@Nonnull` is static analysis only
and enforces nothing at runtime.

### API description

The OpenAPI document at `/api/v1/openapi.json` is generated from the code. What the code cannot say is written
next to it, and `OpenApiIntegrationTest` holds every operation and every enum to that:

- Every controller carries `@Tag`, every endpoint `@Operation` with a `summary`, and a `description` wherever a rule
  is not visible in the signature — what a replacement clears, which parameter is required, what is refused.
- Every enum that crosses the HTTP boundary carries `@Schema(enumAsRef = true, description = …)` with one line per
  value. A new constant without its line fails the test.
- A field gets `@Schema(description = …)` when its meaning is not its name: a default, a format, what `null` or an
  empty list does. A Javadoc that only says the same thing is replaced by it, not kept beside it.
- The error answer is declared once, on the catch-all of `AppExceptionHandler`.

### Exceptions

One class per domain concept, extending `AppException`, with a private constructor and static factories
named `by{Criteria}`. The exception message is English and for the log; the message key it carries is
what the user sees, translated.

### Message keys

A key that reaches a user is written in exactly two places: passed to an `AppException` factory in the
`exception` package (or in `AppExceptionHandler`, which maps the framework's own errors), or handed to
`TranslationService`. Nothing else composes one.

That is not style, it is what makes the keys checkable: `verifyMessageKeys` collects them from those two
places and compares them against the fallback bundle. A key built somewhere else is invisible to it, and
an unknown key is not an error at runtime — the key itself is what the reader gets.

### Logging

English, `@Slf4j`, and never a value that only belongs in the database. A controller logs the
identifiers of a request, not the request object, as soon as the payload carries free text: a note, a
rollback path or a backup reference routinely holds host names and snapshot ids, and a log file is read
by more people, and kept in more places, than the table it came from. Payloads that are nothing but
identifiers and enums may be logged whole.

### Outbound calls

A call to a foreign server never happens inside a transaction. The entry point splits into read, call
and write — `collect()`, send, `remember(draft)` — so no database connection waits on a server nobody
here controls, and so nothing is written down as done before it actually was.

Where a secret is part of an address, **nothing from the client's exception is passed on or logged** —
not the message, not the cause. An HTTP client puts the request URI into both, and a token in the URI is
then a token in the log, and from there in every backup of it. Logged is the exception class, thrown is
a sentence of our own.

### Migrations

`core/src/main/resources/db/migration/V{version}__{snake_case}.sql`.

- Always allocate the next free version and bump the project version to match.
- Never modify a migration that has been applied — add a new one. `V0.1.0__init.sql` is **closed**: it
  has been published in an image, so somewhere a database has already applied it, and editing it now
  makes Flyway refuse to start on exactly that database with a checksum mismatch. Every change to the
  schema from here on is its own file.
- UUID primary keys use the native PostgreSQL `uuid` type. Timestamps are `TIMESTAMP(6)` in UTC.
- On tables with a `deleted` column, uniqueness is a **partial** index `WHERE deleted IS NULL`; a plain
  `UNIQUE` constraint would let a soft deleted row block a re-insert.
- Foreign keys are named `FK_{table}_{refTable}`, unique indexes `UQ_{table}_{columns}`.

---

## Angular

### Access modifiers

Every class member has an explicit one:

| Member | Modifier |
|---|---|
| Bound in the template | `protected` |
| Injected services, internal helpers | `private readonly` |
| Public API — inputs, outputs, exposed methods | `public` |

Dependencies are injected with `inject()`, not through the constructor.

### Change detection

Every component names its strategy: `changeDetection: ChangeDetectionStrategy.Default`.

That is not decoration. This Angular version compiles a component as **OnPush unless told otherwise**,
and these components hold their state in plain fields written from `subscribe` callbacks. An OnPush view
that nothing marks dirty keeps the page it first rendered — the application loads its data, shows a
heading and nothing else, and reports no error anywhere. The application therefore also configures
`provideZoneChangeDetection`, and `zone.js/plugins/zone-patch-fetch` is in the polyfills so an HTTP
answer arrives inside the zone.

The moment a component holds its state in signals, it may drop the strategy — that is the direction, not
the state today. A test that lets Angular render on its own (`autoDetectChanges`, no manual
`detectChanges`) guards the difference; without it every test passes while the application shows nothing.

### Components

Standalone, every import listed explicitly, selector prefix `vc-`. Template control flow uses `@if`
and `@for` with a `track` expression, never `*ngIf` / `*ngFor`.

Subscriptions are collected in a `subs` array and released in `ngOnDestroy` through `unsubscribe(...)`.

### Requests in components

Every subscription that can fail carries an `error` branch. A failed request must clear the loading flag
it set and put the server's translated message on screen — without that the page keeps a spinner or, worse,
renders nothing at all and says nothing.

State that belongs to a selection is cleared **before** the request that refreshes it, not after it
arrives. The previous project's rows under the new project's heading read as the new project's state, and
the buttons in those rows then act on records the reader is no longer looking at.

Where a selection can be changed faster than the server answers, the selection goes through a `Subject`
and `switchMap` rather than a fresh `subscribe` per click: the older answer would otherwise land last.

### Null checks

`isDefined(value)` and `notDefined(value)` from `libs/utils/vc.utils`, never truthiness — `0` and `''`
are values. Prefer the positive form.

### API layer

Components never touch `HttpClient`. Every call goes through a repository in `repositories/api/`, which
holds its path templates as `private readonly` constants and builds URLs with `UrlBuilder` — never by
concatenating strings. DTOs are plain classes with a `constructor(init?: Partial<T>)` that does
`Object.assign(this, init)`.

### Text

No user-visible literal in a template or a component. Every string is a translation key, resolved with
the `translate` pipe or `TranslateService`. See [`docs/i18n.md`](docs/i18n.md).

Dates go through the `localDate` pipe, never Angular's `date`: that one formats in the `LOCALE_ID` fixed
at startup and ignores the language the reader chose. A new language brings its locale data to
`libs/pipes/local-date.pipe.ts`; a test fails for a language of the environment without it.

### Dependency versions

**Every version in `package.json` is exact — `"22.1.4"`, never `"^22.1.4"` or `"~22.1.4"`.** A range
means the build resolves something different tomorrow than it did today, and an Angular upgrade that
arrives by itself is exactly the kind of change nobody is watching for. Upgrades happen deliberately,
in a commit that says so.

The same holds in `libs.versions.toml` on the backend side.

This is about the versions *we* declare. A transitive dependency that carries a range is its author's
business — that is what the lock file is for, and `package-lock.json` is committed and never edited by
hand. Run `npm install` after changing `package.json` and commit the resulting lock file.

Angular packages move as a set: `@angular/core`, `@angular/common`, `@angular/router` and the rest
carry the same version, and `@angular/compiler-cli` matches them. A peer dependency that pulls a newer
patch of one of them forces all of them up — align them rather than letting npm resolve a mixture.

### Styling

Bootstrap utilities and ng-bootstrap components carry the layout; custom CSS only where Bootstrap has
nothing. Bootstrap Icons for iconography.

The look is one theme in `src/styles.scss`: palette, type and shapes are mapped onto Bootstrap's own CSS
variables there, so a template keeps Bootstrap's classes — `btn-primary`, `card`, `badge` — and gets the
theme with them. A colour or a radius written into a template or a component stylesheet is a second theme
next to the first; it goes into the variables instead. A section of a page is a `card card-body`, its
heading a `vc-eyebrow`.

A centred container inside a flex column needs an explicit `width: 100%` alongside `max-width` and
`margin: 0 auto`. Two auto margins on the cross axis turn `stretch` off, the item falls back to
`fit-content`, and a single long unbreakable string then pushes the whole page sideways.
