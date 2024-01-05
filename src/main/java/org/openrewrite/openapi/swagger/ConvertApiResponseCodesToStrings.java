package org.openrewrite.openapi.swagger;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;

public class ConvertApiResponseCodesToStrings extends Recipe {
    @Override
    public String getDisplayName() {
        return "Convert API response codes to strings";
    }

    @Override
    public String getDescription() {
        return "Convert API response codes to strings.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>(){
        // TODO: implement
        };
    }
}
