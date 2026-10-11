package com.amazfy

import com.tngtech.archunit.core.domain.Dependency
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * Checks the hexagonal architecture rules from CLAUDE.md on the compiled code.
 * A rule that has nothing to check yet (e.g. no domain package exists) passes.
 */
@AnalyzeClasses(packages = ["com.amazfy"], importOptions = [ImportOption.DoNotIncludeTests::class])
class ArchitectureTest {
    @ArchTest
    val domainIsPureKotlin: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("com.amazfy..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "com.amazfy..adapter..",
                "com.amazfy.api.generated..",
            ).because("the domain holds business rules only (see CLAUDE.md, Architecture)")
            .allowEmptyShould(true)

    @ArchTest
    val applicationDoesNotDependOnAdapters: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("com.amazfy..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.amazfy..adapter..")
            .because("use cases talk to the outside only through ports")
            .allowEmptyShould(true)

    @ArchTest
    val generatedApiOnlyInWebAdapters: ArchRule =
        noClasses()
            .that()
            .resideOutsideOfPackages("com.amazfy..adapter.inbound.web..", "com.amazfy.api.generated..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.amazfy.api.generated..")
            .because("API request/response types stay at the HTTP edge")

    @ArchTest
    val modulesDoNotReachIntoEachOther: ArchRule =
        classes()
            .that()
            .resideInAPackage("com.amazfy..")
            .should(notReachIntoOtherModulesInternals())
            .because("modules use each other only through their application layer")
}

/** Top-level module of a class: com.amazfy.<module>.... (null for classes directly in com.amazfy) */
private fun JavaClass.module(): String? =
    packageName.removePrefix("com.amazfy.").substringBefore('.').takeIf {
        packageName.startsWith("com.amazfy.")
    }

private val internalLayer = Regex("""^com\.amazfy\.([a-z0-9]+)\.(adapter|domain)(\..*)?$""")

private fun notReachIntoOtherModulesInternals() =
    object : ArchCondition<JavaClass>("not depend on another module's adapter or domain packages") {
        override fun check(
            item: JavaClass,
            events: ConditionEvents,
        ) {
            val origin = item.module()
            item.directDependenciesFromSelf
                .filter { isInternalOfOtherModule(it, origin) }
                .forEach { events.add(SimpleConditionEvent.violated(it, it.description)) }
        }

        private fun isInternalOfOtherModule(
            dependency: Dependency,
            originModule: String?,
        ): Boolean {
            val match = internalLayer.find(dependency.targetClass.packageName) ?: return false
            return match.groupValues[1] != originModule
        }
    }
