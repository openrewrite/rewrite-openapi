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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class SwaggerToOpenAPITest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.openapi.swagger.SwaggerToOpenAPI")
          .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-1.+", "swagger-annotations-2.+"));
    }

    @Test
    void loadYamlRecipesToTriggerValidation() {
        rewriteRun(
          spec-> spec.printRecipe(() -> System.out::println)
        );
    }

    @Test
    void shouldChangeSwaggerArtifacts() {
        rewriteRun(
          //language=java
          java(
            """
            package example.org;
            
            import io.swagger.annotations.ApiModel;

            @ApiModel
            class Example { }
            """,
            """
            package example.org;
            
            import io.swagger.v3.oas.annotations.media.Schema;

            @Schema
            class Example { }
            """
          ),
          //language=xml
          pomXml(
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencies>
                  <dependency>
                    <groupId>io.swagger</groupId>
                    <artifactId>swagger-annotations</artifactId>
                    <version>1.6.14</version>
                  </dependency>
                  <dependency>
                    <groupId>io.swagger</groupId>
                    <artifactId>swagger-models</artifactId>
                    <version>1.6.14</version>
                  </dependency>
                  <dependency>
                    <groupId>io.swagger</groupId>
                    <artifactId>swagger-core</artifactId>
                    <version>1.6.14</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencies>
                  <dependency>
                    <groupId>io.swagger.core.v3</groupId>
                    <artifactId>swagger-annotations</artifactId>
                    <version>2.2.21</version>
                  </dependency>
                  <dependency>
                    <groupId>io.swagger.core.v3</groupId>
                    <artifactId>swagger-models</artifactId>
                    <version>2.2.21</version>
                  </dependency>
                  <dependency>
                    <groupId>io.swagger.core.v3</groupId>
                    <artifactId>swagger-core</artifactId>
                    <version>2.2.21</version>
                  </dependency>
                </dependencies>
              </project>
              """));
    }
}
