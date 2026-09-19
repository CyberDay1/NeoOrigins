#!/usr/bin/env bash
#
# Harness for release-guard.sh. Builds a throwaway repo with the same topology
# the real one has — three parallel release lines sharing only an ancient base,
# the tag cut on the 1.21.1 line only — and drives the guard across a case
# table, asserting the exit code AND the reason it gave.
#
# The point is the failing rows. A guard nobody has watched refuse a build is
# not a guard, and this one cannot be exercised by running it: running it means
# pushing a tag, which means cutting a release.
#
# Run: bash .github/scripts/release-guard-test.sh     (also wired to `check`)

set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
guard="$here/release-guard.sh"
[ -f "$guard" ] || { echo "release-guard-test: cannot find $guard"; exit 1; }

work="$(mktemp -d 2>/dev/null || mktemp -d -t relguard)"
cleanup() { cd /; rm -rf "$work"; }
trap cleanup EXIT

repo="$work/fixture"
mkdir -p "$repo"
cd "$repo" || exit 1

g() { git -c user.name=fixture -c user.email=fixture@example.invalid "$@"; }

props() { printf 'mod_id=neoorigins\nmod_version=%s\nmod_group_id=x\n' "$1" > gradle.properties; }
commit() { g add -A >/dev/null 2>&1; g commit -q -m "$1" >/dev/null 2>&1; }

g init -q -b base . >/dev/null 2>&1
props 1.2.0; commit "ancient shared base"

# ── the 1.21.1 line: tag lives here ─────────────────────────────────────────
g checkout -q -b 1.21.1 base
props 2.2.98; commit "1.21.1: previous release"
g branch -q sideline                      # branches off BEFORE the tagged commit
props 2.2.99; commit "1.21.1: this release"
g tag -a v2.2.99 -m "NeoOrigins 2.2.99" >/dev/null 2>&1

# ── master: parallel line, reconciled to the same version ───────────────────
g checkout -q -b master base
props 2.2.99; commit "master: this release"

# ── 26.2: parallel line, tip NEVER bumped — the landmine ────────────────────
g checkout -q -b 26.2 base
props 2.2.98; commit "26.2: still on the previous release"

# ── sideline: same version as the tag, but the tag is not in its history ────
g checkout -q sideline
props 2.2.99; commit "sideline: work the tag was never cut from"

# ── aheadline: the lead tip moved past the tag (deliberately allowed) ───────
g checkout -q -b aheadline 1.21.1
commit_extra() { printf 'x\n' >> notes.txt; commit "1.21.1: a commit after the tag"; }
commit_extra

# ── futuretag: version names a tag that does not exist ──────────────────────
g checkout -q -b futuretag base
props 2.3.0; commit "a tip bumped before the tag was cut"

# exists in every checkout (untracked) but carries no mod_version
printf 'mod_id=neoorigins
mod_group_id=x
' > no-version.properties

pass=0; fail=0

# run <name> <branch-to-checkout> <expected-exit> <expected-substring> <env...>
run() {
  local name="$1" br="$2" want="$3" needle="$4"; shift 4
  g checkout -q "$br" 2>/dev/null
  local out rc
  out="$(env "$@" bash "$guard" 2>&1)"; rc=$?

  local ok=1
  [ "$rc" = "$want" ] || ok=0
  case "$out" in *"$needle"*) ;; *) ok=0 ;; esac

  if [ "$ok" = "1" ]; then
    pass=$((pass+1))
    printf '  ok    %-34s exit=%s  %s\n' "$name" "$rc" "$needle"
  else
    fail=$((fail+1))
    printf '  FAIL  %-34s exit=%s (wanted %s), looking for: %s\n' "$name" "$rc" "$want" "$needle"
    printf '%s\n' "$out" | sed 's/^/          | /'
  fi
}

echo "release-guard-test: driving .github/scripts/release-guard.sh"
echo

echo "-- it lets a good release through --"
run tag-line-tip-contains-tag      1.21.1    0 "[B] tag containment OK" \
    GUARD_BRANCH=1.21.1 GUARD_TAG_LINE=true  GUARD_EVENT=push GUARD_REF=refs/tags/v2.2.99
run parallel-line-version-agrees   master    0 "[B] skipped" \
    GUARD_BRANCH=master GUARD_TAG_LINE=false GUARD_EVENT=push GUARD_REF=refs/tags/v2.2.99
run tip-ahead-of-tag-allowed       aheadline 0 "[B] tag containment OK" \
    GUARD_BRANCH=1.21.1 GUARD_TAG_LINE=true  GUARD_EVENT=push GUARD_REF=refs/tags/v2.2.99
run dispatch-version-suffix-strip  1.21.1    0 "[B] tag containment OK" \
    GUARD_BRANCH=1.21.1 GUARD_TAG_LINE=true  GUARD_EVENT=workflow_dispatch GUARD_INPUT_VERSION=v2.2.99-1.21.1
run dispatch-tag-not-cut-yet       futuretag 0 "skipping [B]" \
    GUARD_BRANCH=1.21.1 GUARD_TAG_LINE=true  GUARD_EVENT=workflow_dispatch GUARD_INPUT_VERSION=v2.3.0

echo
echo "-- it refuses the landmine: a stale tip --"
run stale-tip-refused              26.2      1 "26.2 is at 2.2.98 but the tag says 2.2.99" \
    GUARD_BRANCH=26.2 GUARD_TAG_LINE=false GUARD_EVENT=push GUARD_REF=refs/tags/v2.2.99
run stale-tip-refused-on-tag-line  sideline  1 "is not an ancestor of the 1.21.1 tip" \
    GUARD_BRANCH=1.21.1 GUARD_TAG_LINE=true GUARD_EVENT=push GUARD_REF=refs/tags/v2.2.99
run stale-tip-refused-on-dispatch  26.2      1 "26.2 is at 2.2.98 but the tag says 2.2.99" \
    GUARD_BRANCH=26.2 GUARD_TAG_LINE=false GUARD_EVENT=workflow_dispatch GUARD_INPUT_VERSION=v2.2.99

echo
echo "-- it refuses a checkout it cannot vouch for --"
run pushed-tag-does-not-resolve    futuretag 1 "does not resolve in the '1.21.1' checkout" \
    GUARD_BRANCH=1.21.1 GUARD_TAG_LINE=true GUARD_EVENT=push GUARD_REF=refs/tags/v2.3.0
run ref-is-not-a-tag               1.21.1    1 "is not a tag ref" \
    GUARD_BRANCH=1.21.1 GUARD_TAG_LINE=true GUARD_EVENT=push GUARD_REF=refs/heads/1.21.1
run dispatch-without-a-version     1.21.1    1 "supplied no version input" \
    GUARD_BRANCH=1.21.1 GUARD_TAG_LINE=true GUARD_EVENT=workflow_dispatch GUARD_INPUT_VERSION=
run branch-not-named               1.21.1    1 "GUARD_BRANCH is not set" \
    GUARD_BRANCH= GUARD_TAG_LINE=true GUARD_EVENT=push GUARD_REF=refs/tags/v2.2.99
run properties-missing             1.21.1    1 "'no-such.properties' not found" \
    GUARD_BRANCH=1.21.1 GUARD_TAG_LINE=true GUARD_EVENT=push GUARD_REF=refs/tags/v2.2.99 \
    GUARD_PROPERTIES=no-such.properties
run properties-has-no-version      1.21.1    1 "no mod_version in" \
    GUARD_BRANCH=1.21.1 GUARD_TAG_LINE=true GUARD_EVENT=push GUARD_REF=refs/tags/v2.2.99 \
    GUARD_PROPERTIES=no-version.properties

echo
echo "release-guard-test: $pass passed, $fail failed"
[ "$fail" = "0" ] || exit 1
