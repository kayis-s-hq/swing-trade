#!/bin/bash
# Source .env and run the API with local profile
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
set -a
source "$PROJECT_ROOT/infra/env/.env"
set +a
cd "$PROJECT_ROOT/backend"
source "$HOME/.sdkman/bin/sdkman-init.sh"
./gradlew :api:bootRun --args='--spring.profiles.active=local'