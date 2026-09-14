package com.coupleoutfit;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class FeatureArchitectureTest {
    @Test void domainAndApplicationDoNotDependOnAdaptersOrFrameworks() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.coupleoutfit.market", "com.coupleoutfit.retailer", "com.coupleoutfit.catalogue", "com.coupleoutfit.source", "com.coupleoutfit.storage");
        noClasses().that().resideInAnyPackage("..domain..", "..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..", "org.springframework..", "jakarta.persistence..", "com.cloudflare..", "software.amazon.awssdk..")
                .check(classes);
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..").check(classes);
    }
}
