import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot") version "3.5.9"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.graalvm.buildtools.native") version "0.10.6"
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.9")
        mavenBom("dev.langchain4j:langchain4j-bom:1.18.1")
    }
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
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-devtools")

    implementation("io.micrometer:micrometer-core")
    implementation("io.micrometer:micrometer-registry-prometheus")

    implementation("org.postgresql:postgresql:42.7.3")
    implementation("org.flywaydb:flyway-core:10.13.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.13.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.mockito:mockito-junit-jupiter:5.12.0")
    testImplementation("org.wiremock:wiremock:3.8.0")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.h2database:h2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

springBoot {
    mainClass = "com.swingtrade.api.app.SwingTradeApiApplication"
    buildInfo()
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
