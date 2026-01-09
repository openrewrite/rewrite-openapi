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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Annotation;
import org.openrewrite.java.tree.J.Assignment;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class that can be used for migrating values of the <code>ApiParam</code> annotation
 * that are contained as part of the <code>Schema</code> annotation in the target <code>Parameter</code> annotation
 */
@RequiredArgsConstructor
class MigrateApiParamSchemaValue extends JavaIsoVisitor<ExecutionContext> {
    private static final String FQN_SCHEMA = "io.swagger.v3.oas.annotations.media.Schema";
    private static final AnnotationMatcher PARAMETER_ANNOTATION_MATCHER = new AnnotationMatcher("io.swagger.v3.oas.annotations.Parameter");

    private final String attribute;

    @Override
    public Annotation visitAnnotation(Annotation annotation, ExecutionContext ctx) {
        J.Annotation anno = super.visitAnnotation(annotation, ctx);

        if (!PARAMETER_ANNOTATION_MATCHER.matches(anno)) {
            return anno;
        }

        StringBuilder tpl = new StringBuilder();
        List<Expression> args = new ArrayList<>();

        SchemaInfo schemaInfo = new SchemaInfo();

        for (Expression exp : anno.getArguments()) {
            if (isInteresingVble(exp)) {
                Expression expression = ((J.Assignment) exp).getAssignment();
                String schema = createSchema(attribute);
                schemaInfo.genSchema(schema, expression);
            } else {
                if (isSchemaAssignment(exp)) {
                    schemaInfo.existingSchema((Assignment) exp);
                } else {
                    tpl.append("#{any()}, ");
                    args.add(exp);
                }
            }
        }

        schemaInfo.process(tpl, args);

        if (tpl.toString().endsWith(", ")) {
            tpl.delete(tpl.length() - 2, tpl.length());
        }

        anno = JavaTemplate.builder(tpl.toString())
                .imports(FQN_SCHEMA)
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-annotations"))
                .build()
                .apply(updateCursor(anno), annotation.getCoordinates().replaceArguments(), args.toArray());
        maybeAddImport(FQN_SCHEMA, false);
        return maybeAutoFormat(annotation, anno, ctx, getCursor().getParentTreeCursor());
    }

    /**
     * Utility method checking whether a certain expression is the
     * <code>schema</code> assignment
     *
     * @param expr the {@link Expression} being processed
     * @return whether the expression is a <code>schema</code> {@link J.Assignment}
     * one
     */
    private boolean isSchemaAssignment(Expression expr) {
        if (expr instanceof J.Assignment) {
            Expression vble = ((J.Assignment) expr).getVariable();
            if (vble instanceof J.Identifier) {
                return "schema".equals(((J.Identifier) vble).getSimpleName());
            }
        }
        return false;
    }

    /**
     * Creates the string for the <code>schema</code> declaration
     *
     * @param schemaVble the schema variable assignment to add
     * @return a string with the <code>schema</code> variable declaration for the <code>@Schema</code> annotation
     */
    private String createSchema(String schemaVble) {
        return String.format("schema = @Schema(%s = #{any()} ", schemaVble);
    }

    /**
     * Utility method checking whether this expression is the one for the variable we are migrating
     *
     * @param exp the {@link Expression} being processed
     * @return    if the identifier of the expression matches the one expected to migrate
     */
    private boolean isInteresingVble(Expression exp) {
        return exp instanceof J.Assignment && attribute.equals(((J.Identifier) ((J.Assignment) exp).getVariable()).getSimpleName());
    }

    /**
     * Utility class that holds a mapping of the {@link Expression} being migrated (and its generated <code>schema</code> string),
     * with a potential already existing <code>schema</code> entry to merge the information
     */
    private static class SchemaInfo {

        private @Nullable Expression schemaExpr;
        private @Nullable String schemaStr;
        private J.@Nullable Assignment existingSchemaExpr;

        private void genSchema(String schemaStr, Expression schemaExpr) {
            this.schemaExpr = schemaExpr;
            this.schemaStr = schemaStr;
        }

        private void existingSchema(J.Assignment existingSchemaExpr) {
            this.existingSchemaExpr = existingSchemaExpr;
        }

        /**
         * Contains the logic for merging the resulting <code>schema</code>
         *
         * @param tpl  holds the current result of the annotation processing
         * @param args holds the current stack of arguments for the annotation
         *             processed
         */
        private void process(StringBuilder tpl, List<Expression> args) {
            if (schemaStr == null) {
                if (existingSchemaExpr != null) {
                    args.add(existingSchemaExpr);
                }
            } else {
                tpl.append(schemaStr);
                if (existingSchemaExpr != null && existingSchemaExpr.getAssignment() instanceof Annotation) {
                    for (Expression schemaArg : ((Annotation) existingSchemaExpr.getAssignment()).getArguments()) {
                        tpl.append(", ").append(schemaArg);
                    }
                }
                tpl.append(")");
                args.add(schemaExpr);
            }
        }
    }
}
