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
import java.util.List;
import java.util.Map;

public class MigrateSwaggerDefinitionToOpenAPIDefinition extends Recipe {

    private static final String FQN_SWAGGER_DEFINITION = "io.swagger.annotations.SwaggerDefinition";
    private static final String FQN_OPENAPI_DEFINITION = "io.swagger.v3.oas.annotations.OpenAPIDefinition";
    private static final String FQN_SERVER = "io.swagger.v3.oas.annotations.servers.Server";

    @Override
    public String getDisplayName() {
        return "Migrate from `@SwaggerDefinition` to `@OpenAPIDefinition`";
    }

    @Override
    public String getDescription() {
        return "Migrate from `@SwaggerDefinition` to `@OpenAPIDefinition`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(FQN_SWAGGER_DEFINITION, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    private final AnnotationMatcher annotationMatcher = new AnnotationMatcher(FQN_SWAGGER_DEFINITION);

                    @Override
                    public J.@Nullable Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation ann = super.visitAnnotation(annotation, ctx);

                        if (annotationMatcher.matches(ann)) {
                            Map<String, Expression> args = AnnotationUtils.extractArgumentAssignedExpressions(ann);

                            StringBuilder tpl = new StringBuilder("@OpenAPIDefinition(\n");
                            List<Expression> tplArgs = new ArrayList<>();
                            List<String> parts = new ArrayList<>();

                            Expression basePath = args.get("basePath");
                            Expression host = args.get("host");
                            Expression schemes = args.get("schemes");
                            String servers = "";
                            if (basePath != null && host != null && schemes != null) {
                                tpl.append("servers = {\n");
                                if (schemes instanceof J.FieldAccess) {
                                    servers += "@Server(url = \"" + ((J.FieldAccess) schemes).getSimpleName().toLowerCase() + "://" + host + basePath + "\")";
                                } else if (schemes instanceof J.NewArray) {
                                    for (Expression scheme : ((J.NewArray) schemes).getInitializer()) {
                                        if (!servers.isEmpty()) {
                                            servers += ",\n";
                                        }
                                        String schemeName = ((J.FieldAccess) scheme).getSimpleName().toLowerCase();
                                        servers += "@Server(url = \"" + schemeName + "://" + host + basePath + "\")";
                                    }
                                }
                                servers += "\n}";
                                parts.add(servers);
                            }

                            args.remove("basePath");
                            args.remove("host");
                            args.remove("schemes");
                            args.remove("produces");
                            args.remove("consumes");
                            for (Map.Entry<String, Expression> arg : args.entrySet()) {
                                parts.add(arg.getKey() + " = #{any()}");
                                tplArgs.add(arg.getValue());
                            }
                            tpl.append(String.join(",\n", parts));
                            tpl.append("\n)");

                            ann = JavaTemplate.builder(tpl.toString())
                                    .imports(FQN_OPENAPI_DEFINITION, FQN_SERVER)
                                    .javaParser(JavaParser.fromJavaVersion().classpath("swagger-annotations"))
                                    .build()
                                    .apply(updateCursor(ann), ann.getCoordinates().replace(), tplArgs.toArray());
                            maybeRemoveImport(FQN_SWAGGER_DEFINITION);
                            maybeAddImport(FQN_OPENAPI_DEFINITION, false);
                            maybeAddImport(FQN_SERVER, false);
                            ann = maybeAutoFormat(annotation, ann, ctx);
                        }

                        doAfterVisit(new RemoveUnusedImports().getVisitor());
                        return ann;
                    }
                }
        );
    }
}
