---
phase: 05-testing-foundation
plan: 06
type: execute
wave: 6
depends_on:
  - 05-05
files_modified:
  - pom.xml (update JaCoCo configuration)
  - core/pom.xml
  - strategy/pom.xml
  - data/pom.xml
  - llm/pom.xml
  - broker/pom.xml
  - api/pom.xml
  - .planning/REQUIREMENTS.md (update REQ-106 status)
autonomous: true
requirements:
  - REQ-106
user_setup:
  - service: "JaCoCo Configuration"
    why: "No additional setup required - Maven plugin handles coverage"
    env_vars: []
    dashboard_config: []


# WORKTREE WORKFLOW ENFORCEMENT
# CRITICAL: This plan MUST be executed in a git worktree environment
# Root .planning/ is source of truth - worktree .planning/ is working copy
worktree_enforcement:
  required: true
  reason: "Prevents direct edits to root .planning/ on main branch"
  workflow:
    - step: 1
      action: "Verify worktree directory"
      command: "pwd | grep worktrees"
      fail_message: "ERROR: Must be in a worktree directory (e.g., .claude/worktrees/phase-05/)"
    - step: 2
      action: "Verify worktree branch"
      command: "git branch --show-current"
      expected_pattern: "worktree-phase-.*"
      fail_message: "ERROR: Must be on a worktree branch (e.g., worktree-phase-05)"
    - step: 3
      action: "Edit planning docs in worktree"
      path: ".planning/phases/05-testing-foundation/"
      note: "Do NOT edit .planning/ in root repository"
    - step: 4
      action: "Sync to root before merge"
      command: "Skill(\"superpowers:gsd-worktree-workflow --sync-to-root\")"
      when: "Before merging worktree branch to main"
    - step: 5
      action: "Verify before merge"
      command: "gsd:verify"
      when: "After plan completion, before merge"

must_haves:
  truths:
    - JaCoCo Maven profile `coverage` runs with `mvn clean install -P coverage`
    - Coverage report generated in target/site/jacoco
    - Module-specific thresholds configured (core 100%, strategy 85%, others 80%)
    - Build fails when thresholds not met
    - All existing tests contribute to coverage calculation
  artifacts:
    - path: "pom.xml"
      provides: "Updated JaCoCo parent configuration"
      min_lines: 50
    - path: "core/pom.xml"
      provides: "Core module JaCoCo configuration (100% threshold)"
      min_lines: 20
    - path: "strategy/pom.xml"
      provides: "Strategy module JaCoCo configuration (85% threshold)"
      min_lines: 20
    - path: "data/pom.xml"
      provides: "Data module JaCoCo configuration (80% threshold)"
      min_lines: 20
    - path: "llm/pom.xml"
      provides: "LLM module JaCoCo configuration (75% threshold)"
      min_lines: 20
    - path: "broker/pom.xml"
      provides: "Broker module JaCoCo configuration (80% threshold)"
      min_lines: 20
    - path: "api/pom.xml"
      provides: "API module JaCoCo configuration (75% threshold)"
      min_lines: 20
  key_links:
    - from: "pom.xml"
      to: "core/pom.xml, strategy/pom.xml, etc."
      via: "JaCoCo plugin configuration with module-specific thresholds"
      pattern: "jacoco.*threshold|coverage.*profile"
---

<objective>
Configure JaCoCo code coverage with Maven profiles, module-specific thresholds, and build failure on threshold violations.
</objective>

<execution_context>
@/Users/kayisrahman/.claude/get-shit-done/workflows/execute-plan.md
@/Users/kayisrahman/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/REQUIREMENTS.md (REQ-106)
@.planning/ROADMAP.md (Phase 5 goal)
@05-01-core-domain-unit-tests-SUMMARY.md
@05-02-strategy-unit-tests-SUMMARY.md
@05-03-integration-test-infrastructure-SUMMARY.md
@05-04-http-mocking-with-MockRestServiceServer-SUMMARY.md
@05-05-api-endpoint-tests-SUMMARY.md

# Target Coverage Thresholds
<!-- Module-specific thresholds per requirements -->

| Module | Target Coverage | Reason |
|--------|-----------------|--------|
| core | 100% | Domain models are critical, pure logic, no external dependencies |
| strategy | 85%+ | Complex technical indicators, TA4J integration |
| data | 80%+ | Repository layer, TestContainers integration |
| llm | 75%+ | External LLM dependency, mocking expected |
| broker | 80%+ | Trading logic, risk controls |
| api | 75%+ | Controller layer, external dependencies mocked |

# JaCoCo Maven Profile Pattern
<!-- How to configure module-specific thresholds -->

```xml
<profiles>
    <profile>
        <id>coverage</id>
        <activation>
            <activeByDefault>false</activeByDefault>
        </activation>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.jacoco</groupId>
                    <artifactId>jacoco-maven-plugin</artifactId>
                    <version>0.8.12</version>
                    <executions>
                        <execution>
                            <id>prepare-agent</id>
                            <goals>
                                <goal>prepare-agent</goal>
                            </goals>
                        </execution>
                        <execution>
                            <id>report</id>
                            <phase>test</phase>
                            <goals>
                                <goal>report</goal>
                            </goals>
                        </execution>
                        <execution>
                            <id>check</id>
                            <goals>
                                <goal>check</goal>
                            </goals>
                            <configuration>
                                <rules>
                                    <rule>
                                        <element>BUNDLE</element>
                                        <limits>
                                            <limit>
                                                <counter>LINE</counter>
                                                <value>COVEREDRATIO</value>
                                                <minimum>0.80</minimum>
                                            </limit>
                                        </limits>
                                    </rule>
                                </rules>
                            </configuration>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```
</context>

<tasks>

<task type="auto">
  <name>Task 1: Update parent pom.xml with coverage profile</name>
  <files>pom.xml</files>
  <action>
Update parent pom.xml JaCoCo configuration:

1. Remove existing excludes that skip domain models (we WANT 100% on core)
2. Update coverage profile with module-specific thresholds

```xml
<!-- In dependencyManagement, keep existing -->

<!-- In build/pluginManagement, update jacoco-maven-plugin: -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>${jacoco.plugin.version}</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>

<!-- In profiles section, add coverage profile with module thresholds: -->
<profile>
    <id>coverage</id>
    <properties>
        <jacoco.ut.skip>false</jacoco.ut.skip>
    </properties>
    <build>
        <plugins>
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>${jacoco.plugin.version}</version>
                <executions>
                    <execution>
                        <id>prepare-agent</id>
                        <goals>
                            <goal>prepare-agent</goal>
                        </goals>
                    </execution>
                    <execution>
                        <id>report</id>
                        <phase>test</phase>
                        <goals>
                            <goal>report</goal>
                        </goals>
                    </execution>
                    <execution>
                        <id>check</id>
                        <goals>
                            <goal>check</goal>
                        </goals>
                        <configuration>
                            <rules>
                                <!-- Override in child modules with module-specific thresholds -->
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</profile>
```
</action>
  <verify>
    <automated>grep -A30 "<profile>" pom.xml | grep -A30 "coverage"</automated>
  </verify>
  <done>Parent pom.xml coverage profile updated with prepare-agent and report executions</done>
</task>

<task type="auto">
  <name>Task 2: Configure core module JaCoCo (100% threshold)</name>
  <files>core/pom.xml</files>
  <action>
Add JaCoCo plugin to core/pom.xml:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>${jacoco.plugin.version}</version>
            <executions>
                <execution>
                    <id>prepare-agent</id>
                    <goals>
                        <goal>prepare-agent</goal>
                    </goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
                <execution>
                    <id>check</id>
                    <goals>
                        <goal>check</goal>
                    </goals>
                    <configuration>
                        <rules>
                            <rule>
                                <element>BUNDLE</element>
                                <limits>
                                    <limit>
                                        <counter>LINE</counter>
                                        <value>COVEREDRATIO</value>
                                        <minimum>1.00</minimum>  <!-- 100% for core -->
                                    </limit>
                                    <limit>
                                        <counter>BRANCH</counter>
                                        <value>COVEREDRATIO</value>
                                        <minimum>1.00</minimum>  <!-- 100% branch coverage -->
                                    </limit>
                                </limits>
                            </rule>
                        </rules>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```
</action>
  <verify>
    <automated>grep -A50 "<artifactId>core</artifactId>" pom.xml && grep -A30 "jacoco" core/pom.xml</automated>
  </verify>
  <done>core/pom.xml configured with 100% line and branch coverage threshold</done>
</task>

<task type="auto">
  <name>Task 3: Configure strategy module JaCoCo (85% threshold)</name>
  <files>strategy/pom.xml</files>
  <action>
Add JaCoCo plugin to strategy/pom.xml:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>${jacoco.plugin.version}</version>
            <executions>
                <execution>
                    <id>prepare-agent</id>
                    <goals>
                        <goal>prepare-agent</goal>
                    </goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
                <execution>
                    <id>check</id>
                    <goals>
                        <goal>check</goal>
                    </goals>
                    <configuration>
                        <rules>
                            <rule>
                                <element>BUNDLE</element>
                                <limits>
                                    <limit>
                                        <counter>LINE</counter>
                                        <value>COVEREDRATIO</value>
                                        <minimum>0.85</minimum>  <!-- 85% for strategy -->
                                    </limit>
                                </limits>
                            </rule>
                        </rules>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```
</action>
  <verify>
    <automated>grep -A30 "jacoco" strategy/pom.xml</automated>
  </verify>
  <done>strategy/pom.xml configured with 85% line coverage threshold</done>
</task>

<task type="auto">
  <name>Task 4: Configure data module JaCoCo (80% threshold)</name>
  <files>data/pom.xml</files>
  <action>
Add JaCoCo plugin to data/pom.xml:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>${jacoco.plugin.version}</version>
            <executions>
                <execution>
                    <id>prepare-agent</id>
                    <goals>
                        <goal>prepare-agent</goal>
                    </goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
                <execution>
                    <id>check</id>
                    <goals>
                        <goal>check</goal>
                    </goals>
                    <configuration>
                        <rules>
                            <rule>
                                <element>BUNDLE</element>
                                <limits>
                                    <limit>
                                        <counter>LINE</counter>
                                        <value>COVEREDRATIO</value>
                                        <minimum>0.80</minimum>  <!-- 80% for data -->
                                    </limit>
                                </limits>
                            </rule>
                        </rules>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```
</action>
  <verify>
    <automated>grep -A30 "jacoco" data/pom.xml</automated>
  </verify>
  <done>data/pom.xml configured with 80% line coverage threshold</done>
</task>

<task type="auto">
  <name>Task 5: Configure llm, broker, api modules JaCoCo</name>
  <files>
    llm/pom.xml,
    broker/pom.xml,
    api/pom.xml
  </files>
  <action>
Add JaCoCo plugins to llm, broker, and api modules:

**llm/pom.xml (75% threshold):**
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>${jacoco.plugin.version}</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.75</minimum>  <!-- 75% for llm -->
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**broker/pom.xml (80% threshold):**
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>${jacoco.plugin.version}</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>  <!-- 80% for broker -->
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**api/pom.xml (75% threshold):**
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>${jacoco.plugin.version}</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.75</minimum>  <!-- 75% for api -->
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```
</action>
  <verify>
    <automated>grep -c "jacoco" llm/pom.xml broker/pom.xml api/pom.xml</automated>
  </verify>
  <done>llm/pom.xml (75%), broker/pom.xml (80%), api/pom.xml (75%) configured</done>
</task>

<task type="auto">
  <name>Task 6: Verify JaCoCo profile runs correctly</name>
  <files>[]</files>
  <action>
Verify JaCoCo configuration by running coverage profile:

```bash
# Run with coverage profile
mvn clean install -P coverage

# Generate report
cd target/site/jacoco
open index.html  # Opens coverage report
```

Expected outcomes:
- All tests run
- Coverage report generated
- Build succeeds if thresholds met
- Build fails with clear message if thresholds not met
</action>
  <verify>
    <automated>mvn clean install -P coverage -DskipTests=false 2>&1 | grep -A10 "JaCoCo"</automated>
  </verify>
  <done>Coverage profile verified, report generated in target/site/jacoco</done>
</task>

<task type="auto">
  <name>Task 7: Update REQUIREMENTS.md with REQ-106 completion</name>
  <files>.planning/REQUIREMENTS.md</files>
  <action>
Update REQUIREMENTS.md Phase 5 section:

```markdown
### REQ-106: Code Coverage Target (80%+)

- [x] core: 100% (configured in core/pom.xml)
- [x] strategy: 85%+ (configured in strategy/pom.xml)
- [x] data: 80%+ (configured in data/pom.xml)
- [x] llm: 75%+ (configured in llm/pom.xml)
- [x] broker: 80%+ (configured in broker/pom.xml)
- [x] api: 75%+ (configured in api/pom.xml)
- [x] Tool: JaCoCo with Maven profile `coverage`
- [x] Build: Threshold violations fail build
- **Files:** Updated pom.xml files
- **Status:** ✅ Complete
```
</action>
  <verify>
    <automated>grep -A10 "REQ-106" .planning/REQUIREMENTS.md</automated>
  </verify>
  <done>REQUIREMENTS.md updated with REQ-106 completion status</done>
</task>

</tasks>

<verification>
Overall checks:
1. Run `mvn clean install -P coverage` to verify all modules pass coverage
2. Check `target/site/jacoco/index.html` for coverage report
3. Verify module-specific thresholds in each pom.xml
4. Ensure build fails when thresholds not met (test by temporarily lowering threshold)
</verification>

<success_criteria>
- JaCoCo configured in all 6 modules with correct thresholds
- core: 100% line + branch coverage
- strategy: 85% line coverage
- data: 80% line coverage
- llm: 75% line coverage
- broker: 80% line coverage
- api: 75% line coverage
- Coverage profile runs with `mvn clean install -P coverage`
- Coverage report generated in target/site/jacoco
- Build fails when thresholds not met
- REQUIREMENTS.md updated
</success_criteria>

<output>
After completion, create `.planning/phases/05-testing-foundation/05-06-jacoco-coverage-configuration-SUMMARY.md`
</output>
