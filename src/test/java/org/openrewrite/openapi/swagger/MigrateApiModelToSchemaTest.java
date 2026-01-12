/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    @DocumentExample
    @Test
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

    @Test
    void shouldMigrateUrlReferenceToRef() {
        rewriteRun(
          //language=java
          java(
            """
              import io.swagger.annotations.ApiModel;

              @ApiModel(reference = "https://example.com/schemas/MySchema")
              class Example {
              }
              """,
            """
              import io.swagger.v3.oas.annotations.media.Schema;

              @Schema(ref = "https://example.com/schemas/MySchema")
              class Example {
              }
              """
          )
        );
    }

    @Test
    void shouldMigrateClassReferenceToImplementation() {
        rewriteRun(
          //language=java
          java(
            """
              import io.swagger.annotations.ApiModel;

              @ApiModel(reference = "String")
              class Example {
              }
              """,
            """
              import io.swagger.v3.oas.annotations.media.Schema;

              @Schema(implementation = String.class)
              class Example {
              }
              """
          )
        );
    }
}
