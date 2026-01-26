/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ConvertApiResponseCodesToStringsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ConvertApiResponseCodesToStrings())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "swagger-annotations-2")
          );
    }

    @DocumentExample
    @Test
    void convertIntegerLiteralToString() {
        rewriteRun(
          //language=java
          java(
            """
              import io.swagger.v3.oas.annotations.responses.ApiResponse;

              class A {
                  @ApiResponse(responseCode = 200, description = "OK")
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
    void noChangeWhenAlreadyString() {
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

    @Issue("https://github.com/moderneinc/customer-requests/issues/1674")
    @Test
    void convertLocalConstantToString() {
        rewriteRun(
          // Type validation disabled because the field declaration identifier lacks full type attribution in tests
          spec -> spec.typeValidationOptions(org.openrewrite.test.TypeValidation.none()),
          //language=java
          java(
            """
              import io.swagger.v3.oas.annotations.responses.ApiResponse;

              class A {
                  static final int OK_CODE = 200;

                  @ApiResponse(responseCode = OK_CODE, description = "OK")
                  void method() {}
              }
              """,
            """
              import io.swagger.v3.oas.annotations.responses.ApiResponse;

              class A {
                  static final int OK_CODE = 200;

                  @ApiResponse(responseCode = "200", description = "OK")
                  void method() {}
              }
              """
          )
        );
    }

    @Issue("https://github.com/moderneinc/customer-requests/issues/1674")
    @Test
    void convertFieldAccessConstantToString() {
        rewriteRun(
          // Type validation disabled because dependsOn classes lack full type attribution in tests
          spec -> spec.typeValidationOptions(org.openrewrite.test.TypeValidation.none()),
          //language=java
          java(
            """
              package a.b.c;

              public class HttpCodes {
                  public static final int NOT_FOUND = 404;
              }
              """
          ),
          //language=java
          java(
            """
              import a.b.c.HttpCodes;
              import io.swagger.v3.oas.annotations.responses.ApiResponse;

              class A {
                  @ApiResponse(responseCode = HttpCodes.NOT_FOUND, description = "Not Found")
                  void method() {}
              }
              """,
            """
              import a.b.c.HttpCodes;
              import io.swagger.v3.oas.annotations.responses.ApiResponse;

              class A {
                  @ApiResponse(responseCode = "404", description = "Not Found")
                  void method() {}
              }
              """
          )
        );
    }

    @Issue("https://github.com/moderneinc/customer-requests/issues/1674")
    @Test
    void convertMultipleConstantsToStrings() {
        rewriteRun(
          // Type validation disabled because dependsOn classes lack full type attribution in tests
          spec -> spec.typeValidationOptions(org.openrewrite.test.TypeValidation.none()),
          //language=java
          java(
            """
              package a.b.c;

              public class HttpCodes {
                  public static final int OK = 200;
                  public static final int NOT_FOUND = 404;
              }
              """
          ),
          //language=java
          java(
            """
              import a.b.c.HttpCodes;
              import io.swagger.v3.oas.annotations.responses.ApiResponse;
              import io.swagger.v3.oas.annotations.responses.ApiResponses;

              class A {
                  @ApiResponses(value = {
                      @ApiResponse(responseCode = HttpCodes.OK, description = "Success"),
                      @ApiResponse(responseCode = HttpCodes.NOT_FOUND, description = "Not Found")
                  })
                  void method() {}
              }
              """,
            """
              import a.b.c.HttpCodes;
              import io.swagger.v3.oas.annotations.responses.ApiResponse;
              import io.swagger.v3.oas.annotations.responses.ApiResponses;

              class A {
                  @ApiResponses(value = {
                      @ApiResponse(responseCode = "200", description = "Success"),
                      @ApiResponse(responseCode = "404", description = "Not Found")
                  })
                  void method() {}
              }
              """
          )
        );
    }
}
