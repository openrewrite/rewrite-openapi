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

public class MigrateApiImplicitParam extends Recipe {
    private static final String FQN_SCHEMA = "io.swagger.v3.oas.annotations.media.Schema";

    @Override
    public String getDisplayName() {
        return "Migrate `@ApiImplicitParam` to `@Parameter`";
    }

    @Override
    public String getDescription() {
        return "Migrate `@ApiImplicitParam` to `@Parameter`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // This recipe is after ChangeType recipe
        return Preconditions.check(
          Preconditions.or(
            new UsesMethod<>("io.swagger.annotations.ApiImplicitParam dataTypeClass()", false),
            new UsesMethod<>("io.swagger.annotations.ApiImplicitParam defaultValue()", false)
          ),
          new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                  J.Annotation anno = super.visitAnnotation(annotation, ctx);

                  if (!new AnnotationMatcher("io.swagger.v3.oas.annotations.Parameter").matches(anno)) {
                      return anno;
                  }

                  StringBuilder tpl = new StringBuilder();
                  StringBuilder schemaTpl = new StringBuilder();
                  List<Expression> schemaArgs = new ArrayList<>();
                  List<Expression> args = new ArrayList<>();
                  for (Expression exp : anno.getArguments()) {
                      if (isDataTypeClass(exp)) {
                          Expression expression = ((J.Assignment) exp).getAssignment();
                          addSchema(schemaTpl, "implementation");
                          schemaArgs.add(expression);
                      } else if (isDefaultValue(exp)) {
                          Expression expression = ((J.Assignment) exp).getAssignment();
                          addSchema(schemaTpl, "defaultValue");
                          schemaArgs.add(expression);
                      } else {
                          tpl.append("#{any()}, ");
                          args.add(exp);
                      }
                  }
                  if (tpl.toString().endsWith(", ")) {
                      tpl.delete(tpl.length() - 2, tpl.length());
                  }
                  if (schemaTpl.length() > 0) {
                      if (schemaTpl.toString().endsWith(", ")) {
                          schemaTpl.delete(schemaTpl.length() - 2, schemaTpl.length());
                      }
                      schemaTpl.append(")");
                      tpl.append(", ").append(schemaTpl);
                      args.addAll(schemaArgs);
                  }
                  anno = JavaTemplate.builder(tpl.toString())
                    .imports(FQN_SCHEMA)
                    .javaParser(JavaParser.fromJavaVersion().classpath("swagger-annotations"))
                    .build()
                    .apply(updateCursor(anno), annotation.getCoordinates().replaceArguments(), args.toArray());
                  maybeAddImport(FQN_SCHEMA, false);
                  return maybeAutoFormat(annotation, anno, ctx, getCursor().getParentTreeCursor());
              }

              private void addSchema(StringBuilder tpl, String key) {
                  if (tpl.length() == 0) {
                      tpl.append("schema = @Schema(");
                  }
                  tpl.append(key).append(" = #{any()}, ");
              }

              private boolean isDataTypeClass(Expression exp) {
                  return exp instanceof J.Assignment && ((J.Identifier) ((J.Assignment) exp).getVariable()).getSimpleName().equals("dataTypeClass");
              }

              private boolean isDefaultValue(Expression exp) {
                  return exp instanceof J.Assignment && ((J.Identifier) ((J.Assignment) exp).getVariable()).getSimpleName().equals("defaultValue");
              }
          }
        );
    }
}
