#!/usr/bin/env bash
# Commit the assembled Pages tree to origin/report-history.
# Updates only that branch. Does not check out or push main.
set -euo pipefail

export GIT_TERMINAL_PROMPT=0

: "${SITE_DIR:?}"
: "${RUN_ID:?}"

if [[ ! "$RUN_ID" =~ ^[0-9]+$ ]]; then
  echo "RUN_ID must be numeric" >&2
  exit 1
fi
if [ ! -f "$SITE_DIR/index.html" ] || [ ! -d "$SITE_DIR/runs/$RUN_ID" ]; then
  echo "site directory is missing this run" >&2
  exit 1
fi

repo_root=$(git rev-parse --show-toplevel)
cd "$repo_root"
primary_branch=$(git branch --show-current)
primary_head=$(git rev-parse HEAD)

SITE_DIR=$(cd "$SITE_DIR" && pwd -P)
case "$SITE_DIR" in
  "$repo_root"|"$repo_root"/*)
    echo "site directory must stay outside the checkout" >&2
    exit 1
    ;;
esac

remote_has_history() {
  local status
  set +e
  git ls-remote --exit-code --heads origin report-history >/dev/null
  status=$?
  set -e
  if [ "$status" -eq 0 ]; then
    return 0
  fi
  if [ "$status" -eq 2 ]; then
    return 1
  fi
  echo "cannot read origin/report-history (ls-remote status ${status})" >&2
  exit 1
}

history=${RUNNER_TEMP:-/tmp}/report-history-worktree
if [ -e "$history" ]; then
  git worktree remove --force "$history" 2>/dev/null || true
  rm -rf "$history"
fi
git worktree prune

if remote_has_history; then
  # The leading + updates only the local remote-tracking ref. Push below is not a force-push.
  git fetch --depth=1 origin '+refs/heads/report-history:refs/remotes/origin/report-history'
  git worktree add --detach "$history" origin/report-history
  git -C "$history" checkout -B report-history
else
  echo "origin/report-history does not exist yet; creating an orphan branch" >&2
  git worktree add --detach "$history" HEAD
  git -C "$history" checkout --orphan report-history
  git -C "$history" rm -rf . >/dev/null
fi

find "$history" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
cp -a "$SITE_DIR"/. "$history"/

if [ -e "$history/.git" ] && [ -d "$history/.git" ]; then
  echo "refusing to publish a nested git directory" >&2
  exit 1
fi

git -C "$history" add -A
if git -C "$history" diff --cached --quiet; then
  echo "report-history already matches this site" >&2
else
  git -C "$history" \
    -c user.name="github-actions[bot]" \
    -c user.email="41898282+github-actions[bot]@users.noreply.github.com" \
    commit -m "Publish test report for run ${RUN_ID}"
fi

if git -C "$history" ls-files | grep -q 'android-appium.yml'; then
  echo "refusing to push the application tree to report-history" >&2
  exit 1
fi

# Normal fast-forward. Do not force, and do not update any branch except report-history.
git -C "$history" push origin HEAD:report-history

branch=$(git branch --show-current)
head=$(git rev-parse HEAD)
if [ "$branch" != "$primary_branch" ] || [ "$head" != "$primary_head" ]; then
  echo "primary checkout changed while publishing report-history" >&2
  exit 1
fi
echo "pushed origin/report-history for run ${RUN_ID}" >&2
