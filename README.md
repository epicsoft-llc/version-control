# Version Control

An inventory of the versions your infrastructure actually runs.

It answers four questions:

1. Which version of a component runs in which environment?
2. Where do the environments differ?
3. Where is something newer available — and may you even jump there?
4. When was what changed, and why was something *not* changed?

It is an inventory, not a deployment system. It runs no actions on target hosts, it does not manage your
application dependencies (Renovate and Dependabot do that), and it does not read the deployed versions
by itself: you enter them, in the same work step in which you upgrade the component.

- Images: [Docker Hub](https://hub.docker.com/r/epicsoft/version-control) and the
  [GitLab container registry](https://gitlab.com/epicsoft-networks/version-control/container_registry)
- License: MIT
- Source: developed on [GitLab](https://gitlab.com/epicsoft-networks/version-control), mirrored to
  [GitHub](https://github.com/epicsoft-llc/version-control) — issues are welcome on both; code changes only as merge
  requests on GitLab, the mirror cannot take pull requests
- Changes: [CHANGELOG.md](https://gitlab.com/epicsoft-networks/version-control/-/blob/main/CHANGELOG.md)

> **Status: version 0.3.0.** Everything described here is built, tested and translated. Releases go to
> Docker Hub and the GitLab registry, branch builds to the GitLab registry only. The schema of 0.1.0 is
> fixed, so an upgrade from here on migrates rather than resets.

## What it looks like

The start page is the task list, not the matrix — it answers what needs doing, in a fixed order: out of
support before merely behind, a check that has been failing for days before a cosmetic warning.

![Task list](https://gitlab.com/epicsoft-networks/version-control/-/raw/main/docs/images/tasks.jpg)

The matrix is the second view: one row per component, one column per environment, and the newest
available version at the end. Rows whose environments disagree are marked, a cell that is deliberately
empty (`–`) is not the same as one nobody has filled in.

![Version matrix](https://gitlab.com/epicsoft-networks/version-control/-/raw/main/docs/images/matrix.jpg)

A component page carries the source and its version rule, the last check, what may be upgraded to next
per environment — and what would be a bigger jump than allowed, shown as a hint rather than a task —
plus the dependencies in both directions, evaluated per environment.

![Component detail](https://gitlab.com/epicsoft-networks/version-control/-/raw/main/docs/images/component-detail.jpg)

Projects, environments and components are maintained on one page.

![Master data](https://gitlab.com/epicsoft-networks/version-control/-/raw/main/docs/images/master-data.jpg)

## What it watches

Infrastructure components you maintain by hand — a database, an identity provider, a reverse proxy, a
monitoring stack, the Docker packages on a host. Sources it can check:

| Source | Example |
|---|---|
| Docker Hub | `library/postgres` |
| GitHub releases | `owner/repository` |
| APT repository | the Docker packages on Ubuntu — one component, several packages |
| Manual | anything without a machine readable source; kept by hand, never an error state |

On top of that it tracks end-of-life dates, records every change with a note, backup reference,
rollback path and downtime, and remembers why an available update was deliberately postponed.

Once a day it reports what is new — to a Telegram chat, by mail through any SMTP server, or both,
whichever is configured. On a day without news it sends nothing.

## Running it

```bash
cp .env.example .env      # fill in at least the database credentials
docker compose -f deploy/compose.yml up
```

Both files come from the repository: clone it, or take
[`deploy/compose.yml`](https://gitlab.com/epicsoft-networks/version-control/-/blob/main/deploy/compose.yml) and
[`.env.example`](https://gitlab.com/epicsoft-networks/version-control/-/blob/main/.env.example) alone.
`VC_IMAGE_REPOSITORY` and `VC_IMAGE_TAG` in `.env` choose the images — see [Images](#images).

The dashboard is then on <http://localhost:8081>. It serves the interface and forwards `/api` to the
backend, so there is a single entry point and the frontend build is environment independent.

**Access control is not part of this application.** It is meant to sit behind a reverse proxy or an
access gateway that handles authentication. Do not expose it directly.

## API

Everything the interface does goes through a REST API under `/api/v1`, and the API describes itself: the
OpenAPI 3.1 document is at `/api/v1/openapi.json` — <http://localhost:8081/api/v1/openapi.json> with the
compose file. It names every operation and field, explains every enum value — what `SKIPPED` does to the
task list, what switching `checkEnabled` off stops — and shows the one shape every error has. Scripts and
other tools can work from it without reading the code.

A component can be changed field by field. `PATCH /api/v1/components/{id}` takes a JSON merge patch
(RFC 7386): a field that is sent changes, a field that is left out keeps its value, `null` clears it.

```bash
curl -X PATCH -H 'Content-Type: application/merge-patch+json' -d '{"checkEnabled": false}' \
  http://localhost:8081/api/v1/components/<id>
```

`PUT` remains the way to replace a component as a whole.

## Developing

Requires Java 25 and Node 24 — `.vfox.toml` pins the versions.

```bash
./gradlew :core:build          # backend: compile and test
./gradlew :core:bootRun        # backend on :8080, actuator on :8090
./gradlew :core:test --tests "one.epicsoft.vc.util.VcUtilTest"

cd dashboard
npm ci
npm start                      # dev server on :4200, /api proxied to localhost:8080
npm run build
npm test                       # unit tests, vitest in jsdom
npm run i18n:verify            # every key used by the interface exists
```

Unit tests run anywhere. Tests tagged `integration` start a PostgreSQL container through
Testcontainers and are skipped automatically when no Docker daemon answers, so `./gradlew test` stays
green on a machine without one.

The dashboard tests run in jsdom through the Angular `unit-test` builder, so they need no browser and
no display — `npm test` is the whole story, locally and in CI.

The dev server forwards `/api` to `localhost:8080` (`proxy.conf.json`), the same way the dashboard
container does in a deployment. The interface therefore always calls a relative URL, in development as
in production, and the backend needs no CORS rule — a rule that would exist only for development and
would then have to be kept out of production by hand.

### Images

Two images are built from this repository, both in one repository per registry with the module in the tag:

| Image | Base | Contains |
|---|---|---|
| `vc-core` | `eclipse-temurin:25-jre-alpine` | the backend as a single jar, non-root, health check on 8090 |
| `vc-dashboard` | `nginx:alpine` | the built interface, SPA fallback, `/api` forwarded to the backend |

| Registry | Repository | Tags |
|---|---|---|
| Docker Hub | `epicsoft/version-control` | releases |
| GitLab | `registry.gitlab.com/epicsoft-networks/version-control` | releases and the branch builds `vc-core-main`, `vc-dashboard-develop`, … |

A release `1.2.3` is tagged `vc-core-1.2.3` and moves `vc-core-1.2`, `vc-core-1` and `vc-core-latest`
along, the same for `vc-dashboard`. A pinned full version changes only when you change it.

PostgreSQL comes from the official image; `deploy/compose.yml` starts all three. Swap the `image` line
for the commented `build` block in that file to run local changes.

### Layout

```
core/          Java 25 / Spring Boot 4 backend, REST under /api/v1
dashboard/     Angular frontend, served by nginx in its own image
deploy/        compose file for the local stack
docs/          documentation and the screenshots above
```

### Conventions

[`CODE_CONVENTION.md`](https://gitlab.com/epicsoft-networks/version-control/-/blob/main/CODE_CONVENTION.md) — read it before changing code. [`CONTRIBUTING.md`](https://gitlab.com/epicsoft-networks/version-control/-/blob/main/CONTRIBUTING.md)
covers the workflow. Translations are explained in [`docs/i18n.md`](https://gitlab.com/epicsoft-networks/version-control/-/blob/main/docs/i18n.md).

### Configuration

Everything is configured through environment variables; see [`.env.example`](https://gitlab.com/epicsoft-networks/version-control/-/blob/main/.env.example). Values that
only come from the environment carry the literal `USE_ENV` in `application.yml`, and every consumer
treats that exactly like a missing value — an unconfigured feature switches itself off instead of
calling an upstream API with the word `USE_ENV` as its token.

**Environment variables** — everything the containers need:

| Variable | Required | Effect |
|---|---|---|
| `VC_DATABASE_HOST` / `_PORT` / `_NAME` | yes | where PostgreSQL is; the compose file sets the first two |
| `VC_DATABASE_USERNAME` / `_PASSWORD` | yes | database credentials |
| `VC_HTTP_PORT` | no (8081) | the port the dashboard is published on |
| `VC_IMAGE_REPOSITORY` | no (GitLab) | where the compose file pulls from: `registry.gitlab.com/epicsoft-networks/version-control` or `epicsoft/version-control` on Docker Hub |
| `VC_IMAGE_TAG` | no (main) | a release such as `0.1.0` or `latest`, or a branch build, `main` or `develop` — Docker Hub carries releases only |
| `VC_GITHUB_TOKEN` | no | raises the GitHub rate limit from 60 to 5.000 requests/hour |
| `VC_TELEGRAM_BOT_TOKEN` / `VC_TELEGRAM_CHAT_ID` | no | the daily summary to a Telegram chat; without them nothing goes there |
| `VC_MAIL_HOST` / `_PORT` / `_SECURITY` | no | the daily summary by mail, through any SMTP server. `_SECURITY` is `starttls` (default, port 587), `tls` (465) or `none` (25, login in clear text); `_PORT` only for a different port |
| `VC_MAIL_USERNAME` / `_PASSWORD` | no | the login at the mail server; without a username none is attempted |
| `VC_MAIL_FROM` / `VC_MAIL_TO` | no | the sender (`Version Control <vc@example.com>` works) and comma separated recipients; without host, sender and recipient no mail is sent |
| `VC_TOLGEE_URL` / `_PROJECT_ID` / `_API_KEY` | no | build time only, to re-export the translations |

**Application settings** — `application.yml`, overridable per environment:

| Setting | Default | Effect |
|---|---|---|
| `vc.i18n.default-lang` | en | the language of the interface and of the daily message |
| `vc.scheduler.enabled` | true | switches every background job off in one place |
| `vc.scheduler.tick-interval` | 15m | how often the scheduler picks up **one** due component |
| `vc.check.default-interval` | 48h | how often a component is checked |
| `vc.check.min-interval` | 48h | hard lower bound, manual checks included |
| `vc.check.jitter` | 2h | spreads the checks instead of running them in a nightly batch |
| `vc.check.backoff` | 1h, 4h, 12h | how long a failing source is left alone, per consecutive failure |
| `vc.check.error-grace-days` | 3 | how long a failing check is tolerated before it becomes a task |
| `vc.eol.check-interval` | 7d | how often end-of-life dates are refreshed |
| `vc.eol.warn-months` / `escalate-months` | 6 / 3 | end-of-life thresholds |
| `vc.notification.daily-time` | 08:00 | when the daily summary is sent |

The two ports are fixed inside the images: 8080 serves the API, 8090 serves the actuator and stays
inside the compose network, which is what the container health check uses.

### Using a package mirror

The build resolves from Maven Central, the Gradle Plugin Portal and npm. To route it through a mirror,
set `VC_MAVEN_MIRROR_URL`, `VC_GRADLE_PLUGIN_MIRROR_URL` (optionally `VC_MAVEN_MIRROR_USERNAME` and
`VC_MAVEN_MIRROR_PASSWORD`) for Gradle, and `VC_NPM_REGISTRY`, `VC_NPM_USERNAME`, `VC_NPM_PASSWORD`
for npm in CI. Locally, npm reads an `.npmrc` — in your home directory, or in `dashboard/`, where it is
ignored by git. A mirror that requires authentication needs the credentials in it: an address alone
carries none, and the mirror answers `401 Unauthorized`.

```
registry=https://mirror.example.com/repository/npm-group/
//mirror.example.com/repository/npm-group/:username=<user>
//mirror.example.com/repository/npm-group/:_password=<base64 of the password>
```

Nothing about a mirror is stored in this repository.

## License

[MIT](https://gitlab.com/epicsoft-networks/version-control/-/blob/main/LICENSE) — Copyright (c) 2026 epicsoft LLC

The MIT license applies only to files created by epicsoft LLC. Third-party software — the Gradle wrapper, the
libraries bundled into the images and the images they build on, such as Eclipse Temurin, Alpine Linux and
nginx — remains under its own licenses.
