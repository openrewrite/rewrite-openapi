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

import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;

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
