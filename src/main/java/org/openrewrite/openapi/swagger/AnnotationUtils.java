package org.openrewrite.openapi.swagger;

import static java.util.Collections.emptyMap;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.HashMap;
import java.util.Map;

public class AnnotationUtils {
    public static Map<String, Expression> extractAnnotationArgumentAssignments(J.Annotation annotation) {
        if (annotation.getArguments() == null ||
          annotation.getArguments().isEmpty() ||
          annotation.getArguments().get(0) instanceof J.Empty) {
            return emptyMap();
        }
        Map<String, Expression> map = new HashMap<>();
        for (Expression expression : annotation.getArguments()) {
            if (expression instanceof J.Assignment) {
                J.Assignment a = (J.Assignment) expression;
                String simpleName = ((J.Identifier) a.getVariable()).getSimpleName();
                map.put(simpleName, a.getAssignment());
            }
        }
        return map;
    }
}
