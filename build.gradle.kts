plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "OpenAPI Migration"

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:$rewriteVersion")

    testImplementation("org.openrewrite:rewrite-java-17")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite:rewrite-gradle")
    testImplementation("org.openrewrite:rewrite-maven")

    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.13.3")

    testRuntimeOnly("io.swagger:swagger-annotations:1.5.16")
    testRuntimeOnly("io.swagger:swagger-models:1.5.16")
    testRuntimeOnly("io.swagger:swagger-core:1.5.16")
    testRuntimeOnly("io.swagger:swagger-mule:1.5.16")
    testRuntimeOnly("io.swagger.core.v3:swagger-annotations:2.2.34")
    testRuntimeOnly("io.swagger.core.v3:swagger-models:2.2.34")
    testRuntimeOnly("io.swagger.core.v3:swagger-core:2.2.34")

    testRuntimeOnly("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")

    testRuntimeOnly("org.gradle:gradle-tooling-api:latest.release")

}

recipeDependencies {
    parserClasspath("io.swagger.core.v3:swagger-annotations:2.2.20")
}
