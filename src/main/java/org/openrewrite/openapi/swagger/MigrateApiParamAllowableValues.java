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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.search.UsesMethod;

public class MigrateApiParamAllowableValues extends Recipe {

    private static final String VBLE_NAME = "allowableValues";

    @Override
    public String getDisplayName() {
        return "Migrate `@ApiParam(allowableValues)` to `@Parameter(schema)`";
    }

    @Override
    public String getDescription() {
        return "Migrate `@ApiParam(allowableValues)` to `@Parameter(schema = @Schema(allowableValues))`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // This recipe is after ChangeType recipe
        return Preconditions.check(
                new UsesMethod<>("io.swagger.annotations.ApiParam allowableValues()", false),
                new MigrateApiParamSchemaValue(VBLE_NAME));
    }
 
}
