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
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.HashMap;
import java.util.Map;

@Value
@EqualsAndHashCode(callSuper = false)
public class ConvertApiResponseCodesToStrings extends ScanningRecipe<Map<String, Object>> {

    private static final AnnotationMatcher ANNOTATION_MATCHER = new AnnotationMatcher("@io.swagger.v3.oas.annotations.responses.ApiResponse");

    @Override
    public String getDisplayName() {
        return "Convert API response codes to strings";
    }

    @Override
    public String getDescription() {
        return "Convert API response codes to strings. Handles numeric literals, local constants, " +
               "and field accesses from other classes.";
    }

    @Override
    public Map<String, Object> getInitialValue(ExecutionContext ctx) {
        return new HashMap<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Map<String, Object> acc) {
        // Scan for constant field declarations and store their values
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                // Look for static final int fields with literal initializers
                if (vd.hasModifier(J.Modifier.Type.Static) && vd.hasModifier(J.Modifier.Type.Final)) {
                    JavaType varType = vd.getType();
                    if (isIntegerType(varType)) {
                        for (J.VariableDeclarations.NamedVariable var : vd.getVariables()) {
                            Expression init = var.getInitializer();
                            if (init instanceof J.Literal) {
                                J.Literal literal = (J.Literal) init;
                                if (literal.getValue() instanceof Number) {
                                    JavaType.Variable fieldType = var.getVariableType();
                                    if (fieldType != null && fieldType.getOwner() instanceof JavaType.FullyQualified) {
                                        String key = ((JavaType.FullyQualified) fieldType.getOwner()).getFullyQualifiedName()
                                                     + "." + var.getSimpleName();
                                        acc.put(key, literal.getValue());
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
    public TreeVisitor<?, ExecutionContext> getVisitor(Map<String, Object> acc) {
        return Preconditions.check(
                new UsesType<>("io.swagger.v3.oas.annotations.responses.ApiResponse", true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation an = super.visitAnnotation(annotation, ctx);
                        if (ANNOTATION_MATCHER.matches(an)) {
                            return an.withArguments(ListUtils.map(an.getArguments(),
                                    arg -> maybeReplaceResponseCodeTypeAndValue(arg, acc, getCursor())));
                        }
                        return an;
                    }

                    private Expression maybeReplaceResponseCodeTypeAndValue(Expression arg, Map<String, Object> acc, Cursor cursor) {
                        if (!(arg instanceof J.Assignment)) {
                            return arg;
                        }
                        J.Assignment assignment = (J.Assignment) arg;
                        if (!(assignment.getVariable() instanceof J.Identifier) ||
                            !"responseCode".equals(((J.Identifier) assignment.getVariable()).getSimpleName())) {
                            return arg;
                        }

                        Expression assignedValue = assignment.getAssignment();
                        String stringValue = resolveIntegerValue(assignedValue, acc, cursor);

                        if (stringValue != null) {
                            J.Literal stringLiteral = new J.Literal(
                                    assignedValue.getId(),
                                    assignedValue.getPrefix(),
                                    assignedValue.getMarkers(),
                                    stringValue,
                                    "\"" + stringValue + "\"",
                                    null,
                                    JavaType.Primitive.String
                            );
                            return assignment
                                    .withType(JavaType.Primitive.String)
                                    .withAssignment(stringLiteral);
                        }
                        return arg;
                    }

                    private @Nullable String resolveIntegerValue(Expression expr, Map<String, Object> acc, Cursor cursor) {
                        // Handle numeric literals directly
                        if (expr instanceof J.Literal) {
                            J.Literal literal = (J.Literal) expr;
                            if (literal.getValue() instanceof Number) {
                                return String.valueOf(literal.getValue());
                            }
                            return null;
                        }

                        // Handle identifier references (local constants)
                        if (expr instanceof J.Identifier) {
                            J.Identifier identifier = (J.Identifier) expr;
                            String fieldName = identifier.getSimpleName();

                            // First try from type attribution
                            JavaType.Variable fieldType = identifier.getFieldType();
                            if (fieldType != null && isIntegerType(fieldType.getType())) {
                                String resolved = resolveConstantValue(fieldType, acc);
                                if (resolved != null) {
                                    return resolved;
                                }
                            }

                            // Fall back to searching the enclosing class for a field with this name
                            J.ClassDeclaration classDecl = cursor.firstEnclosing(J.ClassDeclaration.class);
                            if (classDecl != null) {
                                String value = findFieldValueInClass(classDecl, fieldName);
                                if (value != null) {
                                    return value;
                                }
                            }
                            return null;
                        }

                        // Handle field access (e.g., HttpCodes.NOT_FOUND)
                        if (expr instanceof J.FieldAccess) {
                            J.FieldAccess fieldAccess = (J.FieldAccess) expr;
                            JavaType.Variable fieldType = fieldAccess.getName().getFieldType();
                            if (fieldType != null && isIntegerType(fieldType.getType())) {
                                return resolveConstantValue(fieldType, acc);
                            }
                            return null;
                        }

                        return null;
                    }

                    private @Nullable String findFieldValueInClass(J.ClassDeclaration classDecl, String fieldName) {
                        for (Statement stmt : classDecl.getBody().getStatements()) {
                            if (!(stmt instanceof J.VariableDeclarations)) {
                                continue;
                            }
                            J.VariableDeclarations fieldDecl = (J.VariableDeclarations) stmt;
                            if (!fieldDecl.hasModifier(J.Modifier.Type.Static) || !fieldDecl.hasModifier(J.Modifier.Type.Final)) {
                                continue;
                            }
                            for (J.VariableDeclarations.NamedVariable var : fieldDecl.getVariables()) {
                                if (fieldName.equals(var.getSimpleName())) {
                                    Expression init = var.getInitializer();
                                    if (init instanceof J.Literal) {
                                        J.Literal literal = (J.Literal) init;
                                        if (literal.getValue() instanceof Number) {
                                            return String.valueOf(literal.getValue());
                                        }
                                    }
                                }
                            }
                        }
                        return null;
                    }

                    private @Nullable String resolveConstantValue(JavaType.Variable fieldType, Map<String, Object> acc) {
                        JavaType owner = fieldType.getOwner();
                        if (!(owner instanceof JavaType.FullyQualified)) {
                            return null;
                        }
                        String ownerFqn = ((JavaType.FullyQualified) owner).getFullyQualifiedName();
                        String fieldName = fieldType.getName();
                        String key = ownerFqn + "." + fieldName;

                        // First try to get from scanned source files
                        Object scannedValue = acc.get(key);
                        if (scannedValue instanceof Number) {
                            return String.valueOf(scannedValue);
                        }

                        // Fall back to reflection for compiled classes
                        try {
                            Class<?> clazz = Class.forName(ownerFqn);
                            java.lang.reflect.Field field = clazz.getField(fieldName);
                            Object value = field.get(null);
                            if (value instanceof Number) {
                                return String.valueOf(value);
                            }
                        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException |
                                 SecurityException e) {
                            // Class or field not accessible via reflection, cannot resolve
                        }
                        return null;
                    }
                }
        );
    }

    private static boolean isIntegerType(@Nullable JavaType type) {
        return TypeUtils.isOfType(type, JavaType.Primitive.Int) ||
               TypeUtils.isOfType(type, JavaType.Primitive.Long) ||
               TypeUtils.isOfType(type, JavaType.Primitive.Short) ||
               TypeUtils.isOfType(type, JavaType.Primitive.Byte);
    }
}
