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

import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeAnnotationAttributeName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.openrewrite.openapi.swagger.AnnotationUtils.extractArgumentAssignments;

public class MigrateApiModelToSchema extends Recipe {

    private static final String API_MODEL_FQN = "io.swagger.annotations.ApiModel";
    private static final String SCHEMA_FQN = "io.swagger.v3.oas.annotations.media.Schema";

    private static final AnnotationMatcher API_MODEL_MATCHER = new AnnotationMatcher(API_MODEL_FQN);
    private static final AnnotationMatcher SCHEMA_MATCHER = new AnnotationMatcher(SCHEMA_FQN);

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
                @Override
                public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                    if (getCursor().getParent() != null && getCursor().getParent().getValue() instanceof J.ClassDeclaration) {
                        annotation = super.visitAnnotation(annotation, ctx);
                        if (API_MODEL_MATCHER.matches(annotation)) {
                            doAfterVisit(new ChangeAnnotationAttributeName(API_MODEL_FQN, "value", "name").getVisitor());
                            doAfterVisit(new ChangeType(API_MODEL_FQN, SCHEMA_FQN, true).getVisitor());

                            Map<String, J.Assignment> annotationAssignments = extractArgumentAssignments(annotation);
                            if (annotationAssignments.containsKey("value")) {
                                J.Assignment value = annotationAssignments.remove("value");
                                annotationAssignments.put("name", value.withVariable(((J.Identifier) value.getVariable()).withSimpleName("name")));
                            }

                            // Handle 'reference' attribute migration
                            handleReferenceAttribute(annotation, annotationAssignments, ctx);

                            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, API_MODEL_FQN, annotationAssignments);
                        } else if (SCHEMA_MATCHER.matches(annotation)) {
                            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, SCHEMA_FQN, "USING SCHEMA ALREADY");
                        }
                    }

                    return annotation;
                }

                /**
                 * Handle the 'reference' attribute from @ApiModel.
                 * - If the value looks like a URL, rename to 'ref'
                 * - If the value looks like a class name, schedule conversion to 'implementation = ClassName.class'
                 */
                private void handleReferenceAttribute(J.Annotation annotation, Map<String, J.Assignment> annotationAssignments, ExecutionContext ctx) {
                    if (!annotationAssignments.containsKey("reference")) {
                        return;
                    }

                    J.Assignment referenceAssignment = annotationAssignments.get("reference");
                    Expression assignmentExpr = referenceAssignment.getAssignment();

                    if (!(assignmentExpr instanceof J.Literal)) {
                        // Not a string literal, leave as-is (will likely fail compilation anyway)
                        return;
                    }

                    J.Literal literal = (J.Literal) assignmentExpr;
                    if (literal.getValue() == null) {
                        return;
                    }

                    String referenceValue = literal.getValue().toString();

                    if (referenceValue.contains("://")) {
                        // It's a URL - rename 'reference' to 'ref'
                        // Use SCHEMA_FQN because ChangeType runs before this, converting to @Schema
                        doAfterVisit(new ChangeAnnotationAttributeName(SCHEMA_FQN, "reference", "ref").getVisitor());
                    } else {
                        // It's a class name - schedule a visitor to convert to 'implementation = ClassName.class'
                        // This runs after ChangeType converts @ApiModel to @Schema
                        doAfterVisit(typeReferenceVisitor(referenceValue));
                    }
                    // Remove from map so it won't be added again during merge
                    annotationAssignments.remove("reference");
                }

                @Override
                public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                    J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                    Map<String, Expression> annotationAssignments = getCursor().getMessage(API_MODEL_FQN);
                    if (annotationAssignments == null) {
                        return cd;
                    }

                    boolean schemaAnnotationAlreadyPresent = getCursor().getMessage(SCHEMA_FQN) != null;

                    List<J.Annotation> newLeading = ListUtils.map(cd.getLeadingAnnotations(), annotation -> {
                        if (schemaAnnotationAlreadyPresent && API_MODEL_MATCHER.matches(annotation)) {
                            return null;
                        }
                        if (SCHEMA_MATCHER.matches(annotation)) {
                            AnnotationUtils.extractArgumentAssignedExpressions(annotation).keySet().forEach(annotationAssignments::remove);
                            if (!annotationAssignments.isEmpty()) {
                                return autoFormat(annotation.withArguments(ListUtils.concatAll(annotation.getArguments(), new ArrayList<>(annotationAssignments.values()))), ctx);
                            }
                        }
                        return annotation;
                    });
                    return cd.withLeadingAnnotations(ListUtils.mapFirst(newLeading, annotation -> annotation.withPrefix(Space.EMPTY)));
                }
            }
        );
    }

    private JavaIsoVisitor<ExecutionContext> typeReferenceVisitor(String referenceValue) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation ann = super.visitAnnotation(annotation, ctx);

                if (!SCHEMA_MATCHER.matches(ann) || ann.getArguments() == null) {
                    return ann;
                }

                boolean hasReference = ann.getArguments().stream()
                        .filter(arg -> arg instanceof J.Assignment)
                        .map(arg -> (J.Assignment) arg)
                        .anyMatch(assign -> assign.getVariable() instanceof J.Identifier &&
                                "reference".equals(((J.Identifier) assign.getVariable()).getSimpleName()));

                if (!hasReference) {
                    return ann;
                }

                // Remove the 'reference' attribute
                List<Expression> filteredArgs = ListUtils.map(ann.getArguments(), arg -> {
                    if (arg instanceof J.Assignment) {
                        J.Assignment assign = (J.Assignment) arg;
                        if (assign.getVariable() instanceof J.Identifier &&
                                "reference".equals(((J.Identifier) assign.getVariable()).getSimpleName())) {
                            return null;
                        }
                    }
                    return arg;
                });

                // Build the new annotation with 'implementation = ClassName.class'
                String args = filteredArgs.isEmpty() ? "" :
                        filteredArgs.stream()
                                .map(Object::toString)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("");
                String template = args.isEmpty() ?
                        "implementation = " + referenceValue + ".class" :
                        args + ", implementation = " + referenceValue + ".class";

                return JavaTemplate.builder(template)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-annotations"))
                        .build()
                        .apply(getCursor(), ann.getCoordinates().replaceArguments());
            }
        };
    }
}
