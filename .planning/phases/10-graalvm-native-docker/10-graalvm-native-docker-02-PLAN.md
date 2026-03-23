---
phase: 10-graalvm-native-docker
plan: 02
type: execute
wave: 1
depends_on: []
files_modified: [pom.xml, api/pom.xml]
autonomous: true
requirements: [REQ-202, REQ-204]

must_haves:
  truths:
    - "Maven native profile builds GraalVM native executable"
    - "Spring Boot native auto-configuration generates required metadata"
    - "Native build completes successfully with all modules"
  artifacts:
    - path: "pom.xml"
      provides: "Parent POM with Spring Native BOM and GraalVM properties"
      min_lines: 0
    - path: "api/pom.xml"
      provides: "API module with native-image goal and native profile"
      min_lines: 0
  key_links:
    - from: "api/pom.xml"
      to: "pom.xml"
      via: "spring-native.version property"
      pattern: "spring-native.version"
---

<objective>
Configure Maven for GraalVM native compilation with Spring Boot 3.3.1

Purpose: Enable native image building via Maven profiles. Spring Boot 3.3.1 has built-in native support that auto-generates reflection and resource configuration. The native profile will skip tests during native build (as per user decision D-09: CI/CD only).

Output: Updated parent POM and API module POM with native compilation support
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/ROADMAP.md
@.planning/phases/10-graalvm-native-docker/10-graalvm-native-docker-CONTEXT.md
@.planning/phases/10-graalvm-native-docker/10-graalvm-native-docker-RESEARCH.md
@/Users/kayisrahman/Documents/workspace/ideas/swing-trade/pom.xml
@/Users/kayisrahman/Documents/workspace/ideas/swing-trade/api/pom.xml
@/Users/kayisrahman/Documents/workspace/ideas/swing-trade/api/src/main/java/com/swingtrade/api/SwingTradeApiApplication.java

<!-- Spring Boot native plugin configuration -->
<interfaces>
From api/pom.xml:
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>repackage</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Spring Boot 3.3.1 supports native-image goal without additional plugins.
Key dependencies for native:
- spring-boot-starter-web
- spring-boot-starter-actuator
- postgresql driver
- spring-boot-starter-data-redis
- langchain4j (may need reflection config)
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add Spring Native BOM and properties to parent POM</name>
  <files>pom.xml</files>
  <action>
Add Spring Native BOM and GraalVM version properties to parent pom.xml:

**Add to &lt;properties&gt; section:**
```xml
<!-- Spring Native version (must match Spring Boot 3.3.1) -->
<spring-native.version>1.3.1</spring-native.version>
```

**Add to &lt;dependencyManagement&gt; section:**
```xml
<dependency>
    <groupId>org.springframework.experimental</groupId>
    <artifactId>spring-native-bom</artifactId>
    <version>${spring-native.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

**Rationale:** Spring Native 1.3.1 is compatible with Spring Boot 3.3.1. The BOM manages transitive native dependencies.

Reference: RESEARCH.md Section 2.1, User decision D-03 (Standard Spring Boot native compilation)
</action>
  <verify>
  <automated>grep -q "spring-native.version" pom.xml && grep -q "spring-native-bom" pom.xml</automated>
</verify>
  <done>
  Parent pom.xml includes spring-native.version property (1.3.1) and spring-native-bom in dependencyManagement for consistent versioning across modules.
  </done>
</task>

<task type="auto">
  <name>Task 2: Configure spring-boot-maven-plugin with native-image goal in API module</name>
  <files>api/pom.xml</files>
  <action>
Update api/pom.xml build section to add native-image execution:

**Update spring-boot-maven-plugin:**
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>repackage</goal>
                <goal>native-image</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Add to build section:**
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>3.3.0</version>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Rationale:**
- native-image goal triggers GraalVM compilation
- failsafe-plugin for native integration tests
- Spring Boot 3.3.1 handles reflection/resource auto-configuration

Reference: RESEARCH.md Section 2.2, User decision D-03 (Standard Spring Boot native compilation)
</action>
  <verify>
  <automated>grep -q "native-image" api/pom.xml && grep -q "failsafe-plugin" api/pom.xml</automated>
</verify>
  <done>
  API module pom.xml configures spring-boot-maven-plugin with both repackage and native-image goals, plus failsafe-plugin for integration testing.
  </done>
</task>

<task type="auto">
  <name>Task 3: Add native Maven profile to API module</name>
  <files>api/pom.xml</files>
  <action>
Add native profile to api/pom.xml:

```xml
<profiles>
    <profile>
        <id>native</id>
        <activation>
            <activeByDefault>false</activeByDefault>
        </activation>
        <properties>
            <skipTests>true</skipTests>
            <skipITs>true</skipITs>
        </properties>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <configuration>
                        <imageName>swing-trade-api</imageName>
                        <classifier>native</classifier>
                        <buildArgs>
                            <buildArg>--gc=none</buildArg>
                            <buildArg>--strict-image-heap</buildArg>
                            <buildArg>-O3</buildArg>
                            <buildArg>--allow-incomplete-classpath</buildArg>
                            <buildArg>-H:Name=swing-trade-api</buildArg>
                            <buildArg>--initialize-at-build-time=org.springframework</buildArg>
                            <buildArg>--initialize-at-run-time=dev.langchain4j</buildArg>
                        </buildArgs>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

**BuildArgs explanation:**
- `--gc=none`: Disable GC for faster startup
- `--strict-image-heap`: Smaller memory footprint
- `-O3`: Maximum optimization
- `--allow-incomplete-classpath`: Tolerate some missing classes
- `-H:Name=swing-trade-api`: Output binary name
- `--initialize-at-build-time`: Pre-initialize Spring (faster startup)
- `--initialize-at-run-time`: LangChain4j needs runtime initialization (dynamic configs)

Reference: RESEARCH.md Section 9.1, User decision D-09 (Build native in CI/CD only)
</action>
  <verify>
  <automated>grep -A 30 "<id>native</id>" api/pom.xml | grep -q "buildArgs"</automated>
</verify>
  <done>
  API module has native profile that skips tests, configures GraalVM buildArgs for optimization, and produces swing-trade-api binary.
  </done>
</task>

</tasks>

<verification>
- Parent POM has spring-native.version property and BOM
- API module has native-image goal configured
- Native profile activates with -Pnative flag
- BuildArgs include optimization flags for startup and memory
- mvn clean package -Pnative completes successfully
</verification>

<success_criteria>
- [ ] pom.xml includes spring-native.version and spring-native-bom
- [ ] api/pom.xml has native-image goal in spring-boot-maven-plugin
- [ ] Native profile configured with optimization buildArgs
- [ ] Native profile skips tests (--skipTests=true)
- [ ] mvn clean package -Pnative produces swing-trade-api binary
- [ ] Binary is ELF executable (file command)
</success_criteria>

<output>
After completion, verify native build: `cd api && mvn clean package -Pnative -B && ls -lh target/*native`
</output>
