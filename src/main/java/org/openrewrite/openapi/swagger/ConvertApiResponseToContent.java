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

import org.jspecify.annotations.Nullable;
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
    private static final String FQN_ARRAYSCHEMA = "io.swagger.v3.oas.annotations.media.ArraySchema";

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
                        if (!ANNOTATION_MATCHER.matches(an) || an.getArguments() == null) {
                            return an;
                        }
                        AtomicReference<J.FieldAccess> contentClass = new AtomicReference<>();
                        AtomicReference<J.@Nullable Literal> containerType = new AtomicReference<>();

                        List<Expression> maybeArgsWithoutResponse = ListUtils.map(an.getArguments(), arg -> {
                            if (!(arg instanceof J.Assignment)) {
                                return arg;
                            }
                            J.Assignment assign = (J.Assignment) arg;
                            if (!(assign.getVariable() instanceof J.Identifier)) {
                                return arg;
                            }
                            String name = ((J.Identifier) assign.getVariable()).getSimpleName();
                            Expression assignment = assign.getAssignment();
                            if ("response".equals(name) && assignment instanceof J.FieldAccess) {
                                contentClass.set((J.FieldAccess) assignment);
                                return null;
                            }
                            if ("responseContainer".equals(name) && assignment instanceof J.Literal) {
                                containerType.set((J.Literal) assignment);
                                return null;
                            }
                            return arg;
                        });

                        if (maybeArgsWithoutResponse.size() >= an.getArguments().size()) {
                            return an;
                        }
                        String inner;
                        String type = containerType.get() != null ? containerType.get().toString() : null;
                        // 1) list/set case: wrap in ArraySchema
                        if ("List".equals(type) || "Set".equals(type)) {
                            inner = String.format(
                                    "array = @ArraySchema(uniqueItems = %b, schema = @Schema(implementation = #{any()})))",
                                    "Set".equals(type)
                            );
                            // 2) map case: wrap Schema in Schema
                        } else if ("Map".equals(type)) {
                            inner = "schema = @Schema(type = \"object\", additionalPropertiesSchema = #{any()}))";
                            // 3) absent responseContainer case
                        } else {
                            inner = "schema = @Schema(implementation = #{any()}))";
                        }
                        String arguments = StringUtils.repeat("#{any()}, ", maybeArgsWithoutResponse.size());
                        an = JavaTemplate.builder(arguments + "content = @Content(mediaType = \"application/json\", " + inner)
                                .imports("io.swagger.v3.oas.annotations.media.*")
                                .javaParser(JavaParser.fromJavaVersion().classpath("swagger-annotations"))
                                .build()
                                .apply(
                                        getCursor(),
                                        an.getCoordinates().replaceArguments(),
                                        ListUtils.concat(maybeArgsWithoutResponse, contentClass.get()).toArray()
                                );
                        maybeAddImport(FQN_CONTENT);
                        maybeAddImport(FQN_SCHEMA);
                        maybeAddImport(FQN_ARRAYSCHEMA);

                        return maybeAutoFormat(annotation, an, ctx, getCursor().getParentTreeCursor());
                    }
                }
        );
    }
}
