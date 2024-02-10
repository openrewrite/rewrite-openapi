package org.openrewrite.openapi.swagger;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;

class ConvertApiResponseCodesToStringsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.openapi.MigrateApiResponsesToApiResponses")
          .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-1.+", "swagger-annotations-2.+"));
    }

    @Test
    @DocumentExample
    void convertApiResponseCodesToStrings() {
        rewriteRun(
          //language=java
          java(
            """
              import io.swagger.annotations.ApiResponse;
              
              class A {
                  @ApiResponse(code = 200, message = "OK")
                  void method() {}
              }
              """,
            """
              import io.swagger.v3.oas.annotations.responses.ApiResponse;
              
              class A {
                  @ApiResponse(responseCode = "200", description = "OK")
                  void method() {}
              }
              """
          )
        );
    }

    @Test
    void noChangeOnAlreadyConverted() {
        rewriteRun(
          //language=java
          java(
            """
              import io.swagger.v3.oas.annotations.responses.ApiResponse;
              
              class A {
                  @ApiResponse(responseCode = "200", description = "OK")
                  void method() {}
              }
              """
          )
        );
    }
}