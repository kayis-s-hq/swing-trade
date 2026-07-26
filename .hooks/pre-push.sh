#!/bin/bash
echo "---- Pre Push Hooks ----"
echo $(pwd)
source "$HOME/.sdkman/bin/sdkman-init.sh"
mvn clean pmd:check -f backend