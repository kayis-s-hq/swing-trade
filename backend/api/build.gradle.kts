plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.graalvm.buildtools.native") version "0.10.6"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":strategy"))
    implementation(project(":llm"))
    // TODO: restore when GpuHubController has an auth guard (see its TODO)
    // implementation(project(":gpuhub"))
    implementation(project(":broker"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // Spring Boot's ImperativeHttpClientAutoConfiguration probes for a blocking HTTP client
    // implementation via ClientHttpRequestFactoryBuilder.detect() (tries Jetty, then
    // HttpComponents, then JDK). Without a real client on the classpath it still attempts the
    // Jetty branch and fails with NoClassDefFoundError (org.eclipse.jetty.http.HttpCookieStore)
    // even though Jetty isn't a declared dependency. Adding HttpComponents Client5 makes it the
    // preferred candidate so the Jetty branch is never attempted. Version is managed by the
    // spring-boot-dependencies BOM imported in the root build.gradle.kts.
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
    // developmentOnly (not implementation): Boot's Gradle plugin excludes this from the
    // packaged fat jar automatically. It was previously `implementation`, which shipped
    // devtools' classpath-watching Restarter inside api.jar itself - its restart trigger
    // fires on ANY classpath file change (including a concurrent `./gradlew` rebuild
    // touching build/classes/ while the jar is running) and tears down the running
    // ApplicationContext, which looked like an unexplained silent shutdown a few seconds
    // into every startup.
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Resilience4j — circuit breaker, retry, bulkhead, time limiter.
    // 2.4.0+ is required for the resilience4j-spring-boot4 autoconfiguration module.
    // No explicit Jetty pin here — Spring AI 2.0.1 (see llm/build.gradle.kts) uses OkHttp.
    implementation("io.github.resilience4j:resilience4j-spring-boot4:2.4.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.4.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.4.0")
    implementation("io.github.resilience4j:resilience4j-bulkhead:2.4.0")
    implementation("io.github.resilience4j:resilience4j-timelimiter:2.4.0")
    implementation("io.github.resilience4j:resilience4j-micrometer:2.4.0")

    implementation("io.micrometer:micrometer-core")
    implementation("io.micrometer:micrometer-registry-prometheus")

    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core:12.4.0")
    implementation("org.flywaydb:flyway-database-postgresql:12.4.0")
    // Spring Boot 4 modularized Flyway autoconfiguration out of spring-boot-autoconfigure
    // into its own artifact (org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer).
    // Raw flyway-core/flyway-database-postgresql alone do NOT trigger Flyway at startup —
    // this is the artifact that activates spring.flyway.* properties. Version managed by the
    // spring-boot-dependencies BOM imported in the root build.gradle.kts.
    implementation("org.springframework.boot:spring-boot-flyway")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.wiremock:wiremock:3.8.0")
    testImplementation("org.testcontainers:testcontainers:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("org.testcontainers:postgresql:1.21.3")
    testImplementation("com.h2database:h2")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.assertj:assertj-core")
}

springBoot {
    mainClass = "com.swingtrade.api.app.SwingTradeApiApplication"
    buildInfo()
}

// Copies the api module's full runtime classpath (all resolved dependency jars —
// Spring Boot, Hibernate, Postgres driver, fyersjavasdk, etc.) into build/runtimeDeps/.
// Used by infra/Dockerfile's jar-build stage to assemble /app/lib/ alongside the
// thin api-plain.jar produced by the `jar` task, since this project uses a plain
// jar + lib/ classpath layout instead of Spring Boot's bootJar.
tasks.register<Copy>("copyRuntimeDeps") {
    group = "build"
    description = "Copies the api module's runtime classpath jars into build/runtimeDeps/"
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("runtimeDeps"))
}

tasks.named("processTestAot").configure {
    enabled = false
}

graalvmNative {
    binaries {
        named("main") {
            imageName = "swing-trade-api"
            mainClass = "com.swingtrade.api.app.SwingTradeApiApplication"
            buildArgs.add("--report-unsupported-elements-at-runtime")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("-J-Xmx4g")
            buildArgs.add("--parallelism=4")
            buildArgs.add("--initialize-at-run-time=org.apache.commons.logging,org.apache.log4j,org.springframework.core.SpringProperties")
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
        jvmArgs(
            "-Xms256m",
            "-Xmx512m",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED",
            "--add-opens", "java.base/java.math=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED"
        )
    }

    compileJava {
        options.compilerArgs.add("-parameters")
        options.encoding = "UTF-8"
    }
}
