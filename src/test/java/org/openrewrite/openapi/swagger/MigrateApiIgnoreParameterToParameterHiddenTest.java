/*
 * Copyright 2026 the original author or authors.
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

class MigrateApiIgnoreParameterToParameterHiddenTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateApiIgnoreParameterToParameterHidden())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "swagger-annotations-2")
            .dependsOn(
              //language=java
              """
                package springfox.documentation.annotations;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Target;
                @Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
                public @interface ApiIgnore {
                    String value() default "";
                }
                """
            )
          );
    }

    @DocumentExample
    @Test
    void replacesApiIgnoreOnParameter() {
        rewriteRun(
          //language=java
          java(
            """
              import springfox.documentation.annotations.ApiIgnore;

              import java.security.Principal;

              class Example {
                  void handler(@ApiIgnore Principal principal) {
                  }
              }
              """,
            """
              import io.swagger.v3.oas.annotations.Parameter;

              import java.security.Principal;

              class Example {
                  void handler(@Parameter(hidden = true) Principal principal) {
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesApiIgnoreOnMethodAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import springfox.documentation.annotations.ApiIgnore;

              class Example {
                  @ApiIgnore
                  void method() {
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesApiIgnoreOnClassAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import springfox.documentation.annotations.ApiIgnore;

              @ApiIgnore
              class Example {
              }
              """
          )
        );
    }

    @Test
    void onlyTouchesMatchingParameter() {
        rewriteRun(
          //language=java
          java(
            """
              import springfox.documentation.annotations.ApiIgnore;

              import java.security.Principal;

              class Example {
                  void handler(String pathVar, @ApiIgnore Principal principal, String body) {
                  }
              }
              """,
            """
              import io.swagger.v3.oas.annotations.Parameter;

              import java.security.Principal;

              class Example {
                  void handler(String pathVar, @Parameter(hidden = true) Principal principal, String body) {
                  }
              }
              """
          )
        );
    }
}
