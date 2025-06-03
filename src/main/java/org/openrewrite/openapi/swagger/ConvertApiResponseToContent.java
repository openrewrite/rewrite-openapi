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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ConvertApiResponseToContent extends Recipe {

    private static final AnnotationMatcher ANNOTATION_MATCHER = new AnnotationMatcher("@io.swagger.v3.oas.annotations.responses.ApiResponse");
    private static final String FQN_CONTENT = "io.swagger.v3.oas.annotations.media.Content";
    private static final String FQN_SCHEMA = "io.swagger.v3.oas.annotations.media.Schema";

    @Override
    public String getDisplayName() {
        return "Convert API response to content annotation";
    }

    @Override
    public String getDescription() {
        return "Convert API response to content annotation";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("io.swagger.v3.oas.annotations.responses.ApiResponse", true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation an = super.visitAnnotation(annotation, ctx);
                        if (ANNOTATION_MATCHER.matches(an) && an.getArguments() != null) {
                            AtomicReference<J.FieldAccess> contentClass = new AtomicReference<>();
                            List<Expression> maybeArgsWithoutResponse = ListUtils.map(an.getArguments(), arg -> {
                                if (arg instanceof J.Assignment) {
                                    J.Assignment assignment = (J.Assignment) arg;
                                    if (assignment.getVariable() instanceof J.Identifier &&
                                            "response".equals(((J.Identifier) assignment.getVariable()).getSimpleName()) &&
                                            assignment.getAssignment() instanceof J.FieldAccess) {
                                        contentClass.set((J.FieldAccess) assignment.getAssignment());
                                        return null;
                                    }
                                }
                                return arg;
                            });

                            if (an.getArguments().size() > maybeArgsWithoutResponse.size()) {
                                String arguments = StringUtils.repeat("#{any()}, ", maybeArgsWithoutResponse.size());
                                an = JavaTemplate.builder(arguments + "content = @Content(mediaType = \"application/json\", schema = @Schema(implementation = #{any()}))")
                                        .imports(FQN_CONTENT, FQN_SCHEMA)
                                        .javaParser(JavaParser.fromJavaVersion().classpath("swagger-annotations"))
                                        .build()
                                        .apply(updateCursor(an), an.getCoordinates().replaceArguments(), ListUtils.concat(maybeArgsWithoutResponse, contentClass.get()).toArray());
                                maybeAddImport(FQN_CONTENT);
                                maybeAddImport(FQN_SCHEMA);
                                return maybeAutoFormat(annotation, an, ctx, getCursor().getParentTreeCursor());
                            }
                        }
                        return an;
                    }
                });
    }
}
