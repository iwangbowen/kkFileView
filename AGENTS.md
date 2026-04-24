# AGENTS.md

This document is for coding agents and automation tools working in this repository.

## Project Overview

- Project: `kkFileView`
- Stack: Spring Boot + Freemarker + Redis/Redisson (optional) + JODConverter + front-end preview pages
- Main module: `server`
- Default local URL: `http://127.0.0.1:8012/`
- Production demo: `https://file.kkview.cn/`

This repository is a document preview service. Most user-facing work falls into one of these areas:

1. preview routing and file-type dispatch
2. conversion pipelines for Office / PDF / CAD / archives / images
3. Freemarker preview templates under `server/src/main/resources/web`
4. CI, E2E fixtures, and production deployment automation

## Repository Layout

- `server/`
  Main application code, templates, config, packaged artifacts
- `server/src/main/java/cn/keking/`
  Core Java application code
- `server/src/main/resources/web/`
  Freemarker preview templates
- `server/src/main/resources/static/`
  Front-end static assets used by preview pages
- `server/src/main/config/`
  Main runtime config files
- `server/src/main/bin/`
  Local startup/dev scripts
- `tests/e2e/`
  Playwright-based end-to-end tests and fixtures
- `.github/workflows/`
  CI and deployment workflows
- `.github/scripts/`
  Windows production deployment scripts over WinRM

## Key Entry Points

- App entry:
  - `server/src/main/java/cn/keking/ServerMain.java`
- Preview controller:
  - `server/src/main/java/cn/keking/web/controller/OnlinePreviewController.java`
- File attribute parsing / request handling:
  - `server/src/main/java/cn/keking/service/FileHandlerService.java`
- Office preview flow:
  - `server/src/main/java/cn/keking/service/impl/OfficeFilePreviewImpl.java`
- PDF preview flow:
  - `server/src/main/java/cn/keking/service/impl/PdfFilePreviewImpl.java`
- Archive extraction:
  - `server/src/main/java/cn/keking/service/CompressFileReader.java`

## Important Templates

- `server/src/main/resources/web/compress.ftl`
  Archive directory/tree preview page
- `server/src/main/resources/web/pdf.ftl`
  PDF preview container page
- `server/src/main/resources/web/picture.ftl`
  Single image preview page
- `server/src/main/resources/web/officePicture.ftl`
  Office/PDF image-mode preview page
- `server/src/main/resources/web/officeweb.ftl`
  Front-end xlsx/html preview page

When debugging UX issues, inspect the exact template selected by the preview flow first. Do not assume two similar preview pages share the same CSS or behavior.

## Local Development

### Recommended dev mode

Use:

```bash
./server/src/main/bin/dev.sh
```

This runs Spring Boot with resource hot reload using:

- `spring-boot:run`
- `-Dspring-boot.run.addResources=true`
- `server/src/main/config/application.properties`

For front-end template or CSS/JS edits, prefer `dev.sh` over rebuilding jars repeatedly.

### Jar build

```bash
mvn -q -pl server -DskipTests package
```

### Main test command used in CI

```bash
mvn -B package -Dmaven.test.skip=true --file pom.xml
```

## Configuration Notes

Primary runtime config used by the scripts and defaults committed in this repository:

- `server/src/main/config/application.properties`

Optional environment-specific config:

- `server/src/main/config/test.properties`

Be careful: the repository defaults point at `application.properties`. If a deployment environment explicitly starts the app with `test.properties`, treat that as an environment-specific override rather than the repository default. Always verify the actual startup command before assuming which config file is active.

Examples of config that commonly affects behavior:

- `office.preview.type`
- `office.preview.switch.disabled`
- `trust.host`
- `not.trust.host`
- `file.upload.disable`

## Preview Behavior Notes

- Office files can render in `pdf` mode or `image` mode.
- PDF preview uses `pdf.ftl`.
- Single images use `picture.ftl`.
- Office image-mode previews use `officePicture.ftl`.
- Archive previews are not simple file lists; they can load nested previews via the archive UI in `compress.ftl`.

When changing preview defaults, verify both:

1. server-side default config
2. front-end mode-switch links/buttons

## Archive Preview Notes

Archive preview is a sensitive area because it combines:

- directory tree generation
- extraction to disk
- nested preview URL construction
- inline iframe loading

If an archive-contained Office file gets stuck on loading:

1. verify the extracted file on disk is not corrupted
2. verify conversion output exists
3. verify the preview template points to the correct generated artifact
4. verify the running Office manager / LibreOffice process is healthy

Do not assume “loading forever” is a front-end issue.

## Testing

### Targeted Java tests

Example targeted test:

```bash
mvn -q -pl server -Dtest=PdfViewerCompatibilityTests test
```

### E2E tests

See:

- `tests/e2e/README.md`

PR E2E currently covers:

- common preview smoke tests
- Office smoke tests
- archive smoke tests
- basic security and performance checks

## CI / Deployment

### CI

- `maven.yml`
  - builds on `push` to `master`
  - builds on PRs targeting `master`
- `pr-e2e-mvp.yml`
  - runs E2E on PRs to `master`

### Production deployment

- `master-auto-deploy.yml`
  - triggers on push to `master`
  - deploys to Windows over WinRM

Deployment script:

- `.github/scripts/remote_windows_deploy.ps1`

Important operational detail:

- the committed `bin/startup.bat` in this repo points at `..\config\application.properties`
- if production uses a different config file, treat that as an out-of-repo server override rather than a repository default

If a production config change “does not take effect”, inspect the actual startup command or deployed `startup.bat` on the server first and verify which config file path it is using.

## Working Conventions For Agents

- Prefer minimal, targeted changes over wide refactors.
- Inspect the active preview template before editing CSS.
- Verify whether behavior is controlled by config, back-end routing, or front-end template logic before changing code.
- For production/debug tasks, distinguish clearly between:
  - repository source defaults
  - deployed server config
  - runtime process arguments
- When changing defaults, mention whether the change affects:
  - local dev only
  - repository default config
  - deployed server config
  - existing query-param overrides

## Suggested Validation Checklist

For preview-related changes, validate as many of these as apply:

1. target URL returns `200`
2. selected template is the expected one
3. generated intermediate artifacts exist when required
4. target UI element or style change is actually present in rendered HTML
5. targeted Java test passes
6. relevant E2E path is still compatible

## Non-Goals

This file is not a replacement for user-facing product documentation. Keep it focused on helping coding agents navigate the codebase and make correct changes faster.
