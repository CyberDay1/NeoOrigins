#!/usr/bin/env bash
#
# Asserts that release.yml still does what the release is assumed to do, without
# running it — running it means cutting a release. Three things it pins:
#
#   1. Every build job runs the GATE (`./gradlew build check`), not a bare
#      `./gradlew build`. `build` already depends on `check`, so this is belt and
#      braces against a `-x check` creeping in, and it is what makes the log honest.
#   2. The release guard runs BEFORE the build in every job, and exactly one job
#      claims the tag line. A guard that runs after the jar is built has already
#      lost, and two tag-line jobs would mean the lead's containment check is
#      being applied to a branch the tag can never be an ancestor of.
#   3. The "Report gate coverage" step's own predicate is TRUE on this checkout,
#      so the release log cannot print the "NOT verified" warning for a branch
#      that is in fact wired, or the reassurance for one that is not.
#
# hub: neoorigins/release-tag-guard.md
# Run: bash .github/scripts/release-workflow-check.sh    (also wired to `check`)

set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"
wf="$root/.github/workflows/release.yml"
gradle="$root/build.gradle"

[ -f "$wf" ] || { echo "release-workflow-check: cannot find $wf"; exit 1; }
[ -f "$gradle" ] || { echo "release-workflow-check: cannot find $gradle"; exit 1; }

fails=0
pass() { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; fails=$((fails + 1)); }

# Line number of a job's header, and of the next job header after it, so each
# assertion can be scoped to one job instead of matching anywhere in the file.
job_start() { grep -nE "^  $1:\$" "$wf" | head -1 | cut -d: -f1; }

# Print the body of job $1 (from its header to the next top-level job header).
job_body() {
    local start end
    start="$(job_start "$1")"
    [ -n "$start" ] || return 1
    end="$(awk -v s="$start" 'NR > s && /^  [a-z0-9_-]+:$/ { print NR; exit }' "$wf")"
    [ -n "$end" ] || end="$(wc -l < "$wf")"
    sed -n "${start},${end}p" "$wf"
}

# Line number WITHIN the job body of the first line matching $2.
line_in_job() {
    job_body "$1" | grep -nE "$2" | head -1 | cut -d: -f1
}

echo "release-workflow-check: $wf"

# ── the three build jobs, and the branch each is expected to check out ───────
#    job                 ref        is it the tag-bearing line?
rows='build-master:master:false
build-1-21-1:1.21.1:true
build-26-2:26.2:false'

while IFS=: read -r job ref tagline; do
    [ -n "$job" ] || continue

    if ! job_body "$job" >/dev/null 2>&1 || [ -z "$(job_start "$job")" ]; then
        fail "$job: job not found in release.yml"
        continue
    fi

    # It builds the branch it says it builds.
    if job_body "$job" | grep -qE "^ +ref: '$ref'\$"; then
        pass "$job: checks out '$ref'"
    else
        fail "$job: does not check out '$ref'"
    fi

    # [1] The gate, named explicitly.
    if job_body "$job" | grep -qE "^ +run: \./gradlew build check\$"; then
        pass "$job: runs the gate (./gradlew build check)"
    else
        fail "$job: does not run './gradlew build check' — a bare 'build' publishes an ungated jar the log still calls verified"
    fi

    # [3] The coverage report step is present.
    if job_body "$job" | grep -qE "^ +- name: Report gate coverage\$"; then
        pass "$job: reports gate coverage"
    else
        fail "$job: has no 'Report gate coverage' step"
    fi

    # [2] The guard is present, and runs before the build.
    guard_at="$(line_in_job "$job" '^ +- name: Release guard$')"
    build_at="$(line_in_job "$job" '^ +run: \./gradlew build')"
    if [ -z "$guard_at" ]; then
        fail "$job: has no 'Release guard' step"
    elif [ -z "$build_at" ]; then
        fail "$job: has no gradle build step to order the guard against"
    elif [ "$guard_at" -lt "$build_at" ]; then
        pass "$job: guard runs before the build"
    else
        fail "$job: guard runs AFTER the build — the jar is already built by the time it refuses"
    fi

    # [2] Exactly one job claims the tag line; this row says which.
    if job_body "$job" | grep -qE "^ +GUARD_TAG_LINE: '$tagline'\$"; then
        pass "$job: GUARD_TAG_LINE='$tagline'"
    else
        fail "$job: GUARD_TAG_LINE is not '$tagline'"
    fi
done <<< "$rows"

# Exactly one tag-line job across the whole file, not merely per row above.
tag_lines="$(grep -cE "^ +GUARD_TAG_LINE: 'true'\$" "$wf")"
if [ "$tag_lines" -eq 1 ]; then
    pass "exactly one job claims the tag line"
else
    fail "expected exactly 1 job with GUARD_TAG_LINE='true', found $tag_lines"
fi

# ── [3] the report step's predicate, evaluated here rather than trusted ──────
# Same two greps the workflow step runs. If they disagree with reality the
# release log lies in one direction or the other.
if grep -q 'powerEnumCheck' "$gradle" && grep -q "tasks.named('check')" "$gradle"; then
    pass "gate-coverage predicate is TRUE on this branch (the step will not warn)"
else
    fail "gate-coverage predicate is FALSE — the release log would warn that this jar is unverified"
fi

# ── the tasks 'check' depends on actually exist ──────────────────────────────
# `dependsOn 'name'` takes a string, so a typo or a task dropped in a port only
# surfaces when check runs. Resolve the names against the registrations instead;
# invoking gradle from inside a gradle task would recurse.
# tail -n +2 drops the tasks.named('check') header line itself, whose own
# quoted 'check' would otherwise read back as a dependency of itself.
deps="$(awk "/^tasks\.named\('check'\)/,/^}/" "$gradle" | tail -n +2 \
        | grep -oE "'[A-Za-z0-9_]+'" | tr -d "'")"
if [ -z "$deps" ]; then
    fail "could not read the check dependsOn list out of build.gradle"
else
    missing=''
    count=0
    for t in $deps; do
        count=$((count + 1))
        grep -qE "tasks\.register\('$t'" "$gradle" || missing="$missing $t"
    done
    if [ -n "$missing" ]; then
        fail "check depends on unregistered task(s):$missing"
    else
        pass "all $count tasks wired to check are registered in build.gradle"
    fi
fi

echo
if [ "$fails" -eq 0 ]; then
    echo "release-workflow-check: OK"
    exit 0
fi
echo "release-workflow-check: $fails failure(s)"
exit 1
