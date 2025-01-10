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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Pattern;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class SwaggerToOpenAPITest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.openapi.swagger.SwaggerToOpenAPI")
          .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-1.+", "swagger-annotations-2.+", "rs-api"));
    }

    @Test
    void loadYamlRecipesToTriggerValidation() {
        rewriteRun(
          spec -> spec.printRecipe(() -> System.out::println)
        );
    }

    @DocumentExample
    @Test
    void shouldChangeSwaggerArtifacts() {
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
              import io.swagger.v3.oas.annotations.media.Schema;

              @Schema(name="ApiModelExampleValue", description="ApiModelExampleDescription")
              class Example {
                @Schema(description = "ApiModelPropertyExampleValue")
                private String example;
              }
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
            after -> after.after(actual -> """
              <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <dependencies>
                  <dependency>
                    <groupId>io.swagger.core.v3</groupId>
                    <artifactId>swagger-annotations</artifactId>
                    <version>%1$s</version>
                  </dependency>
                  <dependency>
                    <groupId>io.swagger.core.v3</groupId>
                    <artifactId>swagger-models</artifactId>
                    <version>%1$s</version>
                  </dependency>
                  <dependency>
                    <groupId>io.swagger.core.v3</groupId>
                    <artifactId>swagger-core</artifactId>
                    <version>%1$s</version>
                  </dependency>
                </dependencies>
              </project>
              """.formatted(Pattern.compile("<version>(2\\.2\\.[\\d.]+)</version>")
              .matcher(actual)
              .results()
              .findFirst()
              .get()
              .group(1)))
          )
        );
    }

    @Test
    void migrateApiImplicitParam() {
        rewriteRun(
          //language=java
          java(
            """
              import io.swagger.annotations.ApiImplicitParam;

              class Example {
                @ApiImplicitParam(name = "foo", value = "Foo object", required = true, dataTypeClass = Example.class, defaultValue = "example")
                public void create(Example foo) {
                }
              }
              """,
            """
              import io.swagger.v3.oas.annotations.Parameter;
              import io.swagger.v3.oas.annotations.media.Schema;

              class Example {
                @Parameter(name = "foo", description = "Foo object", required = true, schema = @Schema(implementation = Example.class, defaultValue = "example"))
                public void create(Example foo) {
                }
              }
              """
          )
        );
    }

    @Test
    void migrateApiParam() {
        rewriteRun(
          //language=java
          java(
            """
              import io.swagger.annotations.ApiParam;

              class Example {
                @ApiParam(name = "foo", value = "Foo Object", required = false, defaultValue = "bar")
                private Integer foo;
              }
              """,
            """
              import io.swagger.v3.oas.annotations.Parameter;
              import io.swagger.v3.oas.annotations.media.Schema;

              class Example {
                @Parameter(name = "foo", description = "Foo Object", required = false, schema = @Schema(defaultValue = "bar"))
                private Integer foo;
              }
              """
          )
        );
    }

    @Test
    void migrateSwaggerDefinitionsToOpenAPIDefinitionSingleSchema() {
        rewriteRun(
          //language=java
          java(
            """
              import io.swagger.annotations.Info;
              import io.swagger.annotations.SwaggerDefinition;
              import jakarta.ws.rs.core.MediaType;

              @SwaggerDefinition(
                basePath = "/api",
                host="example.com",
                info = @Info(title = "Example", version = "V1.0"),
                consumes = { MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML },
                produces = { MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML },
                schemes = SwaggerDefinition.Scheme.HTTPS
              )
              class Example {
              }
              """,
            """
              import io.swagger.v3.oas.annotations.OpenAPIDefinition;
              import io.swagger.v3.oas.annotations.info.Info;
              import io.swagger.v3.oas.annotations.servers.Server;

              @OpenAPIDefinition(
                      servers = {
                              @Server(url = "https://example.com/api")
                      },
                      info = @Info(title = "Example", version = "V1.0")
              )
              class Example {
              }
              """
          )
        );
    }

    @Test
    void migrateSwaggerDefinitionsToOpenAPIDefinitionMultipleSchema() {
        rewriteRun(
          //language=java
          java(
            """
              import io.swagger.annotations.Info;
              import io.swagger.annotations.SwaggerDefinition;
              import jakarta.ws.rs.core.MediaType;

              @SwaggerDefinition(
                basePath = "/api",
                host="example.com",
                info = @Info(title = "Example", version = "V1.0"),
                consumes = { MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML },
                produces = { MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML },
                schemes = { SwaggerDefinition.Scheme.HTTP, SwaggerDefinition.Scheme.HTTPS })
              class Example {
              }
              """,
            """
              import io.swagger.v3.oas.annotations.OpenAPIDefinition;
              import io.swagger.v3.oas.annotations.info.Info;
              import io.swagger.v3.oas.annotations.servers.Server;

              @OpenAPIDefinition(
                      servers = {
                              @Server(url = "http://example.com/api"),
                              @Server(url = "https://example.com/api")
                      },
                      info = @Info(title = "Example", version = "V1.0")
              )
              class Example {
              }
              """
          )
        );
    }
}
