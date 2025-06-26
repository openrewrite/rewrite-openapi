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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeAnnotationAttributeName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;

public class MigrateApiModelToSchema extends Recipe {

    private static final String API_MODEL_FQN = "io.swagger.annotations.ApiModel";
    private static final String SCHEMA_FQN = "io.swagger.v3.oas.annotations.media.Schema";

    @Override
    public String getDisplayName() {
        return "Migrate from `@ApiModel` to `@Schema`";
    }

    @Override
    public String getDescription() {
        return "Converts the `@ApiModel` annotation to `@Schema` and converts the \"value\" attribute to \"name\".";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
            new UsesType<>(API_MODEL_FQN, false),
            new JavaIsoVisitor<ExecutionContext>() {
                private final AnnotationMatcher apiModelMatcher = new AnnotationMatcher(API_MODEL_FQN);
                private final AnnotationMatcher schemaMatcher = new AnnotationMatcher(SCHEMA_FQN);

                private final TreeVisitor<?, ExecutionContext> changeAttribute = new ChangeAnnotationAttributeName(API_MODEL_FQN, "value", "name").getVisitor();
                private final TreeVisitor<?, ExecutionContext> changeType = new ChangeType(API_MODEL_FQN, SCHEMA_FQN, true).getVisitor();

                @Override
                public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                    if (getCursor().getParent().getValue() instanceof J.ClassDeclaration) {
                        annotation = super.visitAnnotation(annotation, ctx);
                        if (apiModelMatcher.matches(annotation)) {
                            doAfterVisit(changeAttribute);
                            doAfterVisit(changeType);

                            Map<String, J.Assignment> annotationAssignments = extractArgumentAssignments(annotation);
                            if (annotationAssignments.containsKey("value")) {
                                J.Assignment value = annotationAssignments.remove("value");
                                annotationAssignments.put("name", value.withVariable(((J.Identifier) value.getVariable()).withSimpleName("name")));
                            }
                            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, API_MODEL_FQN, annotationAssignments);
                        } else if (schemaMatcher.matches(annotation)) {
                            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, SCHEMA_FQN, "USING SCHEMA ALREADY");
                        }
                    }

                    return annotation;
                }

                @Override
                public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                    J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                    Map<String, Expression> annotationAssignments = getCursor().getMessage(API_MODEL_FQN);
                    if (annotationAssignments == null) {
                        return cd;
                    }

                    boolean schemaAnnotationAlreadyPresent = getCursor().getMessage(SCHEMA_FQN) != null;

                    cd = cd.withLeadingAnnotations(ListUtils.map(cd.getLeadingAnnotations(), annotation -> {
                        if (annotation == null || (schemaAnnotationAlreadyPresent && apiModelMatcher.matches(annotation))) {
                            return null;
                        } else if (schemaMatcher.matches(annotation)) {
                            AnnotationUtils.extractArgumentAssignments(annotation).keySet().forEach(annotationAssignments::remove);
                            if (!annotationAssignments.isEmpty()) {
                                return autoFormat(annotation.withArguments(ListUtils.concatAll(annotation.getArguments(), new ArrayList<>(annotationAssignments.values()))), ctx);
                            }
                        }
                        return annotation;
                    }));
                    cd = cd.withLeadingAnnotations(ListUtils.mapFirst(cd.getLeadingAnnotations(), annotation -> annotation == null ? null : annotation.withPrefix(Space.EMPTY)));
                    return cd;
                }
            }
        );
    }

    //TODO as we have a AnnotationUtils, I feel we should move it there. The other one unwraps the assignments to their assigned value causing it to force me to build them again if I would use that one.
    private static Map<String, J.Assignment> extractArgumentAssignments(J.Annotation annotation) {
        if (annotation.getArguments() == null ||
                annotation.getArguments().isEmpty() ||
                annotation.getArguments().get(0) instanceof J.Empty) {
            return emptyMap();
        }
        Map<String, J.Assignment> map = new HashMap<>();
        for (Expression expression : annotation.getArguments()) {
            if (expression instanceof J.Assignment) {
                J.Assignment a = (J.Assignment) expression;
                String simpleName = ((J.Identifier) a.getVariable()).getSimpleName();
                map.put(simpleName, a);
            }
        }
        return map;
    }
}
