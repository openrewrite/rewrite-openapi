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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Annotation;
import org.openrewrite.java.tree.J.Assignment;

/**
 * Utility class that can be used for migrating values of the <code>ApiParam</code> annotation
 * that are contained as part of the <code>Schema</code> annotation in the target <code>Parameter</code> annotation
 */
class MigrateApiParamSchemaValue extends JavaIsoVisitor<ExecutionContext> {
	private static final String FQN_SCHEMA = "io.swagger.v3.oas.annotations.media.Schema";
	private static final AnnotationMatcher PARAMETER_ANNOTATION_MATCHER = new AnnotationMatcher("io.swagger.v3.oas.annotations.Parameter");

	private final String vbleName;
	
	public MigrateApiParamSchemaValue(String vbleName) {
		this.vbleName = vbleName;
	}

	@Override
	public Annotation visitAnnotation(Annotation annotation, ExecutionContext ctx) {
		J.Annotation anno = super.visitAnnotation(annotation, ctx);

        if (!PARAMETER_ANNOTATION_MATCHER.matches(anno)) {
            return anno;
        }

        StringBuilder tpl = new StringBuilder();
        List<Expression> args = new ArrayList<>();
        J.Assignment existingSchemaExpr = null;
        SchemaInfo schemaInfo = null;
        for (Expression exp : anno.getArguments()) {
            if (isInteresingVble(exp)) {
                Expression expression = ((J.Assignment) exp).getAssignment();
                String schema = createSchema(vbleName);
                schemaInfo = new SchemaInfo(expression, schema);
            } else {
            	if(isSchema(exp)) {
            		existingSchemaExpr = (Assignment) exp;
            	} else {
            		tpl.append("#{any()}, ");
                    args.add(exp);
            	}
            }
        }

        processSchemaEntry(schemaInfo, existingSchemaExpr, tpl, args);                        

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
	
    private boolean isSchema(Expression expr) {
    	if(expr instanceof J.Assignment) {
    		Expression vble = ((J.Assignment) expr).getVariable();
    		if(vble instanceof J.Identifier) {
    			return ((J.Identifier)vble).getSimpleName().equals("schema");
    		}
    	}
    	return false;
    }
    
    private String createSchema(String key) {
    	return String.format("schema = @Schema(%s = #{any()} ", key);
    }
	
	private boolean isInteresingVble(Expression exp) {
        return exp instanceof J.Assignment && vbleName.equals(((J.Identifier) ((J.Assignment) exp).getVariable()).getSimpleName());
    }

	private void processSchemaEntry(SchemaInfo schemaInfo, J.Assignment existingSchema, StringBuilder tpl, List<Expression> args) {
    	if(Objects.isNull(schemaInfo)) {
    		if(Objects.nonNull(existingSchema)) {
    			args.add(existingSchema);
    		}
    	} else {
    		StringBuilder schema = new StringBuilder(schemaInfo.schemaStr());
    		if(Objects.nonNull(existingSchema)) {
    			Expression schemaAssign = existingSchema.getAssignment();
            	if(schemaAssign instanceof J.Annotation) {
            		List<Expression> schemaArgs = ((J.Annotation)schemaAssign).getArguments();
            		for(Expression schemaArg: schemaArgs) {
            			String sa = schemaArg.toString();
            			schema.append(", ").append(sa);
            		}
            	}
    		}
    		schema.append(")");
    		tpl.append(schema.toString());
    		args.add(schemaInfo.schemaExpr());
    	}
    }
	
	private record SchemaInfo(Expression schemaExpr, String schemaStr) { }
	
}
