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

    testRuntimeOnly("io.swagger:swagger-annotations:1.6.13")
    testRuntimeOnly("io.swagger.core.v3:swagger-annotations:2.2.20")
    testRuntimeOnly("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")

    testRuntimeOnly("org.gradle:gradle-tooling-api:latest.release")
}

recipeDependencies {
    parserClasspath("io.swagger.core.v3:swagger-annotations:2.2.20")
}
