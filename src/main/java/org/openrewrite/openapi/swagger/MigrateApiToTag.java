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
import org.openrewrite.java.ChangeAnnotationAttributeName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

    private static final String FQN_API = "io.swagger.annotations.Api";
    private static final String FQN_TAG = "io.swagger.v3.oas.annotations.tags.Tag";
    private static final String FQN_TAGS = "io.swagger.v3.oas.annotations.tags.Tags";
    private static final JavaType TYPE_TAG = JavaType.buildType(FQN_TAG);

    @Override
    public String getDisplayName() {
        return "Migrate from `@Api` to `@Tag`";
    }

    @Override
    public String getDescription() {
        return "Converts `@Api` to `@Tag` annotation and converts the directly mappable attributes and removes the others.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
          new UsesType<>(FQN_API, false),
          new JavaIsoVisitor<ExecutionContext>() {
              private final AnnotationMatcher apiMatcher = new AnnotationMatcher(FQN_API);

              @Override
              public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                  annotation = super.visitAnnotation(annotation, ctx);
                  if (apiMatcher.matches(annotation)) {
                      List<Expression> arguments = annotation.getArguments();
                      boolean hasTags = arguments
                        .stream()
                        .filter(J.Assignment.class::isInstance)
                        .map(J.Assignment.class::cast).
                        anyMatch(o -> "tags".equals(o.getVariable().printTrimmed()));
                      if (hasTags) {
                          getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, FQN_API, annotation);
                          // Only remove @Api
                          annotation = null;
                      } else {
                          doAfterVisit(new ChangeAnnotationAttributeName(FQN_API, "value", "name").getVisitor());
                      }

                      doAfterVisit(new ChangeType(FQN_API, FQN_TAG, true).getVisitor());
                  }
                  return annotation;
              }

              @Override
              public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                  classDecl = super.visitClassDeclaration(classDecl, ctx);

                  J.Annotation apiAnnotation = getCursor().getMessage(FQN_API);
                  if (apiAnnotation != null) {
                      List<J.Assignment> arguments = apiAnnotation.getArguments().stream()
                        .filter(J.Assignment.class::isInstance)
                        .map(J.Assignment.class::cast)
                        .collect(Collectors.toList());

                      // Get the description from the @Api annotation
                      String desc = arguments.stream()
                        .filter(o -> "description".equals(o.getVariable().printTrimmed()))
                        .findFirst()
                        .map(assignment -> assignment.getAssignment().printTrimmed())
                        .orElse(null);

                      // Get the tags from the @Api annotation
                      String tags = arguments.stream()
                        .filter(o -> "tags".equals(o.getVariable().printTrimmed()))
                        .findFirst()
                        .map(o -> ((J.NewArray) o.getAssignment()).getInitializer().stream()
                          .map(J::printTrimmed)
                          .map(t -> "@Tag(name=" + t + (desc != null ? ", description=" + desc : "") + ")")
                          .collect(Collectors.joining(",\n"))
                        )
                        .orElse(null);
                      if (tags != null) {
                          List<J.Annotation> origin = classDecl.getLeadingAnnotations();
                          origin.add(buildTags(tags));
                          classDecl = classDecl.withLeadingAnnotations(origin);

                          // Force import @Tag
                          maybeAddImport(FQN_TAG, false);
                          maybeAddImport(FQN_TAGS);
                      }
                  }

                  return classDecl;
              }

              private J.Annotation buildTags(String tags) {
                  J.Identifier id = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "Tags", JavaType.buildType(FQN_TAGS), null);
                  return new J.Annotation(
                    randomId(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    id,
                    JContainer.build(
                      Space.EMPTY,
                      Collections.singletonList(
                        new JRightPadded<>(
                          new J.Literal(
                            randomId(),
                            Space.EMPTY,
                            Markers.EMPTY,
                            FQN_TAGS,
                            "{\n" + tags + "\n}",
                            null,
                            JavaType.Primitive.String
                          ),
                          Space.EMPTY,
                          Markers.EMPTY
                        )
                      ),
                      Markers.EMPTY
                    )
                  );
              }
          }
        );
    }
}
