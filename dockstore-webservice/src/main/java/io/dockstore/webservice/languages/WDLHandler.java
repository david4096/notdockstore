/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.webservice.languages;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.io.Files;
import io.dockstore.common.Bridge;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.ToolDAO;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wdl4s.parser.WdlParser;

/**
 * This class will eventually handle support for understanding WDL
 */
public class WDLHandler implements LanguageHandlerInterface {
    public static final Logger LOG = LoggerFactory.getLogger(WDLHandler.class);

    @Override
    public Entry parseWorkflowContent(Entry entry, String content, Set<SourceFile> sourceFiles) {
        // Use Broad WDL parser to grab data
        // Todo: Currently just checks validity of file.  In the future pull data such as author from the WDL file
        try {
            WdlParser parser = new WdlParser();
            WdlParser.TokenStream tokens;
            if (entry.getClass().equals(Tool.class)) {
                tokens = new WdlParser.TokenStream(parser.lex(content, FilenameUtils.getName(((Tool)entry).getDefaultWdlPath())));
            } else {
                tokens = new WdlParser.TokenStream(parser.lex(content, FilenameUtils.getName(((Workflow)entry).getDefaultWorkflowPath())));
            }
            WdlParser.Ast ast = (WdlParser.Ast)parser.parse(tokens).toAst();

            if (ast == null) {
                LOG.info("Error with WDL file.");
            } else {
                LOG.info("Repository has Dockstore.wdl");
            }
        } catch (WdlParser.SyntaxError syntaxError) {
            LOG.info("Invalid WDL file.");
        }

        return entry;
    }

    @Override
    public boolean isValidWorkflow(String content) {
        // TODO: this code looks like it was broken at some point, needs investigation
        //        final NamespaceWithWorkflow nameSpaceWithWorkflow = NamespaceWithWorkflow.load(content);
        //        if (nameSpaceWithWorkflow != null) {
        //            return true;
        //        }
        //
        //        return false;
        // For now as long as a file exists, it is a valid WDL
        return true;
    }

    @Override
    public Map<String, SourceFile> processImports(String repositoryId, String content, Version version,
        SourceCodeRepoInterface sourceCodeRepoInterface) {
        Map<String, SourceFile> imports = new HashMap<>();
        SourceFile.FileType fileType = SourceFile.FileType.DOCKSTORE_WDL;

        // Use matcher to get imports
        String[] lines = StringUtils.split(content, '\n');
        List<String> importPaths = new ArrayList<>();
        Pattern p = Pattern.compile("^import\\s+\"(\\S+)\"");

        for (String line : lines) {
            Matcher m = p.matcher(line);

            while (m.find()) {
                String match = m.group(1);
                if (!match.startsWith("http://") && !match.startsWith("https://")) { // Don't resolve URLs
                    importPaths.add(match.replaceFirst("file://", "")); // remove file:// from path
                }
            }
        }

        for (String importPath : importPaths) {
            SourceFile importFile = new SourceFile();

            final String fileResponse = sourceCodeRepoInterface.readGitRepositoryFile(repositoryId, fileType, version, importPath);
            if (fileResponse == null) {
                LOG.error("Could not read: " + importPath);
                continue;
            }
            importFile.setContent(fileResponse);
            importFile.setPath(importPath);
            importFile.setType(SourceFile.FileType.DOCKSTORE_WDL);
            imports.put(importFile.getPath(), importFile);
        }

        return imports;
    }

    /**
     * This method will get the content for tool tab with descriptor type = WDL
     * It will then call another method to transform the content into JSON string and return
     * @param mainDescName the name of the main descriptor
     * @param mainDescriptor the content of the main descriptor
     * @param secondaryDescContent the content of the secondary descriptors in a map, looks like file paths -> content
     * @param type tools or DAG
     * @param dao used to retrieve information on tools
     * @return either a list of tools or a json map
     */
    @Override
    public String getContent(String mainDescName, String mainDescriptor, Map<String, String> secondaryDescContent, LanguageHandlerInterface.Type type,
        ToolDAO dao) {
        // Initialize general variables
        Bridge bridge = new Bridge();
        bridge.setSecondaryFiles((HashMap<String, String>)secondaryDescContent);
        String callType = "call"; // This may change later (ex. tool, workflow)
        String toolType = "tool";
        // Initialize data structures for DAG
        Map<String, ToolInfo> toolInfoMap;
        Map<String, String> namespaceToPath;
        File tempMainDescriptor = null;
        // Write main descriptor to file
        // The use of temporary files is not needed here and might cause new problems
        try {
            tempMainDescriptor = File.createTempFile("main", "descriptor", Files.createTempDir());
            Files.asCharSink(tempMainDescriptor, StandardCharsets.UTF_8).write(mainDescriptor);

            // Iterate over each call, grab docker containers
            Map<String, String> callsToDockerMap = bridge.getCallsToDockerMap(tempMainDescriptor);
            // Iterate over each call, determine dependencies
            Map<String, List<String>> callsToDependencies = bridge.getCallsToDependencies(tempMainDescriptor);
            toolInfoMap = mapConverterToToolInfo(callsToDockerMap, callsToDependencies);
            // Get import files
            namespaceToPath = bridge.getImportMap(tempMainDescriptor);
        } catch (IOException e) {
            throw new CustomWebApplicationException("could not process wdl into DAG", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } finally {
            FileUtils.deleteQuietly(tempMainDescriptor);
        }
        return convertMapsToContent(mainDescName, type, dao, callType, toolType, toolInfoMap, namespaceToPath);
    }

    /**
     * For existing code, converts from maps of untyped data to ToolInfo
     * @param callsToDockerMap map from names of tools to Docker containers
     * @param callsToDependencies map from names of tools to names of their parent tools (dependencies)
     * @return
     */
    static Map<String, ToolInfo> mapConverterToToolInfo(Map<String, String> callsToDockerMap, Map<String, List<String>> callsToDependencies) {
        Map<String, ToolInfo> toolInfoMap;
        toolInfoMap = new HashMap<>();
        callsToDockerMap.forEach((toolName, containerName) -> toolInfoMap.compute(toolName, (key, value) -> {
            if (value == null) {
                return new ToolInfo(containerName, new ArrayList<>());
            } else {
                value.dockerContainer = containerName;
                return value;
            }
        }));
        callsToDependencies.forEach((toolName, dependencies) -> toolInfoMap.compute(toolName, (key, value) -> {
            if (value == null) {
                return new ToolInfo(null, new ArrayList<>());
            } else {
                value.toolDependencyList.addAll(dependencies);
                return value;
            }
        }));
        return toolInfoMap;
    }

    /**
     * Odd un-used method that seems like it could be useful
     * @param workflowFile
     * @return
     */
    List<String> getWdlImports(File workflowFile) {
        Bridge bridge = new Bridge();
        return bridge.getImportFiles(workflowFile);
    }
}
