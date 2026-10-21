#!/usr/bin/env bash
# Build a GitHub Pages tree for this run plus older runs from origin/report-history.
# Does not check out or push any branch.
set -euo pipefail

export GIT_TERMINAL_PROMPT=0

: "${RUN_ID:?}"
: "${RUN_SHA:?}"

if [[ ! "$RUN_ID" =~ ^[0-9]+$ ]]; then
  echo "RUN_ID must be numeric" >&2
  exit 1
fi
if [[ ! "$RUN_SHA" =~ ^[0-9a-fA-F]{40}$ ]]; then
  echo "RUN_SHA must be a 40-character git sha" >&2
  exit 1
fi

ALLURE_DIR=${ALLURE_DIR:-incoming/allure}
GRADLE_DIR=${GRADLE_DIR:-incoming/gradle}
KEEP_RUNS=30

repo_root=$(git rev-parse --show-toplevel)
cd "$repo_root"
primary_branch=$(git branch --show-current)
primary_head=$(git rev-parse HEAD)

SITE_DIR=${SITE_DIR:-${RUNNER_TEMP:-/tmp}/pages-site}
mkdir -p "$(dirname "$SITE_DIR")"
mkdir -p "$SITE_DIR"
SITE_DIR=$(cd "$SITE_DIR" && pwd -P)
case "$SITE_DIR" in
  "$repo_root"|"$repo_root"/*)
    echo "site directory must stay outside the checkout" >&2
    exit 1
    ;;
esac
rm -rf "$SITE_DIR"
mkdir -p "$SITE_DIR"

set_output() {
  if [ -n "${GITHUB_OUTPUT:-}" ]; then
    printf '%s=%s\n' "$1" "$2" >> "$GITHUB_OUTPUT"
  else
    printf '%s=%s\n' "$1" "$2"
  fi
}

assert_checkout_unchanged() {
  local branch head
  branch=$(git branch --show-current)
  head=$(git rev-parse HEAD)
  if [ "$branch" != "$primary_branch" ] || [ "$head" != "$primary_head" ]; then
    echo "refusing to continue because the checkout moved" >&2
    exit 1
  fi
}

# Exit 0 when the branch exists, 1 when it does not, and abort on any other error.
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

html_escape() {
  local value=$1
  value=${value//&/&amp;}
  value=${value//</&lt;}
  value=${value//>/&gt;}
  value=${value//\"/&quot;}
  printf '%s' "$value"
}

json_string() {
  local file=$1 key=$2 regex=$3
  [ -f "$file" ] || return 0
  sed -n "s/.*\"${key}\":\"\\(${regex}\\)\".*/\\1/p" "$file"
}

has_html() {
  local found
  [ -d "$1" ] || return 1
  found=$(find "$1" -type f -name '*.html' -print -quit || true)
  [ -n "$found" ]
}

install_report() {
  local src=$1 dest=$2
  has_html "$src" || return 1
  rm -rf "$dest"
  mkdir -p "$dest"
  cp -a "$src"/. "$dest"/
}

report_link() {
  local folder=$1 title=$2 missing=$3
  local dir="$run_dir/$folder"
  local rel=""
  if [ ! -d "$dir" ]; then
    printf '<li>%s</li>\n' "$missing"
    return 0
  fi
  if [ -f "$dir/index.html" ]; then
    rel="index.html"
  else
    rel=$(find "$dir" -type f -name index.html -print -quit || true)
    rel=${rel#"$dir"/}
  fi
  if [[ -n "$rel" && "$rel" =~ ^[A-Za-z0-9._/-]+$ && "$rel" != *..* ]]; then
    printf '<li><a href="%s/%s">%s</a></li>\n' "$folder" "$rel" "$title"
  else
    printf '<li><a href="%s/">%s</a></li>\n' "$folder" "$title"
  fi
}

collect_ids() {
  local path name
  while IFS= read -r path; do
    [ -n "$path" ] || continue
    name=$(basename "$path")
    if [[ "$name" =~ ^[0-9]+$ ]]; then
      printf '%s\n' "$name"
    fi
  done < <(find "$SITE_DIR/runs" -mindepth 1 -maxdepth 1 -type d | sort)
}

# Newest first. Always keeps RUN_ID. Deletes older numeric folders past the limit.
prune_runs() {
  local limit=$1
  local -a asc=()
  local name id
  while IFS= read -r name; do
    [ -n "$name" ] || continue
    asc+=("$name")
  done < <(collect_ids | sort -n)

  local -a keep=()
  for ((i=${#asc[@]} - 1; i >= 0; i--)); do
    id=${asc[$i]}
    if [ "${#keep[@]}" -lt "$limit" ] || [ "$id" = "$RUN_ID" ]; then
      keep+=("$id")
    else
      rm -rf "$SITE_DIR/runs/$id"
    fi
  done

  if [ "${#keep[@]}" -gt "$limit" ]; then
    local -a trimmed=()
    local dropped=0
    for ((i=${#keep[@]} - 1; i >= 0; i--)); do
      id=${keep[$i]}
      if [ "$dropped" -eq 0 ] && [ "$id" != "$RUN_ID" ]; then
        rm -rf "$SITE_DIR/runs/$id"
        dropped=1
        continue
      fi
      trimmed+=("$id")
    done
    if [ "${#trimmed[@]}" -eq 0 ]; then
      keep=()
    else
      keep=("${trimmed[@]}")
    fi
  fi

  if [ "${#keep[@]}" -eq 0 ]; then
    return 0
  fi
  printf '%s\n' "${keep[@]}" | sort -nr
}

reject_secrets() {
  if [ -e "$SITE_DIR/.git" ] || [ -d "$SITE_DIR/.github" ]; then
    echo "refusing to publish repository metadata" >&2
    exit 1
  fi
  if grep -R -q -I -F \
    -e 'C:\Users\drpar' \
    -e 'C:/Users/drpar' \
    -e 'D:\Android' \
    -e 'D:/Android' \
    "$SITE_DIR"; then
    echo "::error::Pages site contains a personal Windows path" >&2
    exit 1
  fi
  if grep -R -q -I -E \
    -e 'ghp_[A-Za-z0-9]' \
    -e 'github_pat_' \
    -e 'gho_[A-Za-z0-9]' \
    -e 'AKIA[0-9A-Z]{16}' \
    -e 'BEGIN [A-Z ]*PRIVATE KEY' \
    -e 'x-access-token' \
    -e 'users\.noreply\.github\.com' \
    "$SITE_DIR"; then
    echo "::error::Pages site contains a secret-like string" >&2
    exit 1
  fi
}

if remote_has_history; then
  # The leading + updates only the local remote-tracking ref. The later push is a normal fast-forward.
  git fetch --depth=1 origin '+refs/heads/report-history:refs/remotes/origin/report-history'
  if git cat-file -e "origin/report-history:runs" 2>/dev/null; then
    git archive origin/report-history runs | tar -x -C "$SITE_DIR"
  fi
else
  echo "origin/report-history does not exist yet" >&2
fi
mkdir -p "$SITE_DIR/runs"

if ! has_html "$ALLURE_DIR" && ! has_html "$GRADLE_DIR"; then
  echo "Both report artifacts are missing; skipping Pages deploy." >&2
  rm -rf "$SITE_DIR"
  set_output deploy false
  assert_checkout_unchanged
  exit 0
fi

run_dir="$SITE_DIR/runs/$RUN_ID"
rm -rf "$run_dir"
mkdir -p "$run_dir"

if install_report "$ALLURE_DIR" "$run_dir/allure"; then
  echo "included Allure report" >&2
else
  echo "Allure report is missing; publishing the run without it" >&2
fi
if install_report "$GRADLE_DIR" "$run_dir/gradle"; then
  echo "included Gradle report" >&2
else
  echo "Gradle report is missing; publishing the run without it" >&2
fi

timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
if [[ ! "$timestamp" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]]; then
  echo "clock did not return a UTC timestamp" >&2
  exit 1
fi
printf '{"run_id":"%s","sha":"%s","timestamp":"%s"}\n' "$RUN_ID" "$RUN_SHA" "$timestamp" > "$run_dir/meta.json"

actions_href=""
if [[ "${REPOSITORY:-}" =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ && "${SERVER_URL:-}" == "https://github.com" ]]; then
  actions_href="${SERVER_URL}/${REPOSITORY}/actions/runs/${RUN_ID}"
fi
if [ -n "$actions_href" ]; then
  actions_li="<li><a href=\"$(html_escape "$actions_href")\">Log w Actions</a></li>"
else
  actions_li="<li>Log w Actions jest niedostępny.</li>"
fi
allure_li=$(report_link "allure" "Raport Allure" "Brak raportu Allure w tym uruchomieniu.")
gradle_li=$(report_link "gradle" "Raport Gradle" "Brak raportu Gradle w tym uruchomieniu.")

cat > "$run_dir/index.html" <<EOF
<!DOCTYPE html>
<html lang="pl">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Uruchomienie ${RUN_ID}</title>
  <style>
    body { font-family: system-ui, sans-serif; margin: 2rem; color: #1f2328; line-height: 1.45; }
    a { color: #0969da; }
    code { font-family: ui-monospace, monospace; }
  </style>
</head>
<body>
  <p><a href="../../">Wszystkie uruchomienia</a></p>
  <h1>Uruchomienie ${RUN_ID}</h1>
  <ul>
    <li>Identyfikator: ${RUN_ID}</li>
    <li>Commit: <code>${RUN_SHA}</code></li>
    ${actions_li}
    ${allure_li}
    ${gradle_li}
  </ul>
</body>
</html>
EOF

while IFS= read -r path; do
  [ -n "$path" ] || continue
  name=$(basename "$path")
  if [[ ! "$name" =~ ^[0-9]+$ ]]; then
    rm -rf "$path"
  fi
done < <(find "$SITE_DIR/runs" -mindepth 1 -maxdepth 1 -type d | sort)

newest=$(prune_runs "$KEEP_RUNS")
rows=""
first=1
while IFS= read -r id; do
  [ -n "$id" ] || continue
  meta="$SITE_DIR/runs/$id/meta.json"
  sha=$(json_string "$meta" sha '[0-9a-fA-F]\{7,40\}')
  when=$(json_string "$meta" timestamp '[0-9]\{4\}-[0-9]\{2\}-[0-9]\{2\}T[0-9]\{2\}:[0-9]\{2\}:[0-9]\{2\}Z')
  if [ -n "$sha" ]; then
    sha_cell="<code>$(html_escape "$sha")</code>"
  else
    sha_cell="—"
  fi
  if [ -n "$when" ]; then
    when_cell=$(html_escape "$when")
  else
    when_cell="—"
  fi
  label=$(html_escape "$id")
  klass=""
  if [ "$first" -eq 1 ]; then
    klass=' class="newest"'
    label="${label} (najnowsze)"
    first=0
  fi
  rows="${rows}<tr${klass}><td><a href=\"runs/${id}/\">${label}</a></td><td>${sha_cell}</td><td>${when_cell}</td></tr>"$'\n'
done <<< "$newest"

cat > "$SITE_DIR/index.html" <<EOF
<!DOCTYPE html>
<html lang="pl">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Raporty testów Appium</title>
  <style>
    body { font-family: system-ui, sans-serif; margin: 2rem; color: #1f2328; line-height: 1.45; }
    table { border-collapse: collapse; width: 100%; max-width: 52rem; }
    th, td { text-align: left; padding: 0.55rem 0.75rem; border-bottom: 1px solid #d0d7de; vertical-align: top; }
    tr.newest td { background: #dafbe1; font-weight: 600; }
    a { color: #0969da; }
    code { font-family: ui-monospace, monospace; font-weight: 400; }
  </style>
</head>
<body>
  <h1>Raporty testów</h1>
  <p>Wybierz uruchomienie z gałęzi main. Lista ma co najwyżej ${KEEP_RUNS} pozycji, od najnowszej. Najnowsze uruchomienie jest wyróżnione.</p>
  <table>
    <thead>
      <tr><th>Uruchomienie</th><th>Commit</th><th>Czas</th></tr>
    </thead>
    <tbody>
${rows}
    </tbody>
  </table>
</body>
</html>
EOF

reject_secrets
if [[ "$SITE_DIR" == *$'\n'* || "$SITE_DIR" == *'='* ]]; then
  echo "site directory cannot be passed as an action output" >&2
  exit 1
fi
echo "Prepared ${SITE_DIR} for run ${RUN_ID}" >&2
set_output deploy true
set_output site_dir "$SITE_DIR"
assert_checkout_unchanged
