## Documentation
All README files are created for humans. They contain basic short high-level overview and instructions for configuration and deployment.
Files for AI agents are located in docs folder.
All documents contain current actual requirements and current app state, but not a history of changes. Exception is ADR document.

This application is created by AI. That should be mentioned once in the root README and nowehre else. Don't mention AI in other documents or in git commit messages.

### Doc files
docs/REQUIREMENTS.md - main overview of the app. Other files (like ARCHITECTURE.md and FEATURES.md) define more specific technical details or usecases. If other file definition conflicts with REQUIREMENTS, then REQUIREMENTS has a priority and other files should be corrected (ask developer first).
docs/ARCHITECTURE.md - describes technical details of how app should be implemented. Most of decisions there are discussable - if you see a better solution that fits REQUIREMENTS - propose your (better) solution to developer. Any decision made goes to the docs/ADR.md file.
docs/FEATURES.md - list of app features to be implemented (in a fasion of to-do list). Features a split onto milestones (alpha, beta, RC).
docs/ADR.md - list of decisions made, in historical order. Maintained by AI and mostly for AI.

### AI files
Do not change CLAUDE.md or GEMINI.md file in the root folder. Don't append them into git.
All documentation changes go to the 'docs' folder. If you need to add/change instructions for an AI agent, changes go here into docs/AI-AGENT.md after being approved by developer.

### Build / deploy / setup instructions
Create (and maintain) necessary build + deploy and setup instructions.
All instructions should be command-line instructions using standard build tools (and gcloud cli). If some setup steps are manual and can't be done using cli - provide detailed step-by-step instructions instead of cli.
Build + deploy instructions (for a new app version) go to README files per project (frontend/backend).
One-time configuration instructions (such as database index, GCS bucket creation) go to a separate document file SETUP.md into corresponding folder (frontend/backend).

## git
It is a single-developer project. We don't need feature branches and PRs. All work goes into the single 'main' branch.
Document changes (including READMEs) always go as a separate commit.
You're allowed to commit your changes into a current branch unless developer asks you to make changes without commiting them.
Split your work onto several commits to make changes atomic and consistent - after each commit application should be compilable and run tests without errors. Don't push anything to origin - it is a developer's responsibility and will be done manually each time.
Always run unit tests before commit.
Commit messages should be strictly on-line messages, without author and other garbage info. Commit messages follow 'semantic commits' (based on 'conventional commits') approach.

## tests
Always create unit tests for the new functionality, including edge cases and rare cases.

## Agent skills
Skills are installed globally (user level) with the `npx skills` CLI.

### Frontend (Android)
When writing frontend code, delegate the work to a dedicated agent that uses the Android skills below.

From `android/skills`:
- testing-setup
- jetpack-compose-performance
- navigation-3
- edge-to-edge

From `new-silvermoon/awesome-android-agent-skills`:
- android-coroutines
- kotlin-concurrency-expert
- compose-ui
- compose-performance-audit
- android-accessibility
- android-gradle-logic
- gradle-build-performance
- android-architecture
- android-testing

### Backend (Kotlin + Ktor)
When writing backend code, delegate the work to a dedicated agent that uses the Kotlin backend skills below.

From `affaan-m/ecc`:
- kotlin-ktor-patterns
- kotlin-testing

Every type that Ktor serializes or deserializes by type is looked up by reflection in the native image: `body<T>()` on the client, `receive<T>()` and `respond(value)` on the server, including type arguments (`List<T>`) and library types such as `JsonElement`, and also when `T` is inferred rather than written. When such a type is added, renamed or removed, register its `Companion` field and its `Companion.serializer()` method in backend/src/main/resources/META-INF/native-image/com.sixteenscreener/sixteen-screener-backend/reflect-config.json in the same commit. A missing type works in JVM tests but fails in the deployed backend.
`KtorTypeInfoReflectConfigTest` checks this across the whole backend and is part of `./gradlew test`. Never skip, disable or weaken it to make a commit pass; register the reported type instead.

### Project decisions override skills
Where a skill's defaults conflict with docs/ARCHITECTURE.md or docs/ADR.md, the project documents win. In particular:
- Android tests use JUnit4; backend tests use Kotest.
- The Android app uses Hilt for dependency injection. The backend uses no DI library (no Koin): dependencies are passed through constructors and wired by hand.
- The Android app is a single module with layered packages, not the multi-module layout from android-architecture.
- The Android HTTP client is written by hand on standard Android HTTP libraries, following docs/openapi.yaml. No generated client and no Ktor Client.
