package org.openrewrite.openapi;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class Swagger2ToOpenApiV3MigrationTest implements RewriteTest {
    @Test
    void swagger2ToOpenApiV3() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.openapi.Swagger2ToOpenApiV3Migration"),
          //language=java
          java( // TODO Add old swagger elements
            """
              class A {}
              """
          )
        );
    }
}
