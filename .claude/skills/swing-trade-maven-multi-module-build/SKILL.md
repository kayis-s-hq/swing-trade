---
name: swing-trade-maven-multi-module-build
description: Build and test specific modules with dependency resolution for the swing-trade Maven multi-module project. Use this skill when modifying code in specific modules, running targeted tests, building for deployment, or when resolving module dependency issues. This skill is essential for efficient development and should be triggered proactively when working on module-specific changes or when the full build is too slow.
---

# SwingTrade Maven Multi-Module Build Skill

## Overview

This skill automates Maven multi-module build operations for the swing-trade system. It handles dependency resolution, targeted builds, and efficient testing of specific modules.

## When to Use This Skill

Trigger this skill when:
- Modifying code in a specific module
- Running targeted tests for a module
- Building for deployment
- Resolving module dependency issues
- When full build is too slow
- Before committing module-specific changes

## Module Structure

```
swing-trade (parent pom)
├── core           # Domain models - No dependencies
├── data           # Data ingestion - Depends on: core
├── strategy       # Technical analysis - Depends on: core
├── llm            # LLM integration - Depends on: core
├── broker         # Trading engine - Depends on: core, strategy, llm
└── api            # REST API - Depends on: all modules
```

## Build Commands

### Full Build

```bash
# Clean and install all modules
mvn clean install

# Skip tests
mvn clean install -DskipTests

# With coverage
mvn clean test jacoco:report -Pcoverage
```

### Module-Specific Build

```bash
# Build single module
cd core && mvn clean install
cd ../data && mvn clean install
cd ../strategy && mvn clean install
cd ../llm && mvn clean install
cd ../broker && mvn clean install
cd ../api && mvn clean install

# Build module with dependencies
mvn clean install -pl data -am  # -am: also build dependencies
```

### Module-Specific Test

```bash
# Test single module
mvn test -pl core

# Test with specific class
mvn test -pl strategy -Dtest=TechnicalIndicatorsTest

# Test with specific method
mvn test -pl broker -Dtest=RiskControlsServiceTest#testDailyLossCircuitBreaker

# Test with coverage
mvn test -pl api -Pcoverage
```

## Dependency Resolution

### Common Dependency Issues

```bash
# Check dependency tree
mvn dependency:tree -pl strategy

# Find conflicting dependencies
mvn dependency:analyze -pl api

# Resolve missing dependencies
mvn dependency:resolve -pl broker

# Clean local Maven cache for specific module
rm -rf ~/.m2/repository/com/swingtrade/[module-name]
```

### Dependency Management

```xml
<!-- Parent pom: dependency management -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.0</version>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-bom</artifactId>
            <version>1.19.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Build Profiles

### Development Profile

```bash
# Build with dev profile
mvn clean install -Pdev

# Includes:
# - Debug logging
# - Test data generation
# - Local database connection
```

### Coverage Profile

```bash
# Build with coverage
mvn clean test jacoco:report -Pcoverage

# Includes:
# - JaCoCo instrumentation
# - Coverage reporting
# - Threshold enforcement (80%)
```

### Production Profile

```bash
# Build with production profile
mvn clean package -Pprod

# Includes:
# - Minified resources
# - Optimized logging
# - Production configuration
```

## Build Optimization

### Parallel Build

```bash
# Build all modules in parallel
mvn clean install --batch-mode -T 1C

# Build with 4 threads
mvn clean install --batch-mode -T 4
```

### Incremental Build

```bash
# Only build changed modules
mvn clean install -Dmaven.test.skip=true

# Skip specific modules
mvn clean install -pl '!core'  # Skip core module
```

## Build Report Format

```
# Maven Build Report - [Timestamp]

## Build Summary
- Modules Built: [X]/[X]
- Build Time: [X] minutes
- Test Success Rate: [X]%

## Module Build Status
| Module | Status | Time | Tests | Coverage |
|--------|--------|------|-------|----------|
| core | SUCCESS | 15s | 6/6 | N/A |
| data | SUCCESS | 45s | 5/5 | 85% |
| strategy | SUCCESS | 30s | 4/4 | 78% |
| llm | SUCCESS | 50s | 6/6 | 82% |
| broker | SUCCESS | 60s | 9/9 | 88% |
| api | SUCCESS | 90s | 11/11 | 90% |

## Test Results
- Total Tests: [X]
- Passed: [X]
- Failed: [X]
- Skipped: [X]

## Dependency Analysis
- Conflicts Found: [X]
- Unused Dependencies: [X]
- Missing Dependencies: [X]

## Recommendations
1. [Optimization suggestions]
2. [Dependency cleanup]
3. [Test improvements]
```

## Example Usage

```bash
# Full build
/skill: swing-trade-maven-multi-module-build

# Build specific module
/skill: swing-trade-maven-multi-module-build --module strategy

# Build with dependencies
/skill: swing-trade-maven-multi-module-build --module broker --with-deps

# Run tests only
/skill: swing-trade-maven-multi-module-build --tests --module api

# Build with coverage
/skill: swing-trade-maven-multi-module-build --coverage

# Generate build report
/skill: swing-trade-maven-multi-module-build --report

# Check dependency tree
/skill: swing-trade-maven-multi-module-build --dependencies --module llm
```

## Dependencies

- Maven 3.8+
- Java 21
- Local Maven repository
- Module dependency graph

## Performance Considerations

- Use parallel builds for faster execution
- Cache Maven dependencies in CI/CD
- Use incremental builds for development
- Skip tests during rapid iteration
