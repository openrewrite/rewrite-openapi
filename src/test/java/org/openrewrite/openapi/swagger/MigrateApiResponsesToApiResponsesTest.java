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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateApiResponsesToApiResponsesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.openapi.swagger.MigrateApiResponsesToApiResponses")
          .parser(JavaParser.fromJavaVersion()
            .classpath("swagger-annotations-1.+")
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
    void convertApiResponseCodesToStrings() {
        //language=java
        rewriteRun(
          java(
            """
              import io.swagger.annotations.ApiResponse;
              import org.springframework.http.ResponseEntity;

              class A {
                  @ApiResponse(code = 200, message = "OK", response = User.class)
                  ResponseEntity<User> method() { return null; }
              }
              """,
            """
              import io.swagger.v3.oas.annotations.media.Content;
              import io.swagger.v3.oas.annotations.media.Schema;
              import io.swagger.v3.oas.annotations.responses.ApiResponse;
              import org.springframework.http.ResponseEntity;

              class A {
                  @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", schema = @Schema(implementation = User.class)))
                  ResponseEntity<User> method() { return null; }
              }
              """
          )
        );
    }

	@Test
	void convertApiResponseListContainers() {
		//language=java
		rewriteRun(
		  java(
			"""
              import io.swagger.annotations.ApiResponse;
              import org.springframework.http.ResponseEntity;

              class A {
                  @ApiResponse(code = 200, message = "OK", responseContainer = "List", response = User.class)
                  ResponseEntity<User> method() { return null; }
              }
              """,
			"""
              import io.swagger.v3.oas.annotations.media.ArraySchema;
              import io.swagger.v3.oas.annotations.media.Content;
              import io.swagger.v3.oas.annotations.media.Schema;
              import io.swagger.v3.oas.annotations.responses.ApiResponse;
              import org.springframework.http.ResponseEntity;

              class A {
                  @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", array = @ArraySchema(uniqueItems = false, schema = @Schema(implementation = User.class))))
                  ResponseEntity<User> method() { return null; }
              }
              """
		  )
		);
	}

	@Test
	void convertApiResponseSetContainers() {
		//language=java
		rewriteRun(
		  java(
			"""
              import io.swagger.annotations.ApiResponse;
              import org.springframework.http.ResponseEntity;

              class A {
                  @ApiResponse(code = 200, message = "OK", response = User.class, responseContainer = "Set")
                  ResponseEntity<User> method() { return null; }
              }
              """,
			"""
              import io.swagger.v3.oas.annotations.media.ArraySchema;
              import io.swagger.v3.oas.annotations.media.Content;
              import io.swagger.v3.oas.annotations.media.Schema;
              import io.swagger.v3.oas.annotations.responses.ApiResponse;
              import org.springframework.http.ResponseEntity;

              class A {
                  @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", array = @ArraySchema(uniqueItems = true, schema = @Schema(implementation = User.class))))
                  ResponseEntity<User> method() { return null; }
              }
              """
		  )
		);
	}

	@Test
	void convertApiResponseMapContainers() {
		//language=java
		rewriteRun(
		  java(
			"""
              import io.swagger.annotations.ApiResponse;
              import org.springframework.http.ResponseEntity;

              class A {
                  @ApiResponse(code = 200, message = "OK", responseContainer = "Map", response = User.class)
                  ResponseEntity<User> method() { return null; }
              }
              """,
			"""
              import io.swagger.v3.oas.annotations.media.Content;
              import io.swagger.v3.oas.annotations.media.Schema;
              import io.swagger.v3.oas.annotations.responses.ApiResponse;
              import org.springframework.http.ResponseEntity;

              class A {
                  @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", schema = @Schema(type = "object", additionalPropertiesSchema = User.class)))
                  ResponseEntity<User> method() { return null; }
              }
              """
		  )
		);
	}

    @Test
    void noChangeOnAlreadyConverted() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "swagger-annotations")
            .dependsOn(
              """
                package org.springframework.http;
                public class ResponseEntity<T> {}
                """,
              "class User {}"
            )
          ),
          //language=java
          java(
            """
              import io.swagger.v3.oas.annotations.media.Content;
              import io.swagger.v3.oas.annotations.media.Schema;
              import io.swagger.v3.oas.annotations.responses.ApiResponse;
              import org.springframework.http.ResponseEntity;

              class A {
                  @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", schema = @Schema(implementation = User.class)))
                  ResponseEntity<User> method() { return null; }
              }
              """
          )
        );
    }
}
