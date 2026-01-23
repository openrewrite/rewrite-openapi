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
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Annotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class ConvertApiResponseHeadersToHeaders extends Recipe {

    private static final AnnotationMatcher ANNOTATION_MATCHER = new AnnotationMatcher("@io.swagger.v3.oas.annotations.responses.ApiResponse");
    private static final String FQN_HEADER = "io.swagger.v3.oas.annotations.headers.Header";
    private static final String FQN_SCHEMA = "io.swagger.v3.oas.annotations.media.Schema";
    private static final String FQN_REPONSEHEADER = "io.swagger.annotations.ResponseHeader";

    @Getter
    final String displayName = "Convert API responseHeaders to headers";

    @Getter
    final String description = "Add `headers = @Header(name = ...)` to `@ApiResponse`.";

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
                        if (!containsHeaders(an)) {
                            return an;
                        }

                        StringBuilder result = new StringBuilder();
                        List<Expression> args = new ArrayList<>();

                        Expression respHeadersExpresion = preprocessForHeader(an.getArguments(), result, args);

                        result.append("headers = ");
                        if (respHeadersExpresion instanceof J.Annotation) {
                            result.append(generateHeaderEntry((Annotation) respHeadersExpresion, args));
                        } else {
                            List<Expression> headersArray = requireNonNull(((J.NewArray) respHeadersExpresion).getInitializer());
                            result.append("{");
                            for (int i = 0; i < headersArray.size(); i++) {
                                Expression headerArrEntry = headersArray.get(i);
                                if (headerArrEntry instanceof J.Annotation) {
                                    if (i > 0) {
                                        result.append(", ");
                                    }
                                    result.append(generateHeaderEntry((Annotation) headerArrEntry, args));
                                }

                            }
                            result.append("}");
                        }

                        an = JavaTemplate.builder(result.toString())
                                .imports(FQN_HEADER)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-annotations"))
                                .build()
                                .apply(getCursor(), an.getCoordinates().replaceArguments(), args.toArray());
                        maybeRemoveImport(FQN_REPONSEHEADER);
                        maybeAddImport(FQN_HEADER);
                        maybeAddImport(FQN_SCHEMA);

                        return maybeAutoFormat(annotation, an, ctx, getCursor().getParentTreeCursor());
                    }

                    /**
                     * Utility function to short-circuit the parsing if the <code>responseHeaders</code> is not present
                     *
                     * @param annotation    the original <code>ApiResponse</code> annotation being parsed
                     *
                     * @return  whether <code>responseHeaders</code> is an expression contained in the <code>ApiResponse</code> annotation
                     */
                    private boolean containsHeaders(J.Annotation annotation) {

                        if (annotation.getArguments() == null ||
                                annotation.getArguments().isEmpty() ||
                                annotation.getArguments().get(0) instanceof J.Empty) {
                            return false;
                        }

                        for (Expression expression : annotation.getArguments()) {
                            if (expression instanceof J.Assignment) {
                                J.Assignment a = (J.Assignment) expression;
                                if ("responseHeaders".equals(((J.Identifier) a.getVariable()).getSimpleName())) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    }

                    /**
                     * Utility to pre-process the <code>ApiResponse</code> annotation arguments:
                     * it will add to the final result all but the <code>responseHeaders</code> which will be returned
                     *
                     * @param annotationArgs    the list of arguments from the <code>ApiResponse</code> annotation
                     * @param result            the {@link StringBuilder} containing the current parsing result
                     * @param args              the stack of arguments to use for the final result generation
                     *
                     * @return  the {@link Expression} for the <code>responseHeaders</code> <code>ApiResponse</code> annotation
                     */
                    private Expression preprocessForHeader(List<Expression> annotationArgs, StringBuilder result, List<Expression> args) {
                        Expression headersExpression = null;

                        for (Expression arg : annotationArgs) {

                            if (arg instanceof J.Assignment && (((J.Assignment) arg).getVariable() instanceof J.Identifier)) {

                                J.Assignment assign = (J.Assignment) arg;
                                String name = ((J.Identifier) assign.getVariable()).getSimpleName();
                                Expression assignment = assign.getAssignment();

                                if ("responseHeaders".equals(name) && (assignment instanceof J.Annotation) || (assignment instanceof J.NewArray)) {
                                    headersExpression = assignment;
                                } else {
                                    result.append("#{any()}, ");
                                    args.add(arg);
                                }

                            } else {
                                result.append("#{any()}, ");
                                args.add(arg);
                            }

                        }

                        return headersExpression;
                    }

                    /**
                     * Utility function generating the body of a <code>Header</code> annotation
                     *
                     * @param responseHeader    the <code>ResponseHeaders</code> annotation we are converting to <code>Header</code>
                     * @param args              the current arguments stack for the final conversion result, this function will add any new arguments based on its result
                     *
                     * @return                  a string with the body of a <code>Header</code> annotation <code>responseHeaders</code> parameter
                     */
                    private String generateHeaderEntry(J.Annotation responseHeader, List<Expression> args) {
                        Map<String, Expression> headersAnnontationExpr = AnnotationUtils.extractArgumentAssignedExpressions(responseHeader);
                        final String[] stdExpressions = {"name", "description"}; // these are transformed the same way

                        StringBuilder sb = new StringBuilder("@Header(");

                        for (String stdExpression : stdExpressions) {
                            if (headersAnnontationExpr.containsKey(stdExpression)) {
                                if (sb.length() > 8) {
                                    sb.append(", ");
                                }
                                sb.append(stdExpression);
                                sb.append(" = #{any()}");
                                args.add(headersAnnontationExpr.get(stdExpression));
                            }
                        }

                        // response needs to be embedded in a Schema
                        if (headersAnnontationExpr.containsKey("response")) {
                            if (sb.length() > 0) {
                                sb.append(", ");
                            }
                            sb.append("schema = @Schema(implementation = #{any()})");
                            args.add(headersAnnontationExpr.get("response"));
                            maybeAddImport(FQN_SCHEMA);
                        }

                        sb.append(")");

                        return sb.toString();

                    }
                }
        );
    }

}
