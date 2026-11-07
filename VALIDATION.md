# Validation record

## Scope

This record covers the required implementation and verification sequence completed on 2026-11-07 (JST). It distinguishes checks that ran from checks that were deliberately out of scope or require separate authority.

* Validated source revision: `8f376570d7d1043d36bde0c6bf2ed87878006219`
* Package: `com.notiontasks.app`, version `1.5.0` (code `22`)
* Physical device: Sony SOV42, Android 10 (API 29)

## Passed gates

| Area | Evidence |
| --- | --- |
| JVM, static analysis, APK | `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, and `:app:assembleDebugAndroidTest` completed successfully on the validated revision. `git diff --check` also passed. |
| Room migration | The Debug and instrumentation APKs were installed on the physical device. `TaskDatabaseMigrationTest` completed with 3 tests and 0 failures. |
| Sync queue and ambiguous creation | Stub tests cover retention of retryable intent, connection-scope isolation, client-identifier lookup, and prevention of duplicate CREATE. A dedicated test task was created against the connected Notion database with no category selected; the application returned to the list and displayed the synced state. |
| Notification revalidation | A direct stale `BLOCK_START` intent with an absent block identifier was delivered on the physical device. It completed without creating a task-block notification. The receiver also reads the current local block and task state before notifying. |
| Focus recovery and notification stop | A running specified-duration focus timer persisted a snapshot, survived a normal application crash and relaunch with decreasing remaining time, and did not duplicate the session. Selecting **End** in the expanded notification returned the screen to its configured duration and **Start** state, and cleared the active snapshot. |
| Japan calendar rules | Clock-injected tests cover date-only values, offset-bearing timestamps, a naive datetime, the JST day rollover, and Monday-start week bounds. The application routes task dates, schedule navigation, current time, and retention cutoffs through the Japan-time contract. |

## Boundaries and excluded checks

* Android force-stop is intentionally not treated as normal service recovery: Android prevents services from automatically restarting until the user opens the app again.
* Cloud-backup transport transmission was not run. The manifest rules and local backup simulation confirm that encrypted Notion credentials are excluded, but transmitting device data to an external backup provider requires separate explicit approval.
* UI/accessibility-specific testing was excluded by the agreed scope. Functional device checks above are not an accessibility certification.

## Release decision

All required code, local/database, timer, notification, migration, and connected-Notion validation gates listed above passed. The excluded backup-transport check remains explicitly non-passing rather than being counted as a successful cloud-backup test.
