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

import static org.openrewrite.java.Assertions.java;

class MigrateApiToTagTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateApiToTag())
          .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-1.+"));
    }

    @DocumentExample
    @Test
    void single() {
        rewriteRun(
          //language=java
          java(
            """
              import io.swagger.annotations.Api;

              @Api(value = "Bar")
              class Example {}
              """,
            """
              import io.swagger.v3.oas.annotations.tags.Tag;

              @Tag(name = "Bar")
              class Example {}
              """
          )
        );
    }

    @Test
    void single_with_produces() {
        rewriteRun(
          //language=java
          java(
            """
              import io.swagger.annotations.Api;

              @Api(value = "Bar", produces = "application/json")
              class Example {}
              """,
            """
              import io.swagger.v3.oas.annotations.tags.Tag;

              @Tag(name = "Bar")
              class Example {}
              """
          )
        );
    }

    // Hidden is supported in swagger-annotations-2.+
    @Test
    void singleHidden() {
        rewriteRun(
            //language=java
            java(
                """
                  import io.swagger.annotations.Api;

                  @Api(value = "Bar", hidden = true)
                  class Example {}
                  """,
                """
                  import io.swagger.v3.oas.annotations.Hidden;
                  import io.swagger.v3.oas.annotations.tags.Tag;

                  @Tag(name = "Bar")
                  @Hidden
                  class Example {}
                  """
            )
        );
    }

    @Test
    void singleTagAsArray() {
        rewriteRun(
          //language=java
          java(
            """
              import io.swagger.annotations.Api;

              @Api(tags = {"foo"}, value = "Ignore", description = "Desc")
              class Example {}
              """,
            """
              import io.swagger.v3.oas.annotations.tags.Tag;

              @Tag(name = "foo", description = "Desc")
              class Example {}
              """
          )
        );
    }

    @Test
    void singleTagAsLiteral() {
        rewriteRun(
          //language=java
          java(
            """
              import io.swagger.annotations.Api;

              @Api(tags = "foo", value = "Ignore", description = "Desc")
              class Example {}
              """,
            """
              import io.swagger.v3.oas.annotations.tags.Tag;

              @Tag(name = "foo", description = "Desc")
              class Example {}
              """
          )
        );
    }

    @Test
    void multipleTags() {
        rewriteRun(
          //language=java
          java(
            """
              import io.swagger.annotations.Api;

              @Api(tags = {"foo", "bar"}, value = "Ignore", description = "Desc", hidden = true)
              class Example {}
              """,
            """
              import io.swagger.v3.oas.annotations.Hidden;
              import io.swagger.v3.oas.annotations.tags.Tag;
              import io.swagger.v3.oas.annotations.tags.Tags;

              @Hidden
              @Tags({
                      @Tag(name = "foo", description = "Desc"),
                      @Tag(name = "bar", description = "Desc")
              })
              class Example {}
              """
          )
        );
    }
}
