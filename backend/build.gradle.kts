// ============================================================
// Shared versions — replaces Maven <properties>
// ============================================================
extra["lombokVersion"] = "1.18.34"
extra["jacksonVersion"] = "2.17.1"
extra["slf4jVersion"] = "2.0.13"
extra["logbackVersion"] = "1.5.6"
extra["logstashEncoderVersion"] = "7.4"
extra["junitVersion"] = "5.11.0"
extra["mockitoVersion"] = "5.12.0"
extra["assertjVersion"] = "3.26.3"
extra["wiremockVersion"] = "3.8.0"
extra["bytebuddyVersion"] = "1.17.0"

// ============================================================
// Shared project coordinates
// ============================================================
allprojects {
    group = "com.swingtrade"
    version = "1.0.0"
}

// ============================================================
// Shared configuration for all subprojects
// ============================================================
subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")
    apply(plugin = "checkstyle")
    apply(plugin = "pmd")

    // Java version
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Shared dependencies
    extensions.configure<JavaPluginExtension> {}

    dependencies {
        val v = extra
        // Lombok
        "compileOnly"("org.projectlombok:lombok:${v["lombokVersion"]}")
        "annotationProcessor"("org.projectlombok:lombok:${v["lombokVersion"]}")
        "testCompileOnly"("org.projectlombok:lombok:${v["lombokVersion"]}")
        "testAnnotationProcessor"("org.projectlombok:lombok:${v["lombokVersion"]}")

        // Logging
        "implementation"("org.slf4j:slf4j-api:${v["slf4jVersion"]}")
        "implementation"("ch.qos.logback:logback-classic:${v["logbackVersion"]}")
        "implementation"("net.logstash.logback:logstash-logback-encoder:${v["logstashEncoderVersion"]}")

        // Jackson
        "implementation"("com.fasterxml.jackson.core:jackson-databind:${v["jacksonVersion"]}")
        "implementation"("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${v["jacksonVersion"]}")

        // Testing
        "testImplementation"("org.junit.jupiter:junit-jupiter:${v["junitVersion"]}")
        "testImplementation"("org.mockito:mockito-core:${v["mockitoVersion"]}")
        "testImplementation"("org.assertj:assertj-core:${v["assertjVersion"]}")
        "testImplementation"("org.wiremock:wiremock:${v["wiremockVersion"]}")
        "testImplementation"("net.bytebuddy:byte-buddy:${v["bytebuddyVersion"]}")
        "testImplementation"("net.bytebuddy:byte-buddy-agent:${v["bytebuddyVersion"]}")
    }

    // Test configuration
    tasks.withType<Test> {
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

    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-parameters")
        options.encoding = "UTF-8"
    }

    // Jacoco
    tasks {
        check {
            dependsOn(test)
        }

        val jacocoTestCoverageVerification by getting {
            violationRules {
                rule {
                    limit {
                        counter = "LINE"
                        value = "COVEREDRATIO"
                        minimum = 0.80
                    }
                    excludes.addAll(
                        listOf(
                            "**/domain/*",
                            "**/model/*",
                            "**/dto/*"
                        )
                    )
                }
            }
        }

        val jacocoTestReport by getting {
            dependsOn(test)
        }
    }

    // Checkstyle
    dependencies {
        "checkstyle"("com.puppycrawl.tools:checkstyle:10.17.0")
    }

    tasks.withType<com.puppycrawl.tools.checkstyle.CheckstyleTask> {
        configFile = rootProject.layout.projectDirectory.file("config/checkstyle/checkstyle.xml").asFile
        reportsDir = layout.buildDirectory.dir("reports/checkstyle").get().asFile
        sourceCompatibility = "21"
        maxWarnings = 0
    }

    // PMD
    extensions.configure<net.sourceforge.pmd.gradle.PmdExtension> {
        ruleSetFiles = rootProject.layout.projectDirectory.file("config/pmd/pmd-ruleset.xml").asFile
        ruleSets = emptyList<String>()
        consoleOutput = true
        failOnViolation = true
        printFailingErrors = true
    }
}
