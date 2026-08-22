plugins {
    java
    id("io.spring.dependency-management")
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

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

implementation("org.springframework.ai:spring-ai-starter-model-openai:1.1.0") {
        exclude(group = "org.eclipse.jetty", module = "jetty-client")
        exclude(group = "org.eclipse.jetty", module = "jetty-http")
        exclude(group = "org.eclipse.jetty", module = "jetty-io")
        exclude(group = "org.eclipse.jetty", module = "jetty-util")
        exclude(group = "org.eclipse.jetty", module = "jetty-alpn-client")
    }
    implementation("org.springframework.ai:spring-ai-openai:1.1.0") {
        exclude(group = "org.eclipse.jetty", module = "jetty-client")
        exclude(group = "org.eclipse.jetty", module = "jetty-http")
        exclude(group = "org.eclipse.jetty", module = "jetty-io")
        exclude(group = "org.eclipse.jetty", module = "jetty-util")
        exclude(group = "org.eclipse.jetty", module = "jetty-alpn-client")
    }
    // Jetty 11 — Spring AI 1.1.0's JettyClientHttpRequestFactory expects Jetty 11 API
    implementation("org.eclipse.jetty:jetty-client:11.0.25")
    implementation("org.eclipse.jetty:jetty-http:11.0.25")
    implementation("org.eclipse.jetty:jetty-io:11.0.25")
    implementation("org.eclipse.jetty:jetty-util:11.0.25")
    implementation("org.eclipse.jetty:jetty-alpn-client:11.0.25")

    // Resilience4j — circuit breaker, retry, bulkhead, time limiter
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.2.0")
    implementation("io.github.resilience4j:resilience4j-bulkhead:2.2.0")
    implementation("io.github.resilience4j:resilience4j-timelimiter:2.2.0")
    implementation("io.github.resilience4j:resilience4j-micrometer:2.2.0")
    implementation("commons-codec:commons-codec:1.16.0")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("com.jcraft:jsch:0.1.55")

    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.wiremock:wiremock:3.8.0")
}

tasks {
    compileJava {
        options.compilerArgs.add("-proc:none")
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
