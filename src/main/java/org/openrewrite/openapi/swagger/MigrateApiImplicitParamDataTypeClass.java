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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;

public class MigrateApiImplicitParamDataTypeClass extends Recipe {
    private static final String FQN_SCHEMA = "io.swagger.v3.oas.annotations.media.Schema";

    @Override
    public String getDisplayName() {
        return "Migrate `@ApiImplicitParam(dataTypeClass=Foo.class)` to `@Parameter(schema=@Schema(implementation=Foo.class))`";
    }

    @Override
    public String getDescription() {
        return "Migrate `@ApiImplicitParam(dataTypeClass=Foo.class)` to `@Parameter(schema=@Schema(implementation=Foo.class))`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // This recipe is after ChangeType recipe
        return Preconditions.check(
                new UsesMethod<>("io.swagger.annotations.ApiImplicitParam dataTypeClass()", false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation anno = super.visitAnnotation(annotation, ctx);

                        if (!new AnnotationMatcher("io.swagger.v3.oas.annotations.Parameter").matches(anno)) {
                            return anno;
                        }

                        StringBuilder tpl = new StringBuilder();
                        List<Expression> args = new ArrayList<>();
                        for (Expression exp : anno.getArguments()) {
                            if (!args.isEmpty()) {
                                tpl.append(", ");
                            }
                            if (isDataTypeClass(exp)) {
                                J.FieldAccess fieldAccess = (J.FieldAccess) ((J.Assignment) exp).getAssignment();
                                tpl.append("schema = @Schema(implementation = #{any()})");
                                args.add(fieldAccess);
                            } else {
                                tpl.append("#{any()}");
                                args.add(exp);
                            }
                        }
                        anno = JavaTemplate.builder(tpl.toString())
                                .imports(FQN_SCHEMA)
                                .javaParser(JavaParser.fromJavaVersion().classpath("swagger-annotations"))
                                .build()
                                .apply(updateCursor(anno), annotation.getCoordinates().replaceArguments(), args.toArray());
                        maybeAddImport(FQN_SCHEMA, false);
                        return maybeAutoFormat(annotation, anno, ctx, getCursor().getParentTreeCursor());
                    }

                    private boolean isDataTypeClass(Expression exp) {
                        return exp instanceof J.Assignment && ((J.Identifier) ((J.Assignment) exp).getVariable()).getSimpleName().equals("dataTypeClass");
                    }
                }
        );
    }
}
