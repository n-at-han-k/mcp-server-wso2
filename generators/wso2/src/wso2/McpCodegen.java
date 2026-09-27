package wso2;

import io.swagger.v3.oas.models.Operation;
import org.openapitools.codegen.CodegenConfig;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.DefaultCodegen;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.OperationsMap;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.openapitools.codegen.utils.StringUtils.underscore;

/**
 * An MCP server, one tool per operation, out of an OpenAPI document.
 *
 * Upstream writes one API file per TAG and a typed client to go with it. An
 * MCP server wants neither: every tool is dispatched through the same code
 * path -- fill the path template, attach the query, send the body -- so what
 * the generator has to produce is a TABLE, and a table is one file.
 *
 * So this class overrides exactly the two hooks that decide that, which
 * {@code -t templates/} cannot reach:
 *
 * <ul>
 *   <li>{@code addOperationToGroup} keys every operation into one group, so
 *       the whole API lands in one {@code tools.go};</li>
 *   <li>{@code toApiFilename} and {@code apiFileFolder} say where that file
 *       goes.</li>
 * </ul>
 *
 * What a tool ACCEPTS is a dereferenced JSON Schema, which is a schema walk --
 * so it travels in the document as {@code x-mcp-schema}, written by
 * {@code bin/annotate}, and the template prints it. The generator stays about
 * structure.
 */
public class McpCodegen extends DefaultCodegen implements CodegenConfig {

    /** One group: the whole document is one tool table. */
    private static final String GROUP = "tools";

    public McpCodegen() {
        super();

        outputFolder = "generated-code" + File.separator + "wso2-mcp";
        embeddedTemplateDir = templateDir = "wso2-mcp";

        // No models. Every schema is inlined into the tool that takes it --
        // the server never unmarshals a WSO2 model, it passes JSON through.
        modelTemplateFiles.clear();
        apiTemplateFiles.put("tools.mustache", ".go");

        apiPackage = "wso2";
        modelPackage = "wso2";
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.OTHER;
    }

    @Override
    public String getName() {
        return "wso2-mcp";
    }

    @Override
    public String getHelp() {
        return "Generates an MCP server in Go, one tool per operation.";
    }

    @Override
    public void processOpts() {
        super.processOpts();

        // The runtime the table is dispatched through, the process that serves
        // it, and what makes the result DEPLOYABLE rather than merely
        // compilable -- the image and the workflow that publishes it. An MCP
        // server nobody can run is a table of strings.
        supportingFiles.add(new SupportingFile("client.mustache",
                "internal" + File.separator + "wso2", "client.go"));
        supportingFiles.add(new SupportingFile("client_test.mustache",
                "internal" + File.separator + "wso2", "client_test.go"));
        supportingFiles.add(new SupportingFile("main.mustache", "", "main.go"));
        supportingFiles.add(new SupportingFile("go_mod.mustache", "", "go.mod"));
        supportingFiles.add(new SupportingFile("Dockerfile.mustache", "", "Dockerfile"));
        supportingFiles.add(new SupportingFile("dockerignore.mustache", "", ".dockerignore"));
        supportingFiles.add(new SupportingFile("build_image_workflow.mustache",
                ".github" + File.separator + "workflows", "build-image.yml"));
    }

    /**
     * Every operation, one group. Upstream keys this map on the tag, which is
     * how it gets a file per tag; an MCP server has no use for that split, and
     * a tool named in one file cannot be registered from another.
     */
    @Override
    public void addOperationToGroup(String tag, String resourcePath, Operation operation,
                                    CodegenOperation co, Map<String, List<CodegenOperation>> operations) {
        List<CodegenOperation> group =
                operations.computeIfAbsent(GROUP, key -> new java.util.ArrayList<>());

        // An operation carrying two tags is offered once per tag, and both
        // offers name this same group.
        if (group.stream().anyMatch(existing -> existing.operationId.equals(co.operationId))) {
            return;
        }

        group.add(co);
    }

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
        OperationsMap processed = super.postProcessOperationsWithModels(objs, allModels);
        List<CodegenOperation> group = processed.getOperations().getOperation();

        // A generated file that reorders itself between runs makes a diff
        // nobody can read, and the document's own order is whatever the YAML
        // happened to say.
        group.sort(Comparator.comparing(op -> op.operationId));

        for (CodegenOperation op : group) {
            // The tool name a client sees. operationId is already unique --
            // bin/merge-specs made it so across the four documents -- and
            // snake_case is what MCP tools are conventionally spelled in.
            op.vendorExtensions.put("x-mcp-name",
                    underscore(op.operationId).toLowerCase(Locale.ROOT));
        }

        return processed;
    }

    /** One file, whatever the group is called. */
    @Override
    public String toApiFilename(String name) {
        return GROUP;
    }

    @Override
    public String apiFileFolder() {
        return outputFolder + File.separator + "internal" + File.separator + "wso2";
    }

    @Override
    public String toApiName(String name) {
        return GROUP;
    }
}
