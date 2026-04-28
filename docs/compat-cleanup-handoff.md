# Compat Cleanup Handoff

## What I changed

### Parser policy cleanup
- Updated `src/main/java/com/cyberday1/neoorigins/compat/condition/ConditionParser.java`
- Updated `src/main/java/com/cyberday1/neoorigins/compat/action/ActionParser.java`
- Updated `src/main/java/com/cyberday1/neoorigins/compat/package-info.java`

#### ConditionParser changes
- `parse(null, contextId)` now fails closed instead of returning `alwaysTrue()`.
- Unknown condition types still fail closed, but now use a shared `failClosed(...)` helper for consistent logging.
- Parse exceptions now route through `failClosed(...)` for the same logging/policy path.
- `origins:not` without a valid `condition` object now fails closed instead of negating `alwaysTrue()`.
- `origins:resource` without a valid `resource` field now fails closed.
- `origins:power_active` without a valid `power` field now fails closed.
- `origins:on_block` without a valid `block_condition` object or `block_condition.id` now fails closed.

#### ActionParser changes
- `parse(null, contextId)` now returns a logged no-op through a shared `failNoop(...)` helper.
- Unknown action types and parse exceptions now use `failNoop(...)` for consistent logging.
- `origins:if_else` without a valid `condition` object now defaults to `CompatPolicy.FALSE_CONDITION`, which means the `else_action` path runs instead of the `if_action` path.
- `origins:if_else_list` branches without a valid `condition` object now use `CompatPolicy.FALSE_CONDITION`, so malformed branches are skipped.
- Added an explicit comment on `execute_command` clarifying that commands run with the player's own permissions, not elevated server privileges.

#### package-info cleanup
- Updated compat subsystem docs to reflect that unknown actions use a fail-safe no-op and that missing required action/condition fields follow the explicit compat policy.

### Test scaffolding added
- Added JUnit and Mockito test dependencies to `build.gradle`.
- Added `useJUnitPlatform()` to Gradle test configuration.
- Added new tests:
  - `src/test/java/com/cyberday1/neoorigins/compat/condition/ConditionParserTest.java`
  - `src/test/java/com/cyberday1/neoorigins/compat/action/ActionParserTest.java`

#### Condition tests added
- `nullConditionFailsClosed`
- `unknownConditionTypeFailsClosed`
- `notWithoutConditionFailsClosed`
- `resourceWithoutResourceFieldFailsClosed`
- `onBlockWithoutRequiredFieldsFailsClosed`
- `onBlockWithoutBlockIdFailsClosed`

#### Action tests added
- `ifElseWithoutConditionExecutesElseAction`
- `ifElseListSkipsBranchWithoutCondition`

## Current status
- Source edits are in place.
- Test files are in place.
- `build.gradle` has been corrected after one bad intermediate edit.
- I was not able to finish validation because Gradle needs to run outside the sandbox to create its wrapper/cache directories, and that escalation was not approved before pausing.

## Remaining work

### 1. Run validation
Run:

```powershell
./gradlew test compileJava
```

If that passes, the compat cleanup is structurally done.

### 2. Fix any test/build issues that show up
Most likely areas if the build fails:
- missing/imported test dependencies under the NeoForge toolchain
- Mockito interaction with `ServerPlayer`
- formatting or doc-comment issues from the parser rewrites

### 3. Optional follow-up cleanup
These are not required to finish the current patch, but they would improve polish:
- move the compat fail helpers into `CompatPolicy` if you want the policy to be even more centralized
- add one more test around `power_active` missing `power`
- add one more test around `ConditionParser.parseResource(...)` with a real comparison path if a lightweight attachment test harness is available
- add one more test for unsupported action type returning a no-op

## Files touched
- `C:\Users\conno\Documents\NeoOrigins\build.gradle`
- `C:\Users\conno\Documents\NeoOrigins\src\main\java\com\cyberday1\neoorigins\compat\action\ActionParser.java`
- `C:\Users\conno\Documents\NeoOrigins\src\main\java\com\cyberday1\neoorigins\compat\condition\ConditionParser.java`
- `C:\Users\conno\Documents\NeoOrigins\src\main\java\com\cyberday1\neoorigins\compat\package-info.java`
- `C:\Users\conno\Documents\NeoOrigins\src\test\java\com\cyberday1\neoorigins\compat\action\ActionParserTest.java`
- `C:\Users\conno\Documents\NeoOrigins\src\test\java\com\cyberday1\neoorigins\compat\condition\ConditionParserTest.java`

## Important note
The repo state includes an intermediate failed edit that was corrected by rewriting `build.gradle`. The current file should be the one to keep, but it still needs Gradle validation before considering this complete.
