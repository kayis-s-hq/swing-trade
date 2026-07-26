#!/bin/bash
echo "---- Pre Push Hooks ----"
echo $(pwd)

# Identify what's being pushed
remote="$1"
ref="$2"

# Resolve local refs being pushed
local_refs=$(git for-each-ref --format='%(refname)' refs/heads/)
remote_refs=$(git ls-remote "$remote" 2>/dev/null | awk '{print $2}')

# Find which local branches are being pushed
changed_files=()

for local_ref in $local_refs; do
    local_branch=$(git name-rev --name-only "$local_ref" 2>/dev/null | sed 's|remotes/[^/]*/||')
    for remote_ref in $remote_refs; do
        if echo "$remote_ref" | grep -q "^refs/heads/$local_branch$"; then
            remote_sha=$(git ls-remote "$remote" refs/heads/$local_branch 2>/dev/null | awk '{print $1}')
            if [ -n "$remote_sha" ]; then
                changed_files+=($(git diff --name-only "$remote_sha" "$local_ref" 2>/dev/null))
            fi
            break
        fi
    done
done

# Fallback: if no remote found, compare against HEAD
if [ ${#changed_files[@]} -eq 0 ]; then
    changed_files+=($(git diff --name-only HEAD~1 2>/dev/null))
fi

# Check if any Java/module files are involved
java_files=()
pom_files=()
for f in "${changed_files[@]}"; do
    if [[ "$f" == *.java ]]; then
        java_files+=("$f")
    elif [[ "$f" == */pom.xml ]]; then
        pom_files+=("$f")
    fi
done

if [ ${#java_files[@]} -eq 0 ] && [ ${#pom_files[@]} -eq 0 ]; then
    echo "No Java/module changes detected — skipping pre-push checks."
    exit 0
fi

# Determine which backend modules have changes
modules_to_check=()
for f in "${java_files[@]}" "${pom_files[@]}"; do
    if [[ "$f" == backend/core/* ]]; then
        modules_to_check+=("core")
    elif [[ "$f" == backend/data/* ]]; then
        modules_to_check+=("data")
    elif [[ "$f" == backend/strategy/* ]]; then
        modules_to_check+=("strategy")
    elif [[ "$f" == backend/llm/* ]]; then
        modules_to_check+=("llm")
    elif [[ "$f" == backend/broker/* ]]; then
        modules_to_check+=("broker")
    elif [[ "$f" == backend/api/* ]]; then
        modules_to_check+=("api")
    elif [[ "$f" == "backend/pom.xml" ]]; then
        modules_to_check=("core" "data" "strategy" "llm" "broker" "api")
        break
    fi
done

# Deduplicate
if [ ${#modules_to_check[@]} -gt 0 ]; then
    readarray -t modules_to_check < <(printf '%s\n' "${modules_to_check[@]}" | sort -u)
    echo "Modules with changes: ${modules_to_check[*]}"
fi

# Source Java 21
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Run PMD + checkstyle only on changed modules
if [ ${#modules_to_check[@]} -eq 0 ]; then
    echo "No backend module changes — skipping pre-push checks."
    exit 0
fi

# Build comma-separated module list for Maven
module_args=""
for m in "${modules_to_check[@]}"; do
    module_args="$module_args -pl :$m"
done

echo "Running checkstyle on: ${modules_to_check[*]}"
mvn clean checkstyle:check $module_args -f backend