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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

public class ConvertApiResponseCodesToStrings extends Recipe {

    private static final AnnotationMatcher ANNOTATION_MATCHER = new AnnotationMatcher("@io.swagger.v3.oas.annotations.responses.ApiResponse");

    @Getter
    final String displayName = "Convert API response codes to strings";

    @Getter
    final String description = "Convert API response codes to strings.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // https://docs.swagger.io/swagger-core/v1.5.0/apidocs/io/swagger/annotations/ApiResponse.html
        // https://docs.swagger.io/swagger-core/v2.0.0/apidocs/io/swagger/v3/oas/annotations/responses/ApiResponse.html
        return Preconditions.check(
                new UsesType<>("io.swagger.v3.oas.annotations.responses.ApiResponse", true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation an = super.visitAnnotation(annotation, ctx);
                        if (ANNOTATION_MATCHER.matches(an)) {
                            return an.withArguments(ListUtils.map(an.getArguments(), this::maybeReplaceResponseCodeTypeAndValue));
                        }
                        return an;
                    }

                    private Expression maybeReplaceResponseCodeTypeAndValue(Expression arg) {
                        if (arg instanceof J.Assignment) {
                            J.Assignment assignment = (J.Assignment) arg;
                            boolean matchesField = assignment.getVariable() instanceof J.Identifier &&
                                                   "responseCode".equals(((J.Identifier) assignment.getVariable()).getSimpleName());
                            boolean usesNumberLiteral = assignment.getAssignment() instanceof J.Literal &&
                                                        ((J.Literal) assignment.getAssignment()).getValue() instanceof Number;
                            if (matchesField && usesNumberLiteral) {
                                J.Literal assignedLiteral = (J.Literal) assignment.getAssignment();
                                return assignment
                                        .withType(JavaType.Primitive.String)
                                        .withAssignment(assignedLiteral
                                                .withValue(String.valueOf(assignedLiteral.getValue()))
                                                .withValueSource("\"" + assignedLiteral.getValue() + "\"")
                                                .withType(JavaType.Primitive.String));
                            }
                        }
                        return arg;
                    }
                }
        );
    }
}
