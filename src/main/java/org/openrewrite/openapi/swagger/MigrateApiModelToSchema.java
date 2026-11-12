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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.openrewrite.openapi.swagger.AnnotationUtils.extractArgumentAssignments;

public class MigrateApiModelToSchema extends Recipe {

    private static final String API_MODEL_FQN = "io.swagger.annotations.ApiModel";
    private static final String SCHEMA_FQN = "io.swagger.v3.oas.annotations.media.Schema";

    private static final AnnotationMatcher API_MODEL_MATCHER = new AnnotationMatcher(API_MODEL_FQN);
    private static final AnnotationMatcher SCHEMA_MATCHER = new AnnotationMatcher(SCHEMA_FQN);

    @Getter
    final String displayName = "Migrate from `@ApiModel` to `@Schema`";

    @Getter
    final String description = "Converts the `@ApiModel` annotation to `@Schema` and converts the \"value\" attribute to \"name\".";

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
                            J.Assignment positionalValue = positionalValue(annotation);
                            if (positionalValue != null) {
                                annotationAssignments.put("value", positionalValue);
                            }
                            if (annotationAssignments.containsKey("value")) {
                                J.Assignment value = annotationAssignments.remove("value");
                                J.Identifier name = ((J.Identifier) value.getVariable()).withSimpleName("name");
                                if (name.getType() instanceof JavaType.Method) {
                                    name = name.withType(((JavaType.Method) name.getType()).withName("name"));
                                }
                                annotationAssignments.put("name", value.withVariable(name));
                            }

                            // Handle 'reference' attribute migration
                            handleReferenceAttribute(annotationAssignments);

                            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, API_MODEL_FQN, annotationAssignments);
                        } else if (SCHEMA_MATCHER.matches(annotation)) {
                            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, SCHEMA_FQN, "USING SCHEMA ALREADY");
                        }
                    }

                    return annotation;
                }

                private J.@Nullable Assignment positionalValue(J.Annotation annotation) {
                    List<Expression> arguments = annotation.getArguments();
                    if (arguments == null || arguments.size() != 1 ||
                            arguments.get(0) instanceof J.Assignment || arguments.get(0) instanceof J.Empty) {
                        return null;
                    }
                    Expression value = arguments.get(0);
                    JavaType.Method valueMethod = TypeUtils.findDeclaredMethod(
                            TypeUtils.asFullyQualified(annotation.getType()), "value", emptyList()).orElse(null);
                    J.Identifier name = new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(),
                            "value", valueMethod, null);
                    return new J.Assignment(Tree.randomId(), value.getPrefix().withWhitespace(" "), Markers.EMPTY, name,
                            new JLeftPadded<>(Space.SINGLE_SPACE, value.withPrefix(Space.SINGLE_SPACE), Markers.EMPTY),
                            value.getType());
                }

                /**
                 * Handle the 'reference' attribute from @ApiModel.
                 * - If the value looks like a URL, rename to 'ref'
                 * - If the value looks like a class name, schedule conversion to 'implementation = ClassName.class'
                 */
                private void handleReferenceAttribute(Map<String, J.Assignment> annotationAssignments) {
                    if (!annotationAssignments.containsKey("reference")) {
                        return;
                    }

                    J.Assignment referenceAssignment = annotationAssignments.get("reference");
                    if (!(referenceAssignment.getAssignment() instanceof J.Literal)) {
                        return; // Not a string literal, leave as-is (will likely fail compilation anyway)
                    }
                    J.Literal literal = (J.Literal) referenceAssignment.getAssignment();
                    if (literal.getValue() == null) {
                        return;
                    }
                    String referenceValue = literal.getValue().toString();
                    if (referenceValue.contains("://") || referenceValue.contains("#")) {
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
                                List<Expression> arguments = ListUtils.concatAll(
                                        ListUtils.map(annotation.getArguments(), a -> a instanceof J.Empty ? null : a),
                                        new ArrayList<>(annotationAssignments.values()));
                                return autoFormat(annotation.withArguments(arguments), ctx);
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

                // Replace the 'reference' attribute with 'implementation = ClassName.class'
                return ann.withArguments(ListUtils.map(ann.getArguments(), arg -> {
                    if (arg instanceof J.Assignment) {
                        J.Assignment assign = (J.Assignment) arg;
                        if (assign.getVariable() instanceof J.Identifier &&
                                "reference".equals(((J.Identifier) assign.getVariable()).getSimpleName())) {
                            return JavaTemplate.builder("implementation = " + referenceValue + ".class")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-annotations-2"))
                                    .build()
                                    .apply(new Cursor(getCursor(), arg), arg.getCoordinates().replace());
                        }
                    }
                    return arg;
                }));
            }
        };
    }
}
