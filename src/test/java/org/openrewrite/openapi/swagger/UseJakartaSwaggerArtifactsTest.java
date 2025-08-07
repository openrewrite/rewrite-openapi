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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class UseJakartaSwaggerArtifactsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.openapi.swagger.UseJakartaSwaggerArtifacts")
          .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-2.+", "swagger-annotations-jakarta-2.+"));
    }

    @DocumentExample
    @Test
    void basic() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo-child</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>io.swagger.core.v3</groupId>
                          <artifactId>swagger-annotations</artifactId>
                          <version>2.1.10</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo-child</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>io.swagger.core.v3</groupId>
                          <artifactId>swagger-annotations-jakarta</artifactId>
                          <version>2.1.10</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void dependencyManaged() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo-child</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>io.swagger.core.v3</groupId>
                              <artifactId>swagger-annotations</artifactId>
                              <version>2.1.10</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>

                  <dependencies>
                      <dependency>
                          <groupId>io.swagger.core.v3</groupId>
                          <artifactId>swagger-annotations</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo-child</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>io.swagger.core.v3</groupId>
                              <artifactId>swagger-annotations-jakarta</artifactId>
                              <version>2.1.10</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>

                  <dependencies>
                      <dependency>
                          <groupId>io.swagger.core.v3</groupId>
                          <artifactId>swagger-annotations</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Disabled("Needs: https://github.com/openrewrite/rewrite/pull/5815")
    @Test
    void parentChild() {
        rewriteRun(
          mavenProject("parent",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <modules>
                        <module>child</module>
                    </modules>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>io.swagger.core.v3</groupId>
                                <artifactId>swagger-annotations</artifactId>
                                <version>2.1.10</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """,
              """
                <project>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <modules>
                        <module>child</module>
                    </modules>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>io.swagger.core.v3</groupId>
                                <artifactId>swagger-annotations-jakarta</artifactId>
                                <version>2.1.10</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """
            ),
            mavenProject("child",
              //language=xml
              pomXml(
                """
                  <project>
                      <parent>
                          <groupId>com.mycompany.app</groupId>
                          <artifactId>parent</artifactId>
                          <version>1</version>
                          <relativePath>../pom.xml</relativePath>
                      </parent>
                      <artifactId>child</artifactId>
                      <dependencyManagement>
                          <dependencies>
                              <dependency>
                                  <groupId>io.swagger.core.v3</groupId>
                                  <artifactId>swagger-annotations</artifactId>
                              </dependency>
                          </dependencies>
                      </dependencyManagement>
                  </project>
                  """,
                """
                  <project>
                      <parent>
                          <groupId>com.mycompany.app</groupId>
                          <artifactId>parent</artifactId>
                          <version>1</version>
                          <relativePath>../pom.xml</relativePath>
                      </parent>
                      <artifactId>child</artifactId>
                      <dependencyManagement>
                          <dependencies>
                              <dependency>
                                  <groupId>io.swagger.core.v3</groupId>
                                  <artifactId>swagger-annotations-jakarta</artifactId>
                              </dependency>
                          </dependencies>
                      </dependencyManagement>
                  </project>
                  """
              )
            )
          )
        );
    }
}
