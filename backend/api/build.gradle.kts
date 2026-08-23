import org.springframework.boot.gradle.tasks.bundling.BootJar

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
    // TODO: restore when GpuHubDeploymentService component scan is fixed
    // implementation(project(":gpuhub"))
    implementation(project(":broker"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-devtools")

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
        }
    }
}

tasks {
    named<BootJar>("bootJar") {
        manifest {
            attributes["Main-Class"] = "com.swingtrade.api.app.SwingTradeApiApplication"
        }
    }

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
