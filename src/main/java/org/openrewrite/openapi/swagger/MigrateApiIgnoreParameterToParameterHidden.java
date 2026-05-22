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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class MigrateApiIgnoreParameterToParameterHidden extends Recipe {

    private static final String FQN_API_IGNORE = "springfox.documentation.annotations.ApiIgnore";
    private static final String FQN_PARAMETER = "io.swagger.v3.oas.annotations.Parameter";
    private static final AnnotationMatcher API_IGNORE_MATCHER = new AnnotationMatcher("@" + FQN_API_IGNORE);

    @Getter
    final String displayName = "Replace springfox `@ApiIgnore` on method parameters with `@Parameter(hidden = true)`";

    @Getter
    final String description = "Springfox's `@ApiIgnore` is commonly placed on framework-injected controller parameters " +
            "(`Principal`, `HttpServletRequest`, `Pageable`, ...). A flat `ChangeType` to `io.swagger.v3.oas.annotations.Hidden` " +
            "produces code that does not compile, because `@Hidden` cannot target parameters. Convert parameter usages directly " +
            "to `@io.swagger.v3.oas.annotations.Parameter(hidden = true)` and leave method/class-level `@ApiIgnore` for the " +
            "subsequent `ChangeType` step.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(FQN_API_IGNORE, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if (!API_IGNORE_MATCHER.matches(a)) {
                    return a;
                }
                Object parent = getCursor().getParentTreeCursor().getValue();
                if (!(parent instanceof J.VariableDeclarations) ||
                        !(getCursor().getParentTreeCursor().getParentTreeCursor().getValue() instanceof J.MethodDeclaration)) {
                    return a;
                }
                maybeRemoveImport(FQN_API_IGNORE);
                maybeAddImport(FQN_PARAMETER);
                return JavaTemplate.builder("@Parameter(hidden = true)")
                        .imports(FQN_PARAMETER)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-annotations-2"))
                        .build()
                        .apply(updateCursor(a), a.getCoordinates().replace());
            }
        });
    }
}
