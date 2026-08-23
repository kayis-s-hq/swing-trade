package com.swingtrade.api.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
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
 */
class ModuleBoundaryTest {

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("com.swingtrade");

    @Test
    void noCircularDependencies() {
        slices().matching("com.swingtrade.[[fin*]]")
            .should().beFreeOfCycles()
            .check(CLASSES);
    }

    @Test
    void apiShouldNotImportConcreteBrokerClasses() {
        ArchRule onlyAllowedPackages = classes()
            .that().resideInAnyPackage("..api..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                "..core..",
                "..api..",
                "..broker.service..",
                "..broker.config..",
                "..broker.risk..",
                "..data..",
                "..strategy..",
                "..llm..");
        onlyAllowedPackages.check(CLASSES);

        ArchRule noPaperTradingDependency = noClasses()
            .that().resideInAnyPackage("..api..")
            .should().dependOnClassesThat().haveSimpleNameStartingWith("PaperTrading");
        noPaperTradingDependency.check(CLASSES);
    }
}
