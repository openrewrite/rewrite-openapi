package org.openrewrite.steve;

import lombok.Value;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

public class SteveRecipe3 extends ScanningRecipe<SteveRecipe3.Accumulator> {
    private final String propertyAnnotationName = "x.y.z.Property";
    private final String defaultValueAnnotationName = "x.y.z.DefaultValue";
    @Language("java")
    private final String dependsOnInterface =
            "package x.y.z;\n" +
            "import java.lang.annotation.*;\n" +
            "@Target(ElementType.ANNOTATION_TYPE) @Retention(RetentionPolicy.RUNTIME)\n" +
            "public @interface DefaultValue {\n" +
            "  @DefaultValue @Target({ElementType.METHOD,ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)\n" +
            "  public @interface String {java.lang.String value();}\n" +
            "  @DefaultValue @Target({ElementType.METHOD,ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)\n" +
            "  public @interface Class {java.lang.Class<?> value();}\n" +
            "  @DefaultValue @Target({ElementType.METHOD,ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)\n" +
            "  public @interface Boolean {boolean value();}\n" +
            "  @DefaultValue @Target({ElementType.METHOD,ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)\n" +
            "  public @interface DefaultByte {byte value();}\n" +
            "  @DefaultValue @Target({ElementType.METHOD,ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)\n" +
            "  public @interface DefaultChar {char value();}\n" +
            "  @DefaultValue @Target({ElementType.METHOD,ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)\n" +
            "  public @interface DefaultShort {short value();}\n" +
            "  @DefaultValue @Target({ElementType.METHOD,ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)\n" +
            "  public @interface Int {int value();}\n" +
            "  @DefaultValue @Target({ElementType.METHOD,ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)\n" +
            "  public @interface Long {long value();}\n" +
            "  @DefaultValue @Target({ElementType.METHOD,ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)\n" +
            "  public @interface Float {float value();}\n" +
            "  @DefaultValue @Target({ElementType.METHOD,ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)\n" +
            "  public @interface Double {double value();}\n" +
            "  @DefaultValue @Target({ElementType.METHOD,ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)\n" +
            "  public @interface List {java.lang.String[] value();}\n" +
            "  @DefaultValue @Target({ElementType.METHOD,ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)\n" +
            "  public @interface Array {java.lang.String[] value();}\n" +
            "  @DefaultValue @Target({ElementType.METHOD,ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)\n" +
            "  public @interface Enum {java.lang.String value();}\n" +
            "  @DefaultValue @Target({ElementType.METHOD,ElementType.FIELD}) @Retention(RetentionPolicy.RUNTIME)\n" +
            "  public @interface Set {java.lang.String[] value();}\n" +
            "}";

    @Override
    public String getDisplayName() {
        return "Steve recipe v3";
    }

    @Override
    public String getDescription() {
        return "Steve recipe v3.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator(new HashMap<>());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return Preconditions.check(
                new UsesType<>(propertyAnnotationName, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl, ExecutionContext ctx) {
                        J.MethodDeclaration md = super.visitMethodDeclaration(methodDecl, ctx);
                        Set<J.Annotation> filteredAnnotations = FindAnnotations.find(md, "@" + propertyAnnotationName);
                        for (J.Annotation annotation : filteredAnnotations) {
                            if (annotation.getArguments() != null) {
                            annotation.getArguments().stream()
                                    .filter(a -> a instanceof J.Assignment && "defaultValue".equals(((J.Identifier) ((J.Assignment) a).getVariable()).getSimpleName()))
                                    .map(a -> ((J.Assignment) a).getAssignment())
                                    .findFirst()
                                    .ifPresent(expression -> accumulateNewField(md.getType(), expression, getCursor().getParentOrThrow(2), acc));
                            }
                        }
                        return md;
                    }
                }
        );
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx, Cursor parent) {
                J visited = super.visit(tree, ctx, parent);
                if (!(visited instanceof SourceFile)) {
                    return visited;
                }
                Path sourcePath = ((SourceFile) requireNonNull(visited)).getSourcePath();
                final Map<JavaType.Variable, Map<JavaType.Primitive, Produced>> relevantFields;
                if (!acc.getAccumulatedFields().isEmpty()) {
                    if (!acc.getAccumulatedFields().containsKey(sourcePath)) {
                        return visited;
                    }
                    relevantFields = acc.getAccumulatedFields().get(sourcePath);
                } else {
                    relevantFields = new HashMap<>();
                }
                visited = new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                        if (!relevantFields.isEmpty()) {
                            List<Produced> producedItems = relevantFields.values().stream()
                                    .flatMap(x -> x.values().stream())
                                    .sorted(comparing(p -> p.getNewFieldReference().getSimpleName()))
                                    .collect(Collectors.toList());
                            for (Produced produced : producedItems) {
                                if (TypeUtils.isOfType(cd.getType(), requireNonNull(produced.getNewFieldReference().getFieldType()).getOwner())) {
                                    List<Statement> statements = cd.getBody().getStatements().stream()
                                            .filter(s -> s instanceof J.VariableDeclarations)
                                            .collect(Collectors.toList());
                                    JavaTemplate.Builder builder = JavaTemplate.builder(produced.getNewFieldStatement());
                                    if (produced.isNewFieldContextSensitive()) {
                                        builder = builder.contextSensitive();
                                    }
                                    cd = builder.build().apply(updateCursor(cd), !statements.isEmpty() ? statements.get(statements.size() - 1).getCoordinates().after() : cd.getBody().getCoordinates().firstStatement(), produced.getOldAccess());
                                }
                            }
                        }
                        return cd;
                    }
                }.visit(visited, ctx);
                visited = new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl, ExecutionContext ctx) {
                        J.MethodDeclaration md = super.visitMethodDeclaration(methodDecl, ctx);
                        AtomicReference<Expression> defaultValue = new AtomicReference<>();
                        JavaType methodType = md.getType();
                        md = md.withLeadingAnnotations(ListUtils.map(md.getLeadingAnnotations(), maybeProperty -> {
                            if (TypeUtils.isOfClassType(maybeProperty.getType(), propertyAnnotationName) && maybeProperty.getArguments() != null) {
                                return maybeProperty.withArguments(ListUtils.map(maybeProperty.getArguments(), argExpr -> {
                                    if (argExpr instanceof J.Assignment) {
                                        J.Assignment arg = (J.Assignment) argExpr;
                                        String argName = ((J.Identifier) arg.getVariable()).getSimpleName();
                                        if ("defaultValue".equals(argName) && methodType != null) {
                                            Expression variable = arg.getAssignment();
                                            defaultValue.set(variable);
                                            return null;
                                        }
                                    }
                                    return argExpr;
                                }));
                            }
                            return maybeProperty;
                        }));
                        updateCursor(md);
                        if (defaultValue.get() != null && methodType != null) {
                            Expression updatedValue = defaultValue.get();
                            JavaType.Primitive primitiveType = getJavaTypePrimitive(methodType);
                            String defaultValueType = "String";
                            boolean isContextSensitive = true;
                            if (updatedValue instanceof J.Literal) {
                                updatedValue = getProperlyQuotedLiteral(primitiveType, (J.Literal) updatedValue);
                                defaultValueType = getDefaultValueType(primitiveType);
                                isContextSensitive = false;
                            } else if (!primitiveType.equals(JavaType.Primitive.String)) {
                                if (updatedValue instanceof J.Identifier) {
                                    Produced produced = relevantFields.get(((J.Identifier) updatedValue).getFieldType()).get(primitiveType);
                                    updatedValue = produced.getNewFieldReference();
                                    defaultValueType = produced.getDefaultValueType();
                                } else if (updatedValue instanceof J.FieldAccess) {
                                    Produced produced = relevantFields.get(((J.FieldAccess) updatedValue).getName().getFieldType()).get(primitiveType);
                                    updatedValue = produced.getNewFieldReference();
                                    defaultValueType = produced.getDefaultValueType();
                                    isContextSensitive = false;
                                }
                            }
                            JavaTemplate.Builder builder = JavaTemplate.builder("@DefaultValue." + defaultValueType + "(#{any()})")
                                    .imports(defaultValueAnnotationName)
                                    .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()).dependsOn(dependsOnInterface));
                            if (isContextSensitive) {
                                builder = builder.contextSensitive();
                            }
                            md = builder.build()
                                    .apply(
                                            getCursor(),
                                            md.getCoordinates().addAnnotation(comparing(a -> requireNonNull(a.getType()).toString())),
                                            updatedValue
                                    );
                            maybeAddImport(defaultValueAnnotationName);
                        }
                        return md;
                    }
                }.visit(visited, ctx);
                return visited;
            }
        };
    }

    private JavaType.Primitive getJavaTypePrimitive(JavaType type) {
        JavaType.Primitive fromKeyword = JavaType.Primitive.fromKeyword(type.toString());
        JavaType.Primitive fromClassName = JavaType.Primitive.fromClassName(type.toString());
        return fromKeyword != null ? fromKeyword : fromClassName;
    }

    private String getDefaultValueType(JavaType.Primitive coreType) {
        switch (coreType) {
            case String: {
                return "String";
            }
            case Boolean: {
                return "Boolean";
            }
            case Byte: {
                return "DefaultByte";
            }
            case Char: {
                return "DefaultChar";
            }
            case Short: {
                return "DefaultShort";
            }
            case Int: {
                return "Int";
            }
            case Long: {
                return "Long";
            }
            case Float: {
                return "Float";
            }
            case Double: {
                return "Double";
            }
            default: {
                // TODO: Lists, Arrays, Enum, Set?
                return "";
            }
        }
    }

    private String buildStatement(String newFieldName, JavaType.Primitive coreType) {
        switch (coreType) {
            case Boolean: {
                return "private boolean " + newFieldName + " = Boolean.parseBoolean(#{any(String)});";
            }
            case Short: {
                return "private short " + newFieldName + " = Short.parseShort(#{any(String)});";
            }
            case Double: {
                return "private double " + newFieldName + " = Double.parseDouble(#{any(String)});";
            }
            case Float: {
                return "private float " + newFieldName + " = Float.parseFloat(#{any(String)});";
            }
            case Int: {
                return "private int " + newFieldName + " = Integer.parseInt(#{any(String)});";
            }
            case Byte: {
                return "private byte " + newFieldName + " = Byte.parseByte(#{any(String)});";
            }
            case Long: {
                return "private long " + newFieldName + " = Long.parseLong(#{any(String)});";
            }
            case Char: {
                return "private char " + newFieldName + " = #{any(String)}.charAt(0);";
            }
            // TODO: Maybe not string
            // TODO: String? List?
        }
        return null;
    }

    private J.Literal getProperlyQuotedLiteral(JavaType.Primitive newType, J.Literal originalLiteral) {
        String initial = requireNonNull(originalLiteral.getValueSource());
        String noQuotes = initial.replace("\"", "");
        String singleQuotes = initial.replace("\"", "'");
        switch (newType) {
            case Boolean: {
                return originalLiteral
                        .withValue(Boolean.parseBoolean(noQuotes))
                        .withValueSource(noQuotes)
                        .withType(newType);
            }
            case Short: {
                return originalLiteral
                        .withValue(Short.parseShort(noQuotes))
                        .withValueSource(noQuotes)
                        .withType(newType);
            }
            case Double: {
                return originalLiteral
                        .withValue(Double.parseDouble(noQuotes))
                        .withValueSource(noQuotes)
                        .withType(newType);
            }
            case Float: {
                return originalLiteral
                        .withValue(Float.parseFloat(noQuotes))
                        .withValueSource(noQuotes)
                        .withType(newType);
            }
            case Int: {
                return originalLiteral
                        .withValue(Integer.parseInt(noQuotes))
                        .withValueSource(noQuotes)
                        .withType(newType);
            }
            case Byte: {
                return originalLiteral
                        .withValue(Byte.parseByte(noQuotes))
                        .withValueSource(noQuotes)
                        .withType(newType);
            }
            case Long: {
                return originalLiteral
                        .withValue(Long.parseLong(noQuotes))
                        .withValueSource(noQuotes)
                        .withType(newType);
            }
            case Char: {
                return originalLiteral
                        .withValue(singleQuotes)
                        .withValueSource(singleQuotes)
                        .withType(newType);
            }
            default: {
                return originalLiteral;
            }
        }
    }

    private Produced buildProduced(boolean isContextSensitive, String oldAccess, JavaType.Primitive coreType, Cursor definitionScope, J oldAccessExpression) {
        String appropriateBase = oldAccess.contains(".") ? oldAccess.replace(".", "__").toLowerCase() : oldAccess;
        String newFieldName = VariableNameUtils.generateVariableName(
                appropriateBase + "As" + coreType.name(),
                definitionScope,
                VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER
        );
        JavaType newFieldType = JavaType.buildType(coreType.getKeyword());
        JavaType enclosingType = definitionScope.firstEnclosingOrThrow(J.ClassDeclaration.class).getType();
        J.Identifier newFieldReference = new J.Identifier(
                Tree.randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                Collections.emptyList(),
                newFieldName,
                newFieldType,
                new JavaType.Variable(null, Flag.Private.getBitMask(), newFieldName, enclosingType, newFieldType, null)
        );
        String newFieldStatement = buildStatement(newFieldName, coreType);
        return new Produced(newFieldReference, getDefaultValueType(coreType), newFieldStatement, isContextSensitive, oldAccessExpression);
    }

    private boolean isWithinCurrentScope(JavaType currentScope, JavaType checkType) {
        if (checkType instanceof JavaType.FullyQualified && checkType.toString().contains("$"))
        {
            return isWithinCurrentScope(currentScope, ((JavaType.FullyQualified) checkType).getOwningClass());
        }
        if (currentScope instanceof JavaType.FullyQualified && currentScope.toString().contains("$")) {
            return isWithinCurrentScope(((JavaType.FullyQualified) currentScope).getOwningClass(), checkType);
        }
        return currentScope.equals(checkType);
    }

    private void accumulateNewField(JavaType annotatedMethodType, Expression oldAccess, Cursor definitionScope, Accumulator acc) {
        JavaType.Variable fieldType;
        boolean isContextSensitive = false;
        if (oldAccess instanceof J.Identifier) {
            J.Identifier castAccess =  (J.Identifier) oldAccess;
            fieldType = requireNonNull(castAccess.getFieldType());
            isContextSensitive = true;
        } else if (oldAccess instanceof J.FieldAccess) {
            J.FieldAccess castAccess = (J.FieldAccess) oldAccess;
            fieldType = requireNonNull(castAccess.getName().getFieldType());
            JavaType accessScope = fieldType.getOwner();
            JavaType currentScope = definitionScope.firstEnclosingOrThrow(J.ClassDeclaration.class).getType();
            if (isWithinCurrentScope(currentScope, accessScope)) {
                isContextSensitive = true;
            }
        } else {
            return;
        }
        JavaType.Primitive coreType = getJavaTypePrimitive(annotatedMethodType);
        if (coreType.equals(JavaType.Primitive.String)) {
            return;
        }
        acc.accumulatedFields
                .computeIfAbsent(definitionScope.firstEnclosingOrThrow(J.CompilationUnit.class).getSourcePath(), s -> new HashMap<>())
                .computeIfAbsent(fieldType, k -> new HashMap<>())
                .putIfAbsent(coreType, buildProduced(isContextSensitive, oldAccess.toString(), coreType, definitionScope, oldAccess));
    }

    @Value
    public static class Produced {
        J.Identifier newFieldReference;
        String defaultValueType;
        String newFieldStatement;
        boolean newFieldContextSensitive;
        J oldAccess;
    }

    @Value
    public static class Accumulator {
        Map<Path, Map<JavaType.Variable, Map<JavaType.Primitive, Produced>>> accumulatedFields;
    }
}
