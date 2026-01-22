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

import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeAnnotationAttributeName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.RemoveAnnotationAttribute;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Annotation;

import lombok.Getter;

public class MigrateApiToTag extends Recipe {

    private static final String FQN_API = "io.swagger.annotations.Api";
    private static final String FQN_AUTHORIZATION = "io.swagger.annotations.Authorization";
    private static final String FQN_AUTHORIZATION_SCOPE = "io.swagger.annotations.AuthorizationScope";
    private static final String FQN_TAG = "io.swagger.v3.oas.annotations.tags.Tag";
    private static final String FQN_TAGS = "io.swagger.v3.oas.annotations.tags.Tags";
    private static final String FQN_HIDDEN = "io.swagger.v3.oas.annotations.Hidden";
    private static final String FQN_SECURITY_REQ = "io.swagger.v3.oas.annotations.security.SecurityRequirement";
    private static final String FQN_SECURITY_REQS = "io.swagger.v3.oas.annotations.security.SecurityRequirements";

    @Language("java")
    private static final String HIDDEN_CLASS =
        "package io.swagger.v3.oas.annotations;\n" +
                "import java.lang.annotation.Retention;\n" +
                "import java.lang.annotation.RetentionPolicy;\n" +
                "import java.lang.annotation.Target;\n" +
                "import static java.lang.annotation.ElementType.ANNOTATION_TYPE;\n" +
                "import static java.lang.annotation.ElementType.TYPE;\n" +
                "import static java.lang.annotation.ElementType.FIELD;\n" +
                "import static java.lang.annotation.ElementType.METHOD;\n" +
                "@Target({METHOD, TYPE, FIELD, ANNOTATION_TYPE})\n" +
                "@Retention(RetentionPolicy.RUNTIME)\n" +
                "public @interface Hidden {}";

    @Language("java")
    private static final String SECURITY_REQ_CLASS =
        "package io.swagger.v3.oas.annotations.security;\n" +
            "import java.lang.annotation.Inherited;\n" +
            "import java.lang.annotation.Repeatable;\n" +
            "import java.lang.annotation.Retention;\n" +
            "import java.lang.annotation.RetentionPolicy;\n" +
            "import java.lang.annotation.Target;\n" +
            "import static java.lang.annotation.ElementType.ANNOTATION_TYPE" +
            "import static java.lang.annotation.ElementType.TYPE" +
            "import static java.lang.annotation.ElementType.METHOD" +
            "@Target({METHOD, TYPE, ANNOTATION_TYPE})\n" +
            "@Retention(RetentionPolicy.RUNTIME)\n" +
            "@Repeatable(SecurityRequirements.class)\n" +
            "@Inherited\n" +
            "public @interface SecurityRequirement {\n" +
            "    String name();\n" +
            "    String[] scopes() default {};\n" +
            "}";

    @Language("java")
    private static final String SECURITY_REQS_CLASS =
        "package io.swagger.v3.oas.annotations.security;\n" +
            "import java.lang.annotation.Inherited;\n" +
            "import java.lang.annotation.Retention;\n" +
            "import java.lang.annotation.RetentionPolicy;\n" +
            "import java.lang.annotation.Target;\n" +
            "import static java.lang.annotation.ElementType.ANNOTATION_TYPE" +
            "import static java.lang.annotation.ElementType.TYPE" +
            "import static java.lang.annotation.ElementType.METHOD" +
            "@Target({METHOD, TYPE, ANNOTATION_TYPE})\n" +
            "@Retention(RetentionPolicy.RUNTIME)\n" +
            "@Inherited\n" +
            "public @interface SecurityRequirements {\n" +
            "    SecurityRequirement[] value() default {};\n" +
            "}";

    @Language("java")
    private static final String TAGS_CLASS =
        "package io.swagger.v3.oas.annotations.tags;\n" +
            "import java.lang.annotation.ElementType;\n" +
            "import java.lang.annotation.Retention;\n" +
            "import java.lang.annotation.RetentionPolicy;\n" +
            "import java.lang.annotation.Target;\n" +
            "@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})\n" +
            "@Retention(RetentionPolicy.RUNTIME)\n" +
            "public @interface Tags {\n" +
            "    Tag[] value() default {};\n" +
            "}";

    @Language("java")
    private static final String TAG_CLASS =
        "package io.swagger.v3.oas.annotations.tags;\n" +
            "import java.lang.annotation.ElementType;\n" +
            "import java.lang.annotation.Repeatable;\n" +
            "import java.lang.annotation.Retention;\n" +
            "import java.lang.annotation.RetentionPolicy;\n" +
            "import java.lang.annotation.Target;\n" +
            "@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})\n" +
            "@Retention(RetentionPolicy.RUNTIME)\n" +
            "@Repeatable(Tags.class)\n" +
            "public @interface Tag {\n" +
            "    String name();\n" +
            "    String description() default \"\";\n" +
            "}";

    @Getter
    final String displayName = "Migrate from `@Api` to `@Tag`";

    @Getter
    final String description = "Converts `@Api` to `@Tag` annotation and converts the directly mappable attributes and removes the others.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
            new UsesType<>(FQN_API, false),
            new JavaIsoVisitor<ExecutionContext>() {
                private final AnnotationMatcher apiMatcher = new AnnotationMatcher(FQN_API);

                @Override
                public J.@Nullable Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                    J.Annotation ann = super.visitAnnotation(annotation, ctx);
                    if (apiMatcher.matches(ann)) {
                        doAfterVisit(new ChangeAnnotationAttributeName(FQN_API, "value", "name").getVisitor());
                        doAfterVisit(new RemoveAnnotationAttribute(FQN_API, "hidden").getVisitor());
                        doAfterVisit(new RemoveAnnotationAttribute(FQN_API, "produces").getVisitor());
                        doAfterVisit(new RemoveAnnotationAttribute(FQN_API, "authorizations").getVisitor());
                        doAfterVisit(new ChangeType(FQN_API, FQN_TAG, true).getVisitor());

                        Map<String, Expression> annoAssignments = AnnotationUtils.extractArgumentAssignedExpressions(ann);
                        if (annoAssignments.containsKey("tags") || annoAssignments.containsKey("hidden") || annoAssignments.containsKey("authorizations")) {
                            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, FQN_API, annoAssignments);
                        }
                        // Remove @Api and add @Tag or @Tags at class level
                        if (annoAssignments.containsKey("tags")) {
                            return null;
                        }
                    }
                    return ann;
                }

                @Override
                public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                    J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                    Map<String, Expression> annoArguments = getCursor().getMessage(FQN_API);
                    if (annoArguments == null) {
                        return cd;
                    }

                    Expression hiddenAssignment = annoArguments.get("hidden");
                    if (hiddenAssignment != null) {
                        boolean hidden = Boolean.parseBoolean(hiddenAssignment.printTrimmed());
                        if (hidden) {
                            maybeAddImport(FQN_HIDDEN, false);
                            cd = JavaTemplate.builder("@Hidden")
                                .imports(FQN_HIDDEN)
                                .javaParser(JavaParser.fromJavaVersion().dependsOn(HIDDEN_CLASS))
                                .build()
                                .apply(updateCursor(cd), cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
                        }
                    }

                    Expression authAssignment = annoArguments.get("authorizations");
                    if (authAssignment != null) {
                        if (authAssignment instanceof J.NewArray) {
                            J.NewArray newArray = (J.NewArray) authAssignment;
                            List<Expression> initializer = requireNonNull(newArray.getInitializer());
                            if (initializer.size() == 1 && (initializer.get(0) instanceof J.Annotation)) {
                                cd = addSecurityRequirementAnnotation(cd, (Annotation) initializer.get(0));
                            } else {
                               cd = addSecurityRequirementsAnnotation(cd, initializer);
                            }
                        } else if (authAssignment instanceof J.Annotation){
                            cd = addSecurityRequirementAnnotation(cd, (Annotation) authAssignment);
                        }
                    }

                    Expression descAssignment = annoArguments.get("description");
                    Expression tagsAssignment = annoArguments.get("tags");

                    if (tagsAssignment instanceof J.NewArray) {
                        J.NewArray newArray = (J.NewArray) tagsAssignment;
                        List<Expression> initializer = requireNonNull(newArray.getInitializer());
                        if (initializer.size() == 1) {
                            cd = addTagAnnotation(cd, initializer.get(0), descAssignment);
                        } else {
                            cd = addTagsAnnotation(cd, initializer, descAssignment);
                        }
                    } else if (tagsAssignment != null) {
                        cd = addTagAnnotation(cd, tagsAssignment, descAssignment);
                    }

                    return maybeAutoFormat(classDecl, cd, cd.getName(), ctx, getCursor().getParentTreeCursor());
                }

                private J.ClassDeclaration addTagsAnnotation(J.ClassDeclaration cd, List<Expression> tagsAssignments, @Nullable Expression descAssignment) {
                    // Create template for @Tags annotation
                    StringBuilder template = new StringBuilder("@Tags({");
                    List<Expression> templateArgs = new ArrayList<>();
                    for (Expression expression : tagsAssignments) {
                        if (!templateArgs.isEmpty()) {
                            template.append(",");
                        }
                        template.append("\n@Tag(name = #{any()}");
                        templateArgs.add(expression);
                        if (descAssignment != null) {
                            template.append(", description = #{any()}");
                            templateArgs.add(descAssignment);
                        }
                        template.append(")");
                    }
                    template.append("\n})");

                    // Add formatted template and imports
                    maybeAddImport(FQN_TAG);
                    maybeAddImport(FQN_TAGS);
                    return JavaTemplate.builder(template.toString())
                        .imports(FQN_TAGS, FQN_TAG)
                        .javaParser(JavaParser.fromJavaVersion().dependsOn(TAGS_CLASS, TAG_CLASS))
                        .build()
                        .apply(updateCursor(cd), cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)), templateArgs.toArray());
                }

                private J.ClassDeclaration addTagAnnotation(J.ClassDeclaration cd, Expression tagsAssignment, @Nullable Expression descAssignment) {
                    // Create template for @Tags annotation
                    StringBuilder template = new StringBuilder("@Tag(name = #{any()}");
                    List<Expression> templateArgs = new ArrayList<>();
                    templateArgs.add(tagsAssignment);
                    if (descAssignment != null) {
                        template.append(", description = #{any()}");
                        templateArgs.add(descAssignment);
                    }
                    template.append(")");

                    // Add formatted template and imports
                    maybeAddImport(FQN_TAG);
                    return JavaTemplate.builder(template.toString())
                        .imports(FQN_TAG)
                        .javaParser(JavaParser.fromJavaVersion().dependsOn(TAGS_CLASS, TAG_CLASS))
                        .build()
                        .apply(updateCursor(cd), cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)), templateArgs.toArray());
                }

                private J.ClassDeclaration addSecurityRequirementsAnnotation(J.ClassDeclaration cd, List<Expression> authsAssignment) {
                    // Create template for @SecurityRequirements annotation
                    StringBuilder template = new StringBuilder("@SecurityRequirements({");
                    List<Expression> templateArgs = new ArrayList<>();

                    for (Expression expression : authsAssignment) {
                        if (!templateArgs.isEmpty()) {
                            template.append(", ");
                        }
                        J.Annotation authAnnotation = (Annotation) expression;
                        template.append("@SecurityRequirement(name = #{any()}");

                        Map<String, J.Assignment> authAssignments = AnnotationUtils.extractArgumentAssignments(authAnnotation);
                        Expression valueExpression = authAssignments.get("value").getAssignment();
                        templateArgs.add(valueExpression);

                        if(authAssignments.containsKey("scopes")) {
                            Expression scopesExpression = authAssignments.get("scopes").getAssignment();
                            processScopes(scopesExpression, template, templateArgs);
                        }
                        template.append(")");
                    }
                    template.append("})");

                    // Add formatted template and imports
                    maybeRemoveImport(FQN_AUTHORIZATION);
                    maybeAddImport(FQN_SECURITY_REQS);
                    maybeAddImport(FQN_SECURITY_REQ);
                    return JavaTemplate.builder(template.toString())
                        .imports(FQN_SECURITY_REQS, FQN_SECURITY_REQ)
                        .javaParser(JavaParser.fromJavaVersion().dependsOn(SECURITY_REQS_CLASS, SECURITY_REQ_CLASS))
                        .build()
                        .apply(updateCursor(cd), cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)), templateArgs.toArray());
                }

                private J.ClassDeclaration addSecurityRequirementAnnotation(J.ClassDeclaration cd, J.Annotation authAnnotation) {
                    // Create template for @SecurityRequirement annotation
                    Map<String, J.Assignment> authAssignments = AnnotationUtils.extractArgumentAssignments(authAnnotation);
                    StringBuilder template = new StringBuilder("@SecurityRequirement(name = #{any()}");

                    List<Expression> templateArgs = new ArrayList<>();

                    templateArgs.add(authAssignments.get("value").getAssignment());

                    if(authAssignments.containsKey("scopes")) {
                        Expression scopesExpression = authAssignments.get("scopes").getAssignment();
                        processScopes(scopesExpression, template, templateArgs);
                    }
                    template.append(")");

                    // Add formatted template and imports
                    maybeRemoveImport(FQN_AUTHORIZATION);
                    maybeAddImport(FQN_SECURITY_REQ);
                    return JavaTemplate.builder(template.toString())
                        .imports(FQN_SECURITY_REQ)
                        .javaParser(JavaParser.fromJavaVersion().dependsOn(SECURITY_REQS_CLASS, SECURITY_REQ_CLASS))
                        .build()
                        .apply(updateCursor(cd), cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)), templateArgs.toArray());
                }

                private void processScopes(Expression scopesExpression, StringBuilder template, List<Expression> templateArgs) {
                    if (scopesExpression instanceof J.NewArray) {
                        List<Expression> scopesArr = requireNonNull(((J.NewArray)scopesExpression).getInitializer());
                        template.append(", scopes = {");
                        StringBuilder scopesSb = new StringBuilder();
                        for(Expression scopeExpression : scopesArr) {
                            if(scopesSb.length() > 0) {
                                scopesSb.append(", ");
                            }
                            scopesSb.append("#{any()}");
                            Expression scope = extractScopeFromAnnotation((Annotation) scopeExpression);
                            templateArgs.add(scope);
                        }
                        template.append(scopesSb.toString());
                        template.append("}");
                    } else if (scopesExpression instanceof J.Annotation) {
                        Expression scope = extractScopeFromAnnotation((Annotation) scopesExpression);
                        if(scope != null) {
                            template.append(", scopes = #{any()}");
                            templateArgs.add(scope);
                        }
                    }
                }

                private Expression extractScopeFromAnnotation(J.Annotation scopeAnnotation) {
                    maybeRemoveImport(FQN_AUTHORIZATION_SCOPE);
                    Map<String, J.Assignment> scopeAssignments = AnnotationUtils.extractArgumentAssignments(scopeAnnotation);
                    return scopeAssignments.get("scope").getAssignment();

                }

            }
        );
    }
}
