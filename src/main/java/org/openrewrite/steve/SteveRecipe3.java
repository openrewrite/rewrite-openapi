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
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

public class SteveRecipe3 extends ScanningRecipe<SteveRecipe3.Accumulator> {
    private static final String propertyAnnotationName = "x.y.z.Property";
    private static final String defaultValueAnnotationName = "x.y.z.DefaultValue";
    private static final JavaType listType = JavaType.buildType("java.util.List");
    private static final JavaType setType = JavaType.buildType("java.util.Set");
    private static final JavaType stringType = JavaType.buildType("java.lang.String");
    private static final JavaType arrayOfStringType = new JavaType.Array(null, stringType, null);
    private static final JavaType listOfStringType = new JavaType.Parameterized(null, (JavaType.FullyQualified) listType, singletonList(stringType));
    private static final JavaType setOfStringType = new JavaType.Parameterized(null, (JavaType.FullyQualified) setType, singletonList(stringType));
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
        return new Accumulator(new HashMap<>(), new HashMap<>());
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
                final Map<JavaType.Variable, Map<JavaType, Produced>> relevantRemappedFields = acc.getAccumulatedFields().getOrDefault(sourcePath, new HashMap<>());
                final Map<String, Produced> relevantNetNewFields = acc.getAccumulatedLiteralTransformedFields().getOrDefault(sourcePath, new HashMap<>());
                if (relevantRemappedFields.isEmpty() && relevantNetNewFields.isEmpty()) {
                    return visited;
                }
                visited = new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                        if (!relevantRemappedFields.isEmpty() || !relevantNetNewFields.isEmpty()) {
                            List<Produced> producedItems = Stream.concat(
                                    relevantRemappedFields.values().stream().flatMap(x -> x.values().stream()),
                                    relevantNetNewFields.values().stream()
                            ).sorted(comparing(p -> p.getNewFieldReference().getSimpleName())).collect(Collectors.toList());
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
                                J.Literal castValue = (J.Literal) updatedValue;
                                Produced produced = relevantNetNewFields.get(castValue.getValueSource());
                                if (produced != null) {
                                    updatedValue = produced.getNewFieldReference();
                                    defaultValueType = produced.getDefaultValueType();
                                } else {
                                    // TODO: cleanup here since using primitive type
                                    updatedValue = getProperlyQuotedLiteral(primitiveType, castValue);
                                    defaultValueType = getDefaultValueType(primitiveType);
                                    isContextSensitive = false;
                                }
                            } else if (!JavaType.Primitive.String.equals(primitiveType)) {
                                if (updatedValue instanceof J.Identifier) {
                                    J.Identifier castValue = (J.Identifier) updatedValue;
                                    Produced produced;
                                    // TODO: sort out getting the right type for lookup, since we used static values
                                    if (primitiveType != null) {
                                        produced = relevantRemappedFields.get(castValue.getFieldType()).get(primitiveType);
                                    } else {
                                        produced = relevantRemappedFields.get(castValue.getFieldType()).get(null);
                                    }
                                    updatedValue = produced.getNewFieldReference();
                                    defaultValueType = produced.getDefaultValueType();
                                } else if (updatedValue instanceof J.FieldAccess) {
                                    Produced produced = relevantRemappedFields.get(((J.FieldAccess) updatedValue).getName().getFieldType()).get(primitiveType);
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

    private String getDefaultValueType(JavaType type) {
        if (type instanceof JavaType.Primitive) {
            JavaType.Primitive coreType = (JavaType.Primitive) type;
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
            }
        } else if (TypeUtils.isAssignableTo(arrayOfStringType, type)) {
            return "Array";
        } else if (TypeUtils.isAssignableTo(listOfStringType, type)) {
            return "List";
        } else if (TypeUtils.isAssignableTo(setOfStringType, type)) {
            return "Set";
        }
        return null;
    }

    private String buildStatement(String newFieldName, String defaultType) {
        switch (defaultType) {
            case "Boolean": {
                return "private boolean " + newFieldName + " = Boolean.parseBoolean(#{any(String)});";
            }
            case "DefaultByte": {
                return "private byte " + newFieldName + " = Byte.parseByte(#{any(String)});";
            }
            case "DefaultChar": {
                return "private char " + newFieldName + " = #{any(String)}.charAt(0);";
            }
            case "DefaultShort": {
                return "private short " + newFieldName + " = Short.parseShort(#{any(String)});";
            }
            case "Int": {
                return "private int " + newFieldName + " = Integer.parseInt(#{any(String)});";
            }
            case "Long": {
                return "private long " + newFieldName + " = Long.parseLong(#{any(String)});";
            }
            case "Float": {
                return "private float " + newFieldName + " = Float.parseFloat(#{any(String)});";
            }
            case "Double": {
                return "private double " + newFieldName + " = Double.parseDouble(#{any(String)});";
            }
            case "List":
            case "Array":
            case "Set": {
                return "private String[] " + newFieldName + " = #{any(String)}.split(\",\");";
            }
            default: {
                return null;
            }
        }
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

    private String buildNewFieldName(String oldAccess, String asType, Cursor definitionScope) {
        String appropriateBase = oldAccess.contains(".") ? oldAccess.replace(".", "__").toLowerCase() : oldAccess;
        return VariableNameUtils.generateVariableName(
                appropriateBase + "As" + asType,
                definitionScope,
                VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER
        );
    }

    private Produced buildProducedHelper(boolean isContextSensitive, String newAccess, String defaultType, JavaType fieldType, String newStatement, Cursor definitionScope, J oldAccessExpression) {
        JavaType enclosingType = definitionScope.firstEnclosingOrThrow(J.ClassDeclaration.class).getType();
        J.Identifier newFieldReference = new J.Identifier(
                Tree.randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                Collections.emptyList(),
                newAccess,
                fieldType,
                new JavaType.Variable(null, Flag.Private.getBitMask(), newAccess, enclosingType, fieldType, null)
        );
        return new Produced(newFieldReference, defaultType, newStatement, isContextSensitive, oldAccessExpression);
    }

    private Produced buildProduced(boolean isContextSensitive, JavaType type, Cursor definitionScope, J oldAccessExpression, int count) {
        String defaultType = getDefaultValueType(type);
        if (defaultType == null) {
            return null;
        }
        String asType = "Array";
        JavaType newFieldType = arrayOfStringType;
        if (type instanceof JavaType.Primitive) {
            JavaType.Primitive castType = (JavaType.Primitive) type;
            asType = castType.name();
            newFieldType = JavaType.buildType(castType.getKeyword());
        }
        String newNameBase = oldAccessExpression.toString();
        if (oldAccessExpression instanceof J.Literal) {
            newNameBase = "literalArg" + count;
        }
        String newFieldName = buildNewFieldName(newNameBase, asType, definitionScope);
        return buildProducedHelper(
                isContextSensitive,
                newFieldName,
                defaultType,
                newFieldType,
                buildStatement(newFieldName, defaultType),
                definitionScope,
                oldAccessExpression
        );
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

    private void accumulateRemapped(Path path, JavaType.Variable oldVarType, JavaType newFieldType, Produced produced, Accumulator acc) {
        acc.getAccumulatedFields()
            .computeIfAbsent(path, s -> new HashMap<>())
            .computeIfAbsent(oldVarType, k -> new HashMap<>())
            .putIfAbsent(newFieldType, produced);
    }

    private void accumulateNetNew(Path path, String oldValue, Produced produced, Accumulator acc) {
        acc.getAccumulatedLiteralTransformedFields()
            .computeIfAbsent(path, s -> new HashMap<>())
            .putIfAbsent(oldValue, produced);
    }

    private void accumulateNewField(JavaType annotatedMethodType, Expression oldAccess, Cursor definitionScope, Accumulator acc) {
        String defaultValueType =  getDefaultValueType(annotatedMethodType);
        if (defaultValueType == null) {
            return;
        }
        JavaType matchingType = getJavaTypePrimitive(annotatedMethodType);
        boolean allowLiteralTransformedField = false;
        if ("Array".equals(defaultValueType)) {
            allowLiteralTransformedField = true;
            matchingType = arrayOfStringType;
        } else if ("List".equals(defaultValueType)) {
            allowLiteralTransformedField = true;
            matchingType = listOfStringType;
        } else if ("Set".equals(defaultValueType)) {
            allowLiteralTransformedField = true;
            matchingType = setOfStringType;
        }
        // if String -> String, no new field
        if (matchingType == null || JavaType.Primitive.String.equals(matchingType)) {
            return;
        }

        Path filePath = definitionScope.firstEnclosingOrThrow(J.CompilationUnit.class).getSourcePath();
        int count = acc.getAccumulatedLiteralTransformedFields().getOrDefault(filePath, new HashMap<>()).size();
        if (oldAccess instanceof J.Identifier) {
            J.Identifier castAccess =  (J.Identifier) oldAccess;
            JavaType.Variable oldVarType = requireNonNull(castAccess.getFieldType());
            Produced produced = buildProduced(true, matchingType, definitionScope, oldAccess, count);
            accumulateRemapped(filePath, oldVarType, matchingType, produced, acc);
        } else if (oldAccess instanceof J.FieldAccess) {
            J.FieldAccess castAccess = (J.FieldAccess) oldAccess;
            JavaType.Variable oldVarType = requireNonNull(castAccess.getName().getFieldType());
            JavaType accessScope = oldVarType.getOwner();
            JavaType currentScope = definitionScope.firstEnclosingOrThrow(J.ClassDeclaration.class).getType();
            Produced produced = buildProduced(isWithinCurrentScope(currentScope, accessScope), matchingType, definitionScope, oldAccess, count);
            accumulateRemapped(filePath, oldVarType, matchingType, produced, acc);
        } else if (oldAccess instanceof J.Literal && allowLiteralTransformedField) {
            J.Literal castAccess = (J.Literal) oldAccess;
            // Meaning we came across Array, List or Set
            Produced produced = buildProduced(false, matchingType, definitionScope, oldAccess, count);
            accumulateNetNew(filePath, castAccess.getValueSource(), produced, acc);
        }
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
        Map<Path, Map<JavaType.Variable, Map<JavaType, Produced>>> accumulatedFields;
        Map<Path, Map<String, Produced>> accumulatedLiteralTransformedFields;
    }
}
