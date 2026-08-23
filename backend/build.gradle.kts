plugins {
    java
    id("io.spring.dependency-management") version "1.1.6"
    jacoco
    checkstyle
    pmd
}

// Install Fyers SDK JAR into local Maven repo so it can be used as a proper dependency.
// Run: ./gradlew installFyersSdk
// Then use: implementation("com.fyers:sdk:1.9.0")
tasks.register<Exec>("installFyersSdk") {
    group = "build"
    description = "Installs fyersjavasdk JAR into local Maven repository"
    executable = "mvn"
    args = listOf(
        "install:install-file",
        "-Dfile=libs/fyersjavasdk-1.9.0.jar",
        "-DgroupId=com.fyers",
        "-DartifactId=sdk",
        "-Dversion=1.9.0",
        "-Dpackaging=jar"
    )
    workingDir = layout.projectDirectory.asFile
}

group = "com.swingtrade"
version = "1.0.0"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencyManagement {
    // Managed in subprojects — root project has no dependencies
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")
    apply(plugin = "checkstyle")
    apply(plugin = "pmd")
    apply(plugin = "io.spring.dependency-management")

    dependencyManagement {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
            mavenBom("dev.langchain4j:langchain4j-bom:1.18.1")
            mavenBom("tools.jackson:jackson-bom:3.1.5")
        }
        dependencies {
            dependency("org.projectlombok:lombok:1.18.34")
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    
    // JaCoCo: generate reports after tests
    tasks.named<org.gradle.testing.jacoco.tasks.JacocoReport>("jacocoTestReport") {
        reports {
            xml.required.set(true)
        }
    }

    tasks.withType<Test> {
        finalizedBy("jacocoTestReport")
    }

    // JaCoCo coverage threshold (80% line coverage)
    tasks.named<org.gradle.testing.jacoco.tasks.JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn("test")
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.80")
                }
            }
        }
    }

    // Checkstyle configuration
    checkstyle {
        configDirectory.set(file("../config/checkstyle"))
    }

    // PMD configuration
    pmd {
        isConsoleOutput = false
        ruleSetConfig = resources.text.fromFile(file("../config/pmd/pmd-ruleset.xml"))
        ruleSets = listOf()
    }

    // ArchUnit: enforce module boundary rules (runs as unit test)
    dependencies {
        testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    }

    // Integration test source set
    val integrationTestSourceSet = sourceSets.create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        resources.srcDir("src/integrationTest/resources")
    }

    val integrationTestImplementation = configurations.getByName("integrationTestImplementation")
    integrationTestImplementation.extendsFrom(configurations.getByName("testImplementation"))

    val integrationTestTask = tasks.register<Test>("integrationTest") {
        description = "Runs integration tests"
        group = "verification"
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath
        useJUnitPlatform()
        failFast = true
        jvmArgs(
            "-Xms256m",
            "-Xmx512m",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED",
            "--add-opens", "java.base/java.math=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED"
        )
    }

    tasks.named("check") {
        dependsOn(integrationTestTask)
    }

    // Javadoc generation

    // Source JAR
    tasks.register<Jar>("sourcesJar") {
        description = "Packages source code into a JAR artifact"
        group = "build"
        archiveClassifier.set("sources")
    }
}
