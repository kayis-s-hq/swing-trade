package com.swingtrade.api.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests enforcing module boundary rules.
 *
 * Dependency direction (inward, Clean Architecture):
 *   api -> {strategy, llm, broker, data} -> core
 *
 * Each layer may only depend on layers closer to the center (core).
 *
 * Test classes are excluded from the import: they legitimately depend on
 * test-only libraries (Mockito, Testcontainers, AssertJ, ...) that would
 * otherwise pollute genuine module-boundary violations with noise.
 */
class ModuleBoundaryTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("com.swingtrade");

    @Test
    void noCircularDependencies() {
        slices().matching("com.swingtrade.(*)..")
            .should().beFreeOfCycles()
            .check(CLASSES);
    }

    @Test
    void apiShouldNotImportConcreteBrokerClasses() {
        ArchRule onlyAllowedPackages = classes()
            .that().resideInAnyPackage("..api..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                "..domain..",
                "..core..",
                "..api..",
                "..broker.service..",
                "..broker.config..",
                "..broker.risk..",
                "..data..",
                "..strategy..",
                "..llm..",
                "java..",
                "javax..",
                "jakarta..",
                "org.springframework..",
                "com.fasterxml..",
                "tools.jackson..",
                "lombok..",
                "org.slf4j..",
                "io.micrometer..",
                "io.swagger..",
                "io.github.resilience4j..",
                "reactor..",
                "kotlin..");
        onlyAllowedPackages.check(CLASSES);

        ArchRule noPaperTradingDependency = noClasses()
            .that().resideInAnyPackage("..api..")
            .should().dependOnClassesThat().haveSimpleNameStartingWith("PaperTrading");
        noPaperTradingDependency.check(CLASSES);
    }
}
