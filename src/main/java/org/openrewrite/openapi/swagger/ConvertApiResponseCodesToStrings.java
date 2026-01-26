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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = false)
public class ConvertApiResponseCodesToStrings extends ScanningRecipe<ConvertApiResponseCodesToStrings.ConstantAccumulator> {

    private static final AnnotationMatcher ANNOTATION_MATCHER = new AnnotationMatcher("@io.swagger.v3.oas.annotations.responses.ApiResponse");

    @Getter
    final String displayName = "Convert API response codes to strings";

    @Getter
    final String description = "Convert API response codes to strings. Handles literal integers, " +
            "local constant references, and external constant field accesses.";

    @Override
    public ConstantAccumulator getInitialValue(ExecutionContext ctx) {
        return new ConstantAccumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(ConstantAccumulator acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                // Collect static final int fields with literal initializers
                if (vd.hasModifier(J.Modifier.Type.Static) && vd.hasModifier(J.Modifier.Type.Final)) {
                    JavaType type = vd.getType();
                    if (type == JavaType.Primitive.Int || type == JavaType.Primitive.Long) {
                        for (J.VariableDeclarations.NamedVariable var : vd.getVariables()) {
                            if (var.getInitializer() instanceof J.Literal) {
                                J.Literal literal = (J.Literal) var.getInitializer();
                                if (literal.getValue() instanceof Number) {
                                    JavaType.Variable varType = var.getVariableType();
                                    if (varType != null && varType.getOwner() instanceof JavaType.FullyQualified) {
                                        String key = ((JavaType.FullyQualified) varType.getOwner()).getFullyQualifiedName() + "." + var.getSimpleName();
                                        acc.putConstant(key, String.valueOf(literal.getValue()));
                                    }
                                }
                            }
                        }
                    }
                }
                return vd;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(ConstantAccumulator acc) {
        return Preconditions.check(
                new UsesType<>("io.swagger.v3.oas.annotations.responses.ApiResponse", true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation an = super.visitAnnotation(annotation, ctx);
                        if (ANNOTATION_MATCHER.matches(an)) {
                            return an.withArguments(ListUtils.map(an.getArguments(), arg -> maybeReplaceResponseCodeTypeAndValue(arg, acc)));
                        }
                        return an;
                    }

                    private Expression maybeReplaceResponseCodeTypeAndValue(Expression arg, ConstantAccumulator acc) {
                        if (arg instanceof J.Assignment) {
                            J.Assignment assignment = (J.Assignment) arg;
                            boolean matchesField = assignment.getVariable() instanceof J.Identifier &&
                                    "responseCode".equals(((J.Identifier) assignment.getVariable()).getSimpleName());

                            if (matchesField) {
                                Expression assignedValue = assignment.getAssignment();

                                // Case 1: Literal number (e.g., responseCode = 200)
                                if (assignedValue instanceof J.Literal) {
                                    J.Literal literal = (J.Literal) assignedValue;
                                    if (literal.getValue() instanceof Number) {
                                        return convertToStringLiteral(assignment, String.valueOf(literal.getValue()));
                                    }
                                }

                                // Case 2: Identifier (e.g., responseCode = OK_CODE, referencing local constant)
                                if (assignedValue instanceof J.Identifier) {
                                    J.Identifier ident = (J.Identifier) assignedValue;
                                    String resolvedValue = resolveConstantValue(ident, acc);
                                    if (resolvedValue != null) {
                                        return convertToStringLiteral(assignment, resolvedValue);
                                    }
                                }

                                // Case 3: Field access (e.g., responseCode = StatusCodes.NOT_FOUND)
                                if (assignedValue instanceof J.FieldAccess) {
                                    J.FieldAccess fieldAccess = (J.FieldAccess) assignedValue;
                                    String resolvedValue = resolveFieldAccessValue(fieldAccess, acc);
                                    if (resolvedValue != null) {
                                        return convertToStringLiteral(assignment, resolvedValue);
                                    }
                                }
                            }
                        }
                        return arg;
                    }

                    private J.Assignment convertToStringLiteral(J.Assignment assignment, String value) {
                        J.Literal stringLiteral = new J.Literal(
                                org.openrewrite.Tree.randomId(),
                                assignment.getAssignment().getPrefix(),
                                org.openrewrite.marker.Markers.EMPTY,
                                value,
                                "\"" + value + "\"",
                                null,
                                JavaType.Primitive.String
                        );
                        return assignment
                                .withType(JavaType.Primitive.String)
                                .withAssignment(stringLiteral);
                    }

                    private @Nullable String resolveConstantValue(J.Identifier ident, ConstantAccumulator acc) {
                        JavaType.Variable fieldType = ident.getFieldType();
                        if (fieldType != null && fieldType.getOwner() instanceof JavaType.FullyQualified) {
                            String key = ((JavaType.FullyQualified) fieldType.getOwner()).getFullyQualifiedName() + "." + ident.getSimpleName();
                            return acc.getConstant(key);
                        }
                        return null;
                    }

                    private @Nullable String resolveFieldAccessValue(J.FieldAccess fieldAccess, ConstantAccumulator acc) {
                        JavaType.Variable fieldType = fieldAccess.getName().getFieldType();
                        if (fieldType != null && fieldType.getOwner() instanceof JavaType.FullyQualified) {
                            String key = ((JavaType.FullyQualified) fieldType.getOwner()).getFullyQualifiedName() + "." + fieldAccess.getSimpleName();
                            return acc.getConstant(key);
                        }
                        return null;
                    }
                }
        );
    }

    @Value
    public static class ConstantAccumulator {
        Map<String, String> constants = new HashMap<>();

        public void putConstant(String key, String value) {
            constants.put(key, value);
        }

        public @Nullable String getConstant(String key) {
            return constants.get(key);
        }
    }
}
