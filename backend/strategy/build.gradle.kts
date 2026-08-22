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
    implementation(project(":llm"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.ta4j:ta4j-core:0.16")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1")

    testImplementation(project(":data"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
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
