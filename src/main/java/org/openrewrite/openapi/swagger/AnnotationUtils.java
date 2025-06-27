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

import lombok.experimental.UtilityClass;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyMap;

@UtilityClass
class AnnotationUtils {
    public static Map<String, Expression> extractArgumentAssignedExpressions(J.Annotation annotation) {
        return extractArguments(annotation, J.Assignment::getAssignment);
    }

    public static Map<String, J.Assignment> extractArgumentAssignments(J.Annotation annotation) {
        return extractArguments(annotation, Function.identity());
    }

    private static <T> Map<String, T> extractArguments(J.Annotation annotation, Function<J.Assignment, T> extractor) {
        if (annotation.getArguments() == null ||
                annotation.getArguments().isEmpty() ||
                annotation.getArguments().get(0) instanceof J.Empty) {
            return emptyMap();
        }
        Map<String, T> map = new HashMap<>();
        for (Expression expression : annotation.getArguments()) {
            if (expression instanceof J.Assignment) {
                J.Assignment a = (J.Assignment) expression;
                map.put( ((J.Identifier) a.getVariable()).getSimpleName(), extractor.apply(a));
            }
        }
        return map;
    }
}
