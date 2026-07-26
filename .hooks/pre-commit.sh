#!/bin/bash
echo "---- Pre-Commit Hooks ----"

# Get staged files
staged_files=$(git diff --cached --name-only --diff-filter=ACM)

if [ -z "$staged_files" ]; then
    echo "No staged files — skipping pre-commit checks."
    exit 0
fi

# Check if any Java/module files are involved
java_files=()
pom_files=()
for f in $staged_files; do
    if [[ "$f" == *.java ]]; then
        java_files+=("$f")
    elif [[ "$f" == */pom.xml ]]; then
        pom_files+=("$f")
    fi
done

# Check if any frontend files are involved
vue_files=()
ts_files=()
for f in $staged_files; do
    if [[ "$f" == *.vue ]]; then
        vue_files+=("$f")
    elif [[ "$f" == *.ts ]]; then
        ts_files+=("$f")
    fi
done

# ---- Backend: checkstyle on changed modules ----
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

if [ ${#java_files[@]} -gt 0 ] || [ ${#pom_files[@]} -gt 0 ]; then
    if [ ${#modules_to_check[@]} -eq 0 ]; then
        echo "No backend module changes — skipping backend checks."
    else
        # Deduplicate
        readarray -t modules_to_check < <(printf '%s\n' "${modules_to_check[@]}" | sort -u)
        echo "Modules with changes: ${modules_to_check[*]}"

        # Source Java 21
        source "$HOME/.sdkman/bin/sdkman-init.sh"

        # Build module list for Maven
        module_args=""
        for m in "${modules_to_check[@]}"; do
            module_args="$module_args -pl :$m"
        done

        echo "Running checkstyle on: ${modules_to_check[*]}"
        mvn clean checkstyle:check $module_args -f backend -B
        checkstyle_exit=$?

        if [ $checkstyle_exit -ne 0 ]; then
            echo "ERROR: checkstyle failed. Fix violations before committing."
            exit 1
        fi
    fi
fi

# ---- Frontend: ESLint on staged Vue/TS files ----
if [ ${#vue_files[@]} -gt 0 ] || [ ${#ts_files[@]} -gt 0 ]; then
    echo "Running ESLint on staged frontend files..."
    cd dashboard

    # Collect all staged frontend files
    frontend_files=""
    for f in "${vue_files[@]}" "${ts_files[@]}"; do
        frontend_files="$frontend_files $f"
    done

    npx eslint --fix --no-warn-ignored --quiet $frontend_files 2>&1
    eslint_exit=$?

    if [ $eslint_exit -ne 0 ]; then
        echo "ERROR: ESLint found errors (not warnings). Fix before committing."
        exit 1
    fi

    # Re-stage any files modified by --fix
    git add $frontend_files

    echo "ESLint passed."
fi

exit 0