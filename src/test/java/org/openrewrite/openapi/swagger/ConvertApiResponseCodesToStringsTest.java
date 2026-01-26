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
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class ConvertApiResponseCodesToStringsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ConvertApiResponseCodesToStrings())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "swagger-annotations-2")
            .dependsOn(
              """
                package org.springframework.http;
                public class ResponseEntity<T> {}
                """,
              "class User {}"
            )
          );
    }

    @DocumentExample
    @Test
    void convertIntLiteralToString() {
        //language=java
        rewriteRun(
          java(
            """
              import io.swagger.v3.oas.annotations.responses.ApiResponse;
              import org.springframework.http.ResponseEntity;

              class A {
                  @ApiResponse(responseCode = 200, description = "OK")
                  ResponseEntity<User> method() { return null; }
              }
              """,
            """
              import io.swagger.v3.oas.annotations.responses.ApiResponse;
              import org.springframework.http.ResponseEntity;

              class A {
                  @ApiResponse(responseCode = "200", description = "OK")
                  ResponseEntity<User> method() { return null; }
              }
              """
          )
        );
    }

    @Test
    void noChangeOnAlreadyString() {
        //language=java
        rewriteRun(
          java(
            """
              import io.swagger.v3.oas.annotations.responses.ApiResponse;
              import org.springframework.http.ResponseEntity;

              class A {
                  @ApiResponse(responseCode = "200", description = "OK")
                  ResponseEntity<User> method() { return null; }
              }
              """
          )
        );
    }

    @Issue("https://github.com/moderneinc/customer-requests/issues/1674")
    @Test
    void convertLocalConstantIdentifier() {
        //language=java
        rewriteRun(
          // Input has int constant where String is expected - this is the broken state we're fixing
          spec -> spec.typeValidationOptions(TypeValidation.all().identifiers(false)),
          java(
            """
              import io.swagger.v3.oas.annotations.responses.ApiResponse;
              import org.springframework.http.ResponseEntity;

              class A {
                  public static final int OK_CODE = 200;

                  @ApiResponse(responseCode = OK_CODE, description = "OK")
                  ResponseEntity<User> method() { return null; }
              }
              """,
            """
              import io.swagger.v3.oas.annotations.responses.ApiResponse;
              import org.springframework.http.ResponseEntity;

              class A {
                  public static final int OK_CODE = 200;

                  @ApiResponse(responseCode = "200", description = "OK")
                  ResponseEntity<User> method() { return null; }
              }
              """
          )
        );
    }

    @Issue("https://github.com/moderneinc/customer-requests/issues/1674")
    @Test
    void convertExternalConstantFieldAccess() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;

              public class StatusCodes {
                  public static final int NOT_FOUND = 404;
              }
              """
          ),
          java(
            """
              import com.example.StatusCodes;
              import io.swagger.v3.oas.annotations.responses.ApiResponse;
              import org.springframework.http.ResponseEntity;

              class A {
                  @ApiResponse(responseCode = StatusCodes.NOT_FOUND, description = "Not found")
                  ResponseEntity<User> method() { return null; }
              }
              """,
            """
              import com.example.StatusCodes;
              import io.swagger.v3.oas.annotations.responses.ApiResponse;
              import org.springframework.http.ResponseEntity;

              class A {
                  @ApiResponse(responseCode = "404", description = "Not found")
                  ResponseEntity<User> method() { return null; }
              }
              """
          )
        );
    }

    @Issue("https://github.com/moderneinc/customer-requests/issues/1674")
    @Test
    void convertNestedClassConstantFieldAccess() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;

              public class Dialog {
                  public static class PredefinedButton {
                      public static final int YES = 1;
                      public static final int NO = 0;
                  }
              }
              """
          ),
          java(
            """
              import com.example.Dialog;
              import io.swagger.v3.oas.annotations.responses.ApiResponse;
              import org.springframework.http.ResponseEntity;

              class A {
                  @ApiResponse(responseCode = Dialog.PredefinedButton.YES, description = "Confirmed")
                  ResponseEntity<User> method() { return null; }
              }
              """,
            """
              import com.example.Dialog;
              import io.swagger.v3.oas.annotations.responses.ApiResponse;
              import org.springframework.http.ResponseEntity;

              class A {
                  @ApiResponse(responseCode = "1", description = "Confirmed")
                  ResponseEntity<User> method() { return null; }
              }
              """
          )
        );
    }
}
