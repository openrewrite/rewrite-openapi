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

import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

public class MigrateApiToTag extends Recipe {

    private static final String FQN_API = "io.swagger.annotations.Api";
    private static final String FQN_TAG = "io.swagger.v3.oas.annotations.tags.Tag";
    private static final String FQN_TAGS = "io.swagger.v3.oas.annotations.tags.Tags";

    @Language("java")
    private static final String TAGS_CLASS = "package io.swagger.v3.oas.annotations.tags;\n" +
                                             "import java.lang.annotation.ElementType;\n" +
                                             "import java.lang.annotation.Retention;\n" +
                                             "import java.lang.annotation.RetentionPolicy;\n" +
                                             "import java.lang.annotation.Target;\n" +
                                             "@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})\n" +
                                             "@Retention(RetentionPolicy.RUNTIME)\n" +
                                             "public @interface Tags {\n" +
                                             "    Tag[] value() default {};\n" +
                                             "}";
    @Language("java")
    private static final String TAG_CLASS = "package io.swagger.v3.oas.annotations.tags;\n" +
                                            "import java.lang.annotation.ElementType;\n" +
                                            "import java.lang.annotation.Repeatable;\n" +
                                            "import java.lang.annotation.Retention;\n" +
                                            "import java.lang.annotation.RetentionPolicy;\n" +
                                            "import java.lang.annotation.Target;\n" +
                                            "@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})\n" +
                                            "@Retention(RetentionPolicy.RUNTIME)\n" +
                                            "@Repeatable(Tags.class)\n" +
                                            "public @interface Tag {\n" +
                                            "    String name();\n" +
                                            "    String description() default \"\";\n" +
                                            "}";

    @Override
    public String getDisplayName() {
        return "Migrate from `@Api` to `@Tag`";
    }

    @Override
    public String getDescription() {
        return "Converts `@Api` to `@Tag` annotation and converts the directly mappable attributes and removes the others.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(FQN_API, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    private final AnnotationMatcher apiMatcher = new AnnotationMatcher(FQN_API);

                    @Override
                    public J.@Nullable Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation ann = super.visitAnnotation(annotation, ctx);
                        if (apiMatcher.matches(ann)) {
                            Map<String, Expression> annotationArgumentAssignments = extractAnnotationArgumentAssignments(ann);
                            Expression tagsAssignment = annotationArgumentAssignments.get("tags");
                            if (tagsAssignment instanceof J.NewArray) {
                                List<Expression> initializer = ((J.NewArray) tagsAssignment).getInitializer();
                                if (initializer != null && 1 < initializer.size()) {
                                    // Remove @Api and add @Tag or @Tags at class level
                                    getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, FQN_API, annotationArgumentAssignments);
                                    maybeRemoveImport(FQN_API);
                                    return null;
                                }
                            }
                            doAfterVisit(new ChangeAnnotationAttributeName(FQN_API, "value", "name").getVisitor());
                            doAfterVisit(new ChangeType(FQN_API, FQN_TAG, true).getVisitor());
                        }
                        return ann;
                    }

                    private Map<String, Expression> extractAnnotationArgumentAssignments(J.Annotation apiAnnotation) {
                        if (apiAnnotation.getArguments() == null ||
                            apiAnnotation.getArguments().isEmpty() ||
                            apiAnnotation.getArguments().get(0) instanceof J.Empty) {
                            return emptyMap();
                        }
                        Map<String, Expression> map = new HashMap<>();
                        for (Expression expression : apiAnnotation.getArguments()) {
                            if (expression instanceof J.Assignment) {
                                J.Assignment a = (J.Assignment) expression;
                                String simpleName = ((J.Identifier) a.getVariable()).getSimpleName();
                                map.put(simpleName, a.getAssignment());
                            }
                        }
                        return map;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                        Map<String, Expression> annotationArguments = getCursor().getMessage(FQN_API);
                        if (annotationArguments == null) {
                            return cd;
                        }

                        Expression descriptionAssignment = annotationArguments.get("description");
                        J.NewArray tagsAssignment = (J.NewArray) annotationArguments.get("tags");

                        // Create template for @Tags annotation
                        StringBuilder template = new StringBuilder("@Tags({");
                        List<Expression> templateArgs = new ArrayList<>();
                        for (Expression expression : requireNonNull(tagsAssignment.getInitializer())) {
                            if (!templateArgs.isEmpty()) {
                                template.append(",");
                            }
                            template.append("\n@Tag(name = #{any()}");
                            templateArgs.add(expression);
                            if (descriptionAssignment != null) {
                                template.append(", description = #{any()}");
                                templateArgs.add(descriptionAssignment);
                            }
                            template.append(")");
                        }
                        template.append("\n})");

                        // Add formatted template and imports
                        maybeAddImport(FQN_TAG);
                        maybeAddImport(FQN_TAGS);
                        J.ClassDeclaration applied = JavaTemplate.builder(template.toString())
                                .imports(FQN_TAGS, FQN_TAG)
                                .javaParser(JavaParser.fromJavaVersion().dependsOn(TAGS_CLASS, TAG_CLASS))
                                .build()
                                .apply(updateCursor(cd), cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)), templateArgs.toArray());
                        return maybeAutoFormat(classDecl, applied, applied.getName(), ctx, getCursor().getParentTreeCursor());
                    }
                }
        );
    }
}
