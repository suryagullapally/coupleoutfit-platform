package com.coupleoutfit;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class FeatureArchitectureTest {
    @Test void domainAndApplicationDoNotDependOnAdaptersOrFrameworks() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.coupleoutfit.market", "com.coupleoutfit.retailer", "com.coupleoutfit.catalogue", "com.coupleoutfit.source", "com.coupleoutfit.storage", "com.coupleoutfit.analysis", "com.coupleoutfit.compatibility", "com.coupleoutfit.matching", "com.coupleoutfit.affiliate", "com.coupleoutfit.feed");
        noClasses().that().resideInAnyPackage("..domain..", "..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..", "org.springframework..", "jakarta.persistence..", "com.openai..", "java.net.http..", "okhttp3..", "com.cloudflare..", "software.amazon.awssdk..")
                .check(classes);
        noClasses().that().resideInAnyPackage("..analysis..", "..compatibility..", "..matching.domain..", "..matching.application..", "..matching.infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage("..affiliate..", "..feed..").check(classes);
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..").check(classes);
    }
}
