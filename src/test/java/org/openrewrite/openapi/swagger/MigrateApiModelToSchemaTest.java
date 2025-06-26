package org.openrewrite.openapi.swagger;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateApiModelToSchemaTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateApiModelToSchema())
          .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-1.+", "swagger-annotations-2.+", "rs-api"));
    }

    @Test
    @DocumentExample
    void shouldChangeApiModelWithSchema() {
        rewriteRun(
          //language=java
          java(
            """
              import io.swagger.annotations.ApiModel;
              import io.swagger.annotations.ApiModelProperty;

              @ApiModel(value="ApiModelExampleValue", description="ApiModelExampleDescription")
              class Example {
                @ApiModelProperty(value = "ApiModelPropertyExampleValue", position = 1)
                private String example;
              }
              """,
            """
              import io.swagger.annotations.ApiModelProperty;
              import io.swagger.v3.oas.annotations.media.Schema;

              @Schema(name="ApiModelExampleValue", description="ApiModelExampleDescription")
              class Example {
                @ApiModelProperty(value = "ApiModelPropertyExampleValue", position = 1)
                private String example;
              }
              """
          )
        );
    }

    @Test
    void shouldMergeWithExistingAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import io.swagger.v3.oas.annotations.media.Schema;
              import io.swagger.annotations.ApiModel;
              import io.swagger.annotations.ApiModelProperty;

              @ApiModel(value="ApiModelExampleValue")
              @Schema(description="ApiModelExampleDescription")
              class Example {
                @ApiModelProperty(value = "ApiModelPropertyExampleValue", position = 1)
                private String example;
              }
              """,
            """
              import io.swagger.v3.oas.annotations.media.Schema;
              import io.swagger.annotations.ApiModelProperty;

              @Schema(description = "ApiModelExampleDescription", name = "ApiModelExampleValue")
              class Example {
                @ApiModelProperty(value = "ApiModelPropertyExampleValue", position = 1)
                private String example;
              }
              """
          )
        );
    }

    @Test
    void shouldPreferExistingAnnotationValue() {
        rewriteRun(
          //language=java
          java(
            """
              import io.swagger.v3.oas.annotations.media.Schema;
              import io.swagger.annotations.ApiModel;
              import io.swagger.annotations.ApiModelProperty;

              @ApiModel(value="AnotherApiModelExampleValue", description="AnotherApiModelExampleDescription")
              @Schema(name="ApiModelExampleValue", description="ApiModelExampleDescription")
              class Example {
                @ApiModelProperty(value = "ApiModelPropertyExampleValue", position = 1)
                private String example;
              }
              """,
            """
              import io.swagger.v3.oas.annotations.media.Schema;
              import io.swagger.annotations.ApiModelProperty;

              @Schema(name="ApiModelExampleValue", description="ApiModelExampleDescription")
              class Example {
                @ApiModelProperty(value = "ApiModelPropertyExampleValue", position = 1)
                private String example;
              }
              """
          )
        );
    }
}
