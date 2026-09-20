#!/usr/bin/env bash
#
# Release guard. release.yml checks out branch TIPS, not the tagged commit, so a
# tag cut while one tip is stale publishes three jars that disagree with each
# other and with the tag's own changelog. Published changelogs are frozen, so
# that is not recoverable after the fact — refuse the build instead.
#
# Two assertions, because the three lines are parallel histories:
#   A  version agreement  - gradle.properties mod_version must equal the tag.
#                           Runs in every job. This is the one that catches a
#                           stale tip on master/26.2, which can never contain
#                           the tag because the tag is cut on the 1.21.1 line.
#   B  tag containment    - HEAD must contain the tag's commit. Runs only in the
#                           job whose line owns the tag (GUARD_TAG_LINE=true).
#                           Catches a tag cut on a side branch the tip never got.
#
# Inputs, all environment so the script takes no arguments and stays testable:
#   GUARD_BRANCH         branch this job checked out, for the message
#   GUARD_EVENT          github.event_name
#   GUARD_REF            github.ref                    (tag pushes)
#   GUARD_INPUT_VERSION  github.event.inputs.version   (workflow_dispatch)
#   GUARD_TAG_LINE       "true" only on the tag-bearing line
#   GUARD_PROPERTIES     path to gradle.properties (default: gradle.properties)
#
# Exit 0 = safe to build. Exit 1 = refused, with the reason on stdout.

set -uo pipefail

branch="${GUARD_BRANCH:-}"
event="${GUARD_EVENT:-push}"
ref="${GUARD_REF:-}"
input="${GUARD_INPUT_VERSION:-}"
tag_line="${GUARD_TAG_LINE:-false}"
props="${GUARD_PROPERTIES:-gradle.properties}"

say()  { printf '%s\n' "$*"; }
warn() { printf '::warning::%s\n' "$*"; }
fail() { printf '::error::%s\n' "$*"; exit 1; }

[ -n "$branch" ] || fail "release-guard: GUARD_BRANCH is not set; refusing to vouch for an unnamed checkout."

# ── Which tag are we releasing? ──────────────────────────────────────────────
if [ "$event" = "workflow_dispatch" ]; then
  raw="$input"
else
  raw="${ref#refs/tags/}"
  [ "$raw" != "$ref" ] || raw=""
fi
# Mirror the suffix strip the release job does, so both read the same version.
tag="${raw%-1.21.1}"

if [ -z "$tag" ]; then
  if [ "$event" = "workflow_dispatch" ]; then
    fail "release-guard: workflow_dispatch supplied no version input; cannot tell what '$branch' is meant to be releasing."
  fi
  fail "release-guard: '$ref' is not a tag ref, but this workflow only publishes from tags."
fi

version="${tag#v}"
say "release-guard: branch '$branch', event '$event', releasing '$tag' (version $version)."

# ── A. Version agreement ─────────────────────────────────────────────────────
[ -f "$props" ] || fail "release-guard: '$props' not found in the '$branch' checkout."

mod_version="$(sed -n 's/^[[:space:]]*mod_version[[:space:]]*=[[:space:]]*\([^[:space:]]*\).*/\1/p' "$props" | head -1)"

if [ -z "$mod_version" ]; then
  fail "release-guard: no mod_version in '$props' on '$branch'; cannot prove this tip is the one being released."
fi

if [ "$mod_version" != "$version" ]; then
  say ""
  say "  The '$branch' tip is STALE for this release."
  say "    tag being built : $tag  (version $version)"
  say "    $props on $branch : mod_version=$mod_version"
  say ""
  say "  release.yml checks out branch tips, not the tagged commit, so this job"
  say "  would build a $mod_version jar and publish it as $version. The three jars"
  say "  would disagree, and published changelogs cannot be edited afterwards."
  say ""
  say "  Fix: bump mod_version on '$branch' and push the tip BEFORE tagging."
  say ""
  fail "release-guard: $branch is at $mod_version but the tag says $version."
fi
say "release-guard: [A] version agreement OK — $branch carries mod_version=$mod_version."

# ── B. Tag containment, tag-bearing line only ────────────────────────────────
if [ "$tag_line" != "true" ]; then
  say "release-guard: [B] skipped — '$branch' is not the tag-bearing line, so the tag is not in its history by design."
  exit 0
fi

# A shallow checkout has neither the tag nor enough history to answer this.
if git remote 2>/dev/null | grep -q . ; then
  git fetch --force --quiet origin "refs/tags/${tag}:refs/tags/${tag}" 2>/dev/null || true
  if [ "$(git rev-parse --is-shallow-repository 2>/dev/null)" = "true" ]; then
    git fetch --unshallow --quiet 2>/dev/null || true
  fi
fi

tag_commit="$(git rev-parse -q --verify "refs/tags/${tag}^{commit}" 2>/dev/null)"
if [ -z "$tag_commit" ]; then
  if [ "$event" = "workflow_dispatch" ]; then
    warn "release-guard: tag '$tag' does not exist yet; skipping [B] for '$branch' because this is a manual dispatch."
    exit 0
  fi
  fail "release-guard: tag '$tag' was pushed but does not resolve in the '$branch' checkout."
fi

head_commit="$(git rev-parse -q --verify "HEAD^{commit}" 2>/dev/null)"
[ -n "$head_commit" ] || fail "release-guard: the '$branch' checkout has no HEAD commit."

if git merge-base --is-ancestor "$tag_commit" "$head_commit" 2>/dev/null; then
  say "release-guard: [B] tag containment OK — $branch tip ${head_commit:0:12} contains $tag (${tag_commit:0:12})."
  exit 0
fi

say ""
say "  The '$branch' tip does NOT contain the tag being built."
say "    tag  $tag -> ${tag_commit:0:12}"
say "    tip  $branch -> ${head_commit:0:12}"
say ""
say "  The tag was cut somewhere this branch never received — a side branch, or a"
say "  tip that was force-moved after tagging. This job would build a jar with"
say "  different code from the one the tag's changelog describes."
say ""
say "  Fix: retag at the '$branch' tip, or push the commits the tag was cut from."
say ""
fail "release-guard: $tag is not an ancestor of the $branch tip."
