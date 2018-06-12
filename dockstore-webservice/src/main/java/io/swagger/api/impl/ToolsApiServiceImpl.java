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

package io.swagger.api.impl;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import avro.shaded.com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.swagger.api.ToolsApiService;
import io.swagger.model.Error;
import io.swagger.model.ToolContainerfile;
import io.swagger.model.ToolFile;
import io.swagger.model.ToolTests;
import io.swagger.model.ToolVersion;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.core.SourceFile.FileType.CWL_TEST_JSON;
import static io.dockstore.webservice.core.SourceFile.FileType.DOCKERFILE;
import static io.dockstore.webservice.core.SourceFile.FileType.DOCKSTORE_CWL;
import static io.dockstore.webservice.core.SourceFile.FileType.DOCKSTORE_WDL;
import static io.dockstore.webservice.core.SourceFile.FileType.WDL_TEST_JSON;

public class ToolsApiServiceImpl extends ToolsApiService {
    private static final String GITHUB_PREFIX = "git@github.com:";
    private static final String BITBUCKET_PREFIX = "git@bitbucket.org:";
    private static final int SEGMENTS_IN_ID = 3;
    private static final int DEFAULT_PAGE_SIZE = 1000;
    private static final Logger LOG = LoggerFactory.getLogger(ToolsApiServiceImpl.class);

    private static ToolDAO toolDAO = null;
    private static WorkflowDAO workflowDAO = null;
    private static DockstoreWebserviceConfiguration config = null;
    private static EntryVersionHelper<Tool, Tag, ToolDAO> toolHelper;
    private static EntryVersionHelper<Workflow, WorkflowVersion, WorkflowDAO> workflowHelper;

    public static void setToolDAO(ToolDAO toolDAO) {
        ToolsApiServiceImpl.toolDAO = toolDAO;
        ToolsApiServiceImpl.toolHelper = () -> toolDAO;
    }

    public static void setWorkflowDAO(WorkflowDAO workflowDAO) {
        ToolsApiServiceImpl.workflowDAO = workflowDAO;
        ToolsApiServiceImpl.workflowHelper = () -> workflowDAO;
    }

    public static void setConfig(DockstoreWebserviceConfiguration config) {
        ToolsApiServiceImpl.config = config;
    }

    @Override
    public Response toolsIdGet(String id, SecurityContext securityContext, ContainerRequestContext value) {
        ParsedRegistryID parsedID = new ParsedRegistryID(id);
        Entry entry = getEntry(parsedID);
        return buildToolResponse(entry, null, false);
    }

    @Override
    public Response toolsIdVersionsGet(String id, SecurityContext securityContext, ContainerRequestContext value) {
        ParsedRegistryID parsedID = new ParsedRegistryID(id);
        Entry entry = getEntry(parsedID);
        return buildToolResponse(entry, null, true);
    }

    private Response buildToolResponse(Entry container, String version, boolean returnJustVersions) {
        Response response;
        if (container == null) {
            response = Response.status(Response.Status.NOT_FOUND).build();
        } else if (!container.getIsPublished()) {
            // check whether this is registered
            response = Response.status(Response.Status.UNAUTHORIZED).build();
        } else {
            io.swagger.model.Tool tool = ToolsImplCommon.convertEntryToTool(container, config);
            assert (tool != null);
            // filter out other versions if we're narrowing to a specific version
            if (version != null) {
                tool.getVersions().removeIf(v -> !v.getName().equals(version));
                if (tool.getVersions().size() != 1) {
                    response = Response.status(Response.Status.NOT_FOUND).build();
                } else {
                    response = Response.ok(tool.getVersions().get(0)).build();
                }
            } else {
                if (returnJustVersions) {
                    response = Response.ok(tool.getVersions()).build();
                } else {
                    response = Response.ok(tool).build();
                }
            }
        }
        return response;
    }

    @Override
    public Response toolsIdVersionsVersionIdGet(String id, String versionId, SecurityContext securityContext, ContainerRequestContext value) {
        ParsedRegistryID parsedID = new ParsedRegistryID(id);
        try {
            versionId = URLDecoder.decode(versionId, StandardCharsets.UTF_8.displayName());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        Entry entry = getEntry(parsedID);
        return buildToolResponse(entry, versionId, false);
    }

    public Entry<?,?> getEntry(ParsedRegistryID parsedID) {
        Entry<?,?> entry;
        String entryPath = parsedID.getPath();
        String entryName = parsedID.getToolName().isEmpty() ? null : parsedID.getToolName();
        if (entryName != null) {
            entryPath += "/" + parsedID.getToolName();
        }
        if (parsedID.isTool()) {
            entry = toolDAO.findByPath(entryPath, true);
        } else {
            entry = workflowDAO.findByPath(entryPath, true);
        }
        return entry;
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeDescriptorGet(String type, String id, String versionId, SecurityContext securityContext,
        ContainerRequestContext value) {
        SourceFile.FileType fileType = getFileType(type);
        if (fileType == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return getFileByToolVersionID(id, versionId, fileType, null,
            value.getAcceptableMediaTypes().contains(MediaType.TEXT_PLAIN_TYPE) || StringUtils.containsIgnoreCase(type, "plain"));
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeDescriptorRelativePathGet(String type, String id, String versionId, String relativePath,
        SecurityContext securityContext, ContainerRequestContext value) {
        if (type == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        SourceFile.FileType fileType = getFileType(type);
        if (fileType == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return getFileByToolVersionID(id, versionId, fileType, relativePath,
            value.getAcceptableMediaTypes().contains(MediaType.TEXT_PLAIN_TYPE) || StringUtils.containsIgnoreCase(type, "plain"));
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeTestsGet(String type, String id, String versionId, SecurityContext securityContext,
        ContainerRequestContext value) {
        if (type == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        SourceFile.FileType fileType = getFileType(type);
        if (fileType == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // The getFileType version never returns *TEST_JSON filetypes.  Linking CWL_TEST_JSON with DOCKSTORE_CWL and etc until solved.
        boolean plainTextResponse =
            value.getAcceptableMediaTypes().contains(MediaType.TEXT_PLAIN_TYPE) || type.toLowerCase().contains("plain");
        switch (fileType) {
        case CWL_TEST_JSON:
        case DOCKSTORE_CWL:
            return getFileByToolVersionID(id, versionId, CWL_TEST_JSON, null, plainTextResponse);
        case WDL_TEST_JSON:
        case DOCKSTORE_WDL:
            return getFileByToolVersionID(id, versionId, WDL_TEST_JSON, null, plainTextResponse);
        case NEXTFLOW:
        case NEXTFLOW_CONFIG:
        case NEXTFLOW_TEST_PARAMS:
            return getFileByToolVersionID(id, versionId, SourceFile.FileType.NEXTFLOW_TEST_PARAMS, null, plainTextResponse);
        default:
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    private SourceFile.FileType getFileType(String format) {
        SourceFile.FileType type;
        if (StringUtils.containsIgnoreCase(format, "CWL")) {
            type = DOCKSTORE_CWL;
        } else if (StringUtils.containsIgnoreCase(format, "WDL")) {
            type = DOCKSTORE_WDL;
        } else if (StringUtils.containsIgnoreCase(format, "NFL")) {
            type = SourceFile.FileType.NEXTFLOW_CONFIG;
        } else if (Objects.equals("JSON", format)) {
            // if JSON is specified
            type = DOCKSTORE_CWL;
        } else {
            // TODO: no other descriptor formats implemented for now
            type = null;
        }
        return type;
    }

    @Override
    public Response toolsIdVersionsVersionIdContainerfileGet(String id, String versionId, SecurityContext securityContext,
        ContainerRequestContext value) {
        boolean unwrap = !value.getAcceptableMediaTypes().contains(MediaType.APPLICATION_JSON_TYPE);
        return getFileByToolVersionID(id, versionId, DOCKERFILE, null, unwrap);
    }

    @SuppressWarnings("CheckStyle")
    @Override
    public Response toolsGet(String registryId, String registry, String organization, String name, String toolname, String description,
        String author, String offset, Integer limit, SecurityContext securityContext, ContainerRequestContext value) {
        final List<Entry> all = new ArrayList<>();
        all.addAll(toolDAO.findAllPublished());
        all.addAll(workflowDAO.findAllPublished());
        all.sort(Comparator.comparing(Entry::getGitUrl));

        List<io.swagger.model.Tool> results = new ArrayList<>();
        for (Entry c : all) {
            if (c instanceof Workflow && (registryId != null || registry != null || organization != null || name != null
                || toolname != null)) {
                continue;
            }

            if (c instanceof Tool) {
                Tool tool = (Tool)c;
                // check each criteria. This sucks. Can we do this better with reflection? Or should we pre-convert?
                if (registryId != null) {
                    if (!registryId.contains(tool.getToolPath())) {
                        continue;
                    }
                }
                if (registry != null && tool.getRegistry() != null) {
                    if (!tool.getRegistry().contains(registry)) {
                        continue;
                    }
                }
                if (organization != null && tool.getNamespace() != null) {
                    if (!tool.getNamespace().contains(organization)) {
                        continue;
                    }
                }
                if (name != null && tool.getName() != null) {
                    if (!tool.getName().contains(name)) {
                        continue;
                    }
                }
                if (toolname != null && tool.getToolname() != null) {
                    if (!tool.getToolname().contains(toolname)) {
                        continue;
                    }
                }
            }
            if (description != null && c.getDescription() != null) {
                if (!c.getDescription().contains(description)) {
                    continue;
                }
            }
            if (author != null && c.getAuthor() != null) {
                if (!c.getAuthor().contains(author)) {
                    continue;
                }
            }
            // if passing, for each container that matches the criteria, convert to standardised format and return
            io.swagger.model.Tool tool = ToolsImplCommon.convertEntryToTool(c, config);
            if (tool != null) {
                results.add(tool);
            }
        }

        if (limit == null) {
            limit = DEFAULT_PAGE_SIZE;
        }
        List<List<io.swagger.model.Tool>> pagedResults = Lists.partition(results, limit);
        int offsetInteger = 0;
        if (offset != null) {
            offsetInteger = Integer.parseInt(offset);
        }
        if (offsetInteger >= pagedResults.size()) {
            results = new ArrayList<>();
        } else {
            results = pagedResults.get(offsetInteger);
        }
        final Response.ResponseBuilder responseBuilder = Response.ok(results);
        responseBuilder.header("current_offset", offset);
        responseBuilder.header("current_limit", limit);
        responseBuilder.header("self_link", value.getUriInfo().getRequestUri().toString());
        // construct links to other pages
        try {
            List<String> filters = new ArrayList<>();
            handleParameter(registryId, "id", filters);
            handleParameter(organization, "organization", filters);
            handleParameter(name, "name", filters);
            handleParameter(toolname, "toolname", filters);
            handleParameter(description, "description", filters);
            handleParameter(author, "author", filters);
            handleParameter(registry, "registry", filters);
            handleParameter(limit.toString(), "limit", filters);

            if (offsetInteger + 1 < pagedResults.size()) {
                URI nextPageURI = new URI(config.getScheme(), null, config.getHostname(), Integer.parseInt(config.getPort()),
                    DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools",
                    Joiner.on('&').join(filters) + "&offset=" + (offsetInteger + 1), null);
                responseBuilder.header("next_page", nextPageURI.toURL().toString());
            }
            URI lastPageURI = new URI(config.getScheme(), null, config.getHostname(), Integer.parseInt(config.getPort()),
                DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools",
                Joiner.on('&').join(filters) + "&offset=" + (pagedResults.size() - 1), null);
            responseBuilder.header("last_page", lastPageURI.toURL().toString());

        } catch (URISyntaxException | MalformedURLException e) {
            throw new WebApplicationException("Could not construct page links", HttpStatus.SC_BAD_REQUEST);
        }
        return responseBuilder.build();
    }

    private void handleParameter(String parameter, String queryName, List<String> filters) {
        if (parameter != null) {
            filters.add(queryName + "=" + parameter);
        }
    }

    /**
     * @param gitUrl       The git formatted url for the repo
     * @param reference    the git tag or branch
     * @param githubPrefix the prefix for the git formatted url to strip out
     * @param builtPrefix  the prefix to use to start the extracted prefix
     * @return the prefix to access these files
     */
    private static String extractHTTPPrefix(String gitUrl, String reference, String githubPrefix, String builtPrefix) {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(builtPrefix);
        final String substring = gitUrl.substring(githubPrefix.length(), gitUrl.lastIndexOf(".git"));
        urlBuilder.append(substring).append(builtPrefix.contains("bitbucket.org") ? "/raw/" : '/').append(reference);
        return urlBuilder.toString();
    }

    /**
     * @param registryId   registry id
     * @param versionId    git reference
     * @param type         type of file
     * @param relativePath if null, return the primary descriptor, if not null, return a specific file
     * @param unwrap       unwrap the file and present the descriptor sans wrapper model
     * @return a specific file wrapped in a response
     */
    private Response getFileByToolVersionID(String registryId, String versionId, SourceFile.FileType type, String relativePath,
        boolean unwrap) {
        // if a version is provided, get that version, otherwise return the newest
        ParsedRegistryID parsedID = new ParsedRegistryID(registryId);
        try {
            versionId = URLDecoder.decode(versionId, StandardCharsets.UTF_8.displayName());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        Entry<?,?> entry = getEntry(parsedID);
        // check whether this is registered
        if (entry == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!entry.getIsPublished()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        final io.swagger.model.Tool convertedTool = ToolsImplCommon.convertEntryToTool(entry, config);

        String finalVersionId = versionId;
        if (convertedTool == null || convertedTool.getVersions() == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        final Optional<ToolVersion> convertedToolVersion = convertedTool.getVersions().stream()
            .filter(toolVersion -> toolVersion.getName().equalsIgnoreCase(finalVersionId)).findFirst();
        Optional<? extends Version> entryVersion;
        if (entry instanceof Tool) {
            Tool toolEntry = (Tool)entry;
            entryVersion = toolEntry.getVersions().stream().filter(toolVersion -> toolVersion.getName().equalsIgnoreCase(finalVersionId))
                .findFirst();
        } else {
            Workflow workflowEntry = (Workflow)entry;
            entryVersion = workflowEntry.getVersions().stream()
                .filter(toolVersion -> toolVersion.getName().equalsIgnoreCase(finalVersionId)).findFirst();
        }

        if (!entryVersion.isPresent()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String urlBuilt;
        String gitUrl = entry.getGitUrl();
        if (gitUrl.startsWith(GITHUB_PREFIX)) {
            urlBuilt = extractHTTPPrefix(gitUrl, entryVersion.get().getReference(), GITHUB_PREFIX, "https://raw.githubusercontent.com/");
        } else if (gitUrl.startsWith(BITBUCKET_PREFIX)) {
            urlBuilt = extractHTTPPrefix(gitUrl, entryVersion.get().getReference(), BITBUCKET_PREFIX, "https://bitbucket.org/");
        } else {
            LOG.error("Found a git url neither from BitBucket or GitHub " + gitUrl);
            urlBuilt = "https://unimplemented_git_repository/";
        }

        if (convertedToolVersion.isPresent()) {
            final ToolVersion toolVersion = convertedToolVersion.get();
            switch (type) {
            case WDL_TEST_JSON:
            case CWL_TEST_JSON:
            case NEXTFLOW_TEST_PARAMS:
                // this only works for test parameters associated with tools
                List<SourceFile> testSourceFiles = new ArrayList<>();
                try {
                    testSourceFiles.addAll(toolHelper.getAllSourceFiles(entry.getId(), versionId, type));
                } catch (CustomWebApplicationException e) {
                    LOG.warn("intentionally ignoring failure to get test parameters", e);
                }
                try {
                    testSourceFiles.addAll(workflowHelper.getAllSourceFiles(entry.getId(), versionId, type));
                } catch (CustomWebApplicationException e) {
                    LOG.warn("intentionally ignoring failure to get source files", e);
                }

                List<ToolTests> toolTestsList = new ArrayList<>();

                for (SourceFile file : testSourceFiles) {
                    ToolTests toolTests = ToolsImplCommon.sourceFileToToolTests(urlBuilt, file);
                    toolTestsList.add(toolTests);
                }
                return Response.status(Response.Status.OK).type(unwrap ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON).entity(
                    unwrap ? toolTestsList.stream().map(ToolTests::getTest).filter(Objects::nonNull).collect(Collectors.joining("\n"))
                        : toolTestsList).build();
            case DOCKERFILE:
                Optional<SourceFile> potentialDockerfile = entryVersion.get().getSourceFiles().stream()
                    .filter(sourcefile -> ((SourceFile)sourcefile).getType() == SourceFile.FileType.DOCKERFILE).findFirst();
                if (potentialDockerfile.isPresent()) {
                    ToolContainerfile dockerfile = new ToolContainerfile();
                    dockerfile.setContainerfile(potentialDockerfile.get().getContent());
                    dockerfile.setUrl(urlBuilt + ((Tag)entryVersion.get()).getDockerfilePath());
                    toolVersion.setContainerfile(true);
                    List<ToolContainerfile> containerfilesList = new ArrayList<>();
                    containerfilesList.add(dockerfile);
                    return Response.status(Response.Status.OK).type(unwrap ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON)
                        .entity(unwrap ? dockerfile.getContainerfile() : containerfilesList).build();
                }
            default:
                Set<String> primaryDescriptors = new HashSet<>();
                String path;
                // figure out primary descriptors and use them if no relative path is specified
                if (entry instanceof Tool) {
                    if (type == DOCKSTORE_WDL) {
                        path = ((Tag)entryVersion.get()).getWdlPath();
                        primaryDescriptors.add(path);
                    } else if (type == DOCKSTORE_CWL) {
                        path = ((Tag)entryVersion.get()).getCwlPath();
                        primaryDescriptors.add(path);
                    } else {
                        return Response.status(Response.Status.NOT_FOUND).build();
                    }
                } else {
                    path = ((WorkflowVersion)entryVersion.get()).getWorkflowPath();
                    primaryDescriptors.add(path);
                }
                String searchPath;
                if (relativePath != null) {
                    searchPath = cleanRelativePath(relativePath);
                } else {
                    searchPath = path;
                }

                final Set<SourceFile> sourceFiles = entryVersion.get().getSourceFiles();
                // annoyingly, test json and Dockerfiles include a fullpath whereas descriptors are just relative to the main descriptor,
                // so in this stream we need to standardize relative to the main descriptor
                Optional<SourceFile> correctSourceFile = lookForFilePath(sourceFiles, searchPath, entryVersion.get().getWorkingDirectory());
                if (correctSourceFile.isPresent()) {
                    SourceFile sourceFile = correctSourceFile.get();
                    // annoyingly, test json, Dockerfiles, primaries include a fullpath whereas secondary descriptors
                    // are just relative to the main descriptor this affects the url that needs to be built
                    // in a non-hotfix, this could re-use code from the file listing
                    StringBuilder sourceFileUrl = new StringBuilder(urlBuilt);
                    if (!SourceFile.TEST_FILE_TYPES.contains(sourceFile.getType()) && sourceFile.getType() != SourceFile.FileType.DOCKERFILE
                        && !primaryDescriptors.contains(sourceFile.getPath())) {
                        sourceFileUrl.append(StringUtils.prependIfMissing(entryVersion.get().getWorkingDirectory(), "/"));
                    }
                    Object toolDescriptor = ToolsImplCommon.sourceFileToToolDescriptor(sourceFileUrl.toString(), sourceFile);
                    if (toolDescriptor == null) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                    }
                }

                // annoyingly, test json and Dockerfiles include a fullpath whereas descriptors are just relative to the main descriptor,
                // so in this stream we need to standardize relative to the main descriptor
                if (correctSourceFile.isPresent()) {
                    SourceFile sourceFile = correctSourceFile.get();
                    // annoyingly, test json, Dockerfiles, primaries include a fullpath whereas secondary descriptors
                    // are just relative to the main descriptor this affects the url that needs to be built
                    // in a non-hotfix, this could re-use code from the file listing
                    StringBuilder sourceFileUrl = new StringBuilder(urlBuilt);
                    if (!SourceFile.TEST_FILE_TYPES.contains(sourceFile.getType()) && sourceFile.getType() != SourceFile.FileType.DOCKERFILE
                        && !primaryDescriptors.contains(sourceFile.getPath())) {
                        sourceFileUrl.append(StringUtils.prependIfMissing(entryVersion.get().getWorkingDirectory(), "/"));
                    }
                    Object toolDescriptor = ToolsImplCommon.sourceFileToToolDescriptor(sourceFileUrl.toString(), sourceFile);
                    if (toolDescriptor == null) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                    }
                    return Response.status(Response.Status.OK).type(unwrap ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON)
                        .entity(unwrap ? sourceFile.getContent() : toolDescriptor).build();
                }
            }
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    /**
     * Return a matching source file
     *
     * @param sourceFiles      files to look through
     * @param searchPath       file to look for
     * @param workingDirectory working directory if relevant
     * @return
     */
    public Optional<SourceFile> lookForFilePath(Set<SourceFile> sourceFiles, String searchPath, String workingDirectory) {
        // ignore leading slashes
        searchPath = cleanRelativePath(searchPath);

        for (SourceFile sourceFile : sourceFiles) {
            String calculatedPath = sourceFile.getPath();
            // annoyingly, test json and Dockerfiles include a fullpath whereas descriptors are just relative to the main descriptor,
            // so we need to standardize relative to the main descriptor
            if (SourceFile.TEST_FILE_TYPES.contains(sourceFile.getType())) {
                calculatedPath = StringUtils.removeStart(cleanRelativePath(sourceFile.getPath()), cleanRelativePath(workingDirectory));
            }
            calculatedPath = cleanRelativePath(calculatedPath);
            if (searchPath.equalsIgnoreCase(calculatedPath) || searchPath
                .equalsIgnoreCase(StringUtils.removeStart(calculatedPath, workingDirectory + "/"))) {
                return Optional.of(sourceFile);
            }
        }
        return Optional.empty();
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeFilesGet(String type, String id, String versionId, SecurityContext securityContext, ContainerRequestContext containerRequestContext) {
        ParsedRegistryID parsedID = new ParsedRegistryID(id);
        Entry entry = getEntry(parsedID);
        List<String> primaryDescriptorPaths = new ArrayList<>();
        if (entry instanceof Workflow) {
            Workflow workflow = (Workflow)entry;
            Set<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
            Optional<WorkflowVersion> first = workflowVersions.stream()
                .filter(workflowVersion -> workflowVersion.getName().equals(versionId)).findFirst();
            if (first.isPresent()) {
                WorkflowVersion workflowVersion = first.get();
                // Matching the workflow path in a workflow automatically indicates that the file is a primary descriptor
                primaryDescriptorPaths.add(workflowVersion.getWorkflowPath());
                Set<SourceFile> sourceFiles = workflowVersion.getSourceFiles();
                List<ToolFile> toolFiles = getToolFiles(sourceFiles, primaryDescriptorPaths, type, workflowVersion.getWorkingDirectory());
                return Response.ok().entity(toolFiles).build();
            } else {
                return Response.noContent().build();
            }
        } else if (entry instanceof Tool) {
            Tool tool = (Tool)entry;
            Set<Tag> versions = tool.getVersions();
            Optional<Tag> first = versions.stream().filter(tag -> tag.getName().equals(versionId)).findFirst();
            if (first.isPresent()) {
                Tag tag = first.get();
                // Matching the CWL path or WDL path in a tool automatically indicates that the file is a primary descriptor
                primaryDescriptorPaths.add(tag.getCwlPath());
                primaryDescriptorPaths.add(tag.getWdlPath());
                Set<SourceFile> sourceFiles = tag.getSourceFiles();
                List<ToolFile> toolFiles = getToolFiles(sourceFiles, primaryDescriptorPaths, type, tag.getWorkingDirectory());
                return Response.ok().entity(toolFiles).build();
            } else {
                return Response.noContent().build();
            }
        } else {
            return Response.noContent().build();
        }
    }

    /**
     * Converts SourceFile.FileType to ToolFile.FileTypeEnum
     *
     * @param fileType The SourceFile.FileType
     * @return The ToolFile.FileTypeEnum
     */
    private ToolFile.FileTypeEnum fileTypeToToolFileFileTypeEnum(SourceFile.FileType fileType) {
        switch (fileType) {
        case NEXTFLOW_TEST_PARAMS:
        case CWL_TEST_JSON:
        case WDL_TEST_JSON:
            return ToolFile.FileTypeEnum.TEST_FILE;
        case DOCKERFILE:
            return ToolFile.FileTypeEnum.CONTAINERFILE;
        case DOCKSTORE_WDL:
        case DOCKSTORE_CWL:
            return ToolFile.FileTypeEnum.SECONDARY_DESCRIPTOR;
        case NEXTFLOW_CONFIG:
            return ToolFile.FileTypeEnum.PRIMARY_DESCRIPTOR;
        case NEXTFLOW:
            return ToolFile.FileTypeEnum.SECONDARY_DESCRIPTOR;
        default:
            return ToolFile.FileTypeEnum.OTHER;
        }
    }

    /**
     * Converts a list of SourceFile to a list of ToolFile.
     *
     * @param sourceFiles    The list of SourceFile to convert
     * @param mainDescriptor The main descriptor path, used to determine if the file is a primary or secondary descriptor
     * @return A list of ToolFile for the Tool
     */
    private List<ToolFile> getToolFiles(Set<SourceFile> sourceFiles, List<String> mainDescriptor, String type, String workingDirectory) {
        List<SourceFile> filteredSourceFiles = filterSourcefiles(sourceFiles, type);
        return filteredSourceFiles.stream().map(file -> {
            ToolFile toolFile = new ToolFile();
            toolFile.setPath(file.getPath());
            ToolFile.FileTypeEnum fileTypeEnum = fileTypeToToolFileFileTypeEnum(file.getType());
            if (fileTypeEnum.equals(ToolFile.FileTypeEnum.SECONDARY_DESCRIPTOR) && mainDescriptor.contains(file.getPath())) {
                fileTypeEnum = ToolFile.FileTypeEnum.PRIMARY_DESCRIPTOR;
            }
            if (!fileTypeEnum.equals(ToolFile.FileTypeEnum.SECONDARY_DESCRIPTOR)) {
                Path pathBase = Paths.get(workingDirectory.isEmpty() ? "/" : StringUtils.prependIfMissing(workingDirectory, "/"));
                Path specificPath = Paths.get(file.getPath());
                Path pathRelative = pathBase.relativize(specificPath);
                toolFile.setPath(pathRelative.toString());
            }
            toolFile.setFileType(fileTypeEnum);
            return toolFile;
        }).sorted(Comparator.comparing(ToolFile::getPath)).collect(Collectors.toList());
    }

    /**
     * Filters the source files to only show the ones that are possibly relevant to the type (CWL or WDL)
     *
     * @param sourceFiles The original source files for the Tool
     * @param type        The type (CWL or WDL), nextflow is not currently handled
     * @return A list of source files that are possibly relevant to the type (CWL or WDL)
     */
    private List<SourceFile> filterSourcefiles(Set<SourceFile> sourceFiles, String type) {
        switch (type) {
        case "CWL":
            return sourceFiles.stream().filter(this::isCWL).collect(Collectors.toList());
        case "WDL":
            return sourceFiles.stream().filter(this::isWDL).collect(Collectors.toList());
        case "NFL":
            return sourceFiles.stream().filter(this::isNFL).collect(Collectors.toList());
        default:
            throw new CustomWebApplicationException("Unknown descriptor type.", HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * This checks whether the sourcefile is CWL
     *
     * @param sourceFile the sourcefile to check
     * @return true if the sourcefile is CWL-related, false otherwise
     */
    private boolean isCWL(SourceFile sourceFile) {
        SourceFile.FileType type = sourceFile.getType();
        return Stream.of(SourceFile.FileType.CWL_TEST_JSON, SourceFile.FileType.DOCKERFILE, SourceFile.FileType.DOCKSTORE_CWL)
            .anyMatch(type::equals);
    }

    /**
     * This checks whether the sourcefile is WDL
     *
     * @param sourceFile the sourcefile to check
     * @return true if the sourcefile is WDL-related, false otherwise
     */
    private boolean isWDL(SourceFile sourceFile) {
        SourceFile.FileType type = sourceFile.getType();
        return Stream.of(SourceFile.FileType.WDL_TEST_JSON, SourceFile.FileType.DOCKERFILE, SourceFile.FileType.DOCKSTORE_WDL)
            .anyMatch(type::equals);
    }

    /**
     * This checks whether the sourcefile is Nextflow
     * @param sourceFile the sourcefile to check
     * @return true if the sourcefile is WDL-related, false otherwise
     */
    private boolean isNFL(SourceFile sourceFile) {
        SourceFile.FileType type = sourceFile.getType();
        return Stream.of(SourceFile.FileType.NEXTFLOW_CONFIG, SourceFile.FileType.DOCKERFILE, SourceFile.FileType.NEXTFLOW, SourceFile.FileType.NEXTFLOW_TEST_PARAMS).anyMatch(type::equals);
    }

    private String cleanRelativePath(String relativePath) {
        String cleanRelativePath = StringUtils.stripStart(relativePath, "./");
        return StringUtils.stripStart(cleanRelativePath, "/");
    }

    /**
     * Used to parse localised IDs (no URL)
     * If tool, the id will look something like "registry.hub.docker.com/sequenza/sequenza"
     * If workflow, the id will look something like "#workflow/DockstoreTestUser/dockstore-whalesay/dockstore-whalesay-wdl"
     * Both cases have registry/organization/name/toolName but workflows have a "#workflow" prepended to it.
     */
    public static class ParsedRegistryID {
        private boolean tool = true;
        private String registry;
        private String organization;
        private String name;
        private String toolName;

        public ParsedRegistryID(String id) {
            try {
                id = URLDecoder.decode(id, StandardCharsets.UTF_8.displayName());
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            List<String> textSegments = Splitter.on('/').omitEmptyStrings().splitToList(id);
            List<String> list = new ArrayList<>(textSegments);
            if (list.get(0).equalsIgnoreCase("#workflow")) {
                list.remove(0); // Remove #workflow from ArrayList to make parsing similar to tool
                tool = false;
            }
            checkToolId(list);
            registry = list.get(0);
            organization = list.get(1);
            name = list.get(2);
            toolName = list.size() > SEGMENTS_IN_ID ? list.get(SEGMENTS_IN_ID) : "";
        }

        /**
         * This checks if the GA4GH toolId string segments provided by the user is of proper length
         * If it is not the proper length, returns an Error response object similar to what's defined for the
         * 404 response in the GA4GH swagger.yaml
         * @param toolIdStringSegments    The toolId provided by the user which was split into string segments
         */
        private void checkToolId(List<String> toolIdStringSegments) {
            if (toolIdStringSegments.size() < SEGMENTS_IN_ID) {
                Error error = new Error();
                error.setCode(HttpStatus.SC_BAD_REQUEST);
                error.setMessage("Tool ID should have at least 3 separate segments, seperated by /");
                Response errorResponse = Response.status(HttpStatus.SC_BAD_REQUEST).entity(error).type(MediaType.APPLICATION_JSON).build();
                throw new WebApplicationException(errorResponse);
            }
        }

        public String getRegistry() {
            return registry;
        }

        public String getOrganization() {
            return organization;
        }

        public String getName() {
            return name;
        }

        String getToolName() {
            return toolName;
        }

        /**
         * Get an internal path
         *
         * @return an internal path, usable only if we know if we have a tool or workflow
         */
        public String getPath() {
            return registry + "/" + organization + "/" + name;
        }

        public boolean isTool() {
            return tool;
        }
    }
}
