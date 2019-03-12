/*
 *    Copyright 2018 OICR
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
package io.dockstore.webservice.resources;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.MoreObjects;
import com.google.gson.Gson;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.Registry;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.helpers.ElasticManager;
import io.dockstore.webservice.helpers.ElasticMode;
import io.dockstore.webservice.helpers.FileFormatHelper;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.FileFormatDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.VersionDAO;
import io.dockstore.webservice.permissions.PermissionsInterface;
import io.dockstore.webservice.permissions.Role;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.dropwizard.jersey.PATCH;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Methods to create and edit hosted tool and workflows.
 * Reuse existing methods to GET them, add labels to them, and other operations.
 *
 * @author dyuen
 */
@Api("hosted")
@Produces(MediaType.APPLICATION_JSON)
public abstract class AbstractHostedEntryResource<T extends Entry<T, U>, U extends Version<U>, W extends EntryDAO<T>, X extends VersionDAO<U>>
        implements AuthenticatedResourceInterface {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractHostedEntryResource.class);
    final ElasticManager elasticManager;
    private final FileDAO fileDAO;
    private final UserDAO userDAO;
    private final PermissionsInterface permissionsInterface;
    private final FileFormatDAO fileFormatDAO;
    private final int calculatedEntryLimit;
    private final int calculatedEntryVersionLimit;

    AbstractHostedEntryResource(SessionFactory sessionFactory, PermissionsInterface permissionsInterface, DockstoreWebserviceConfiguration.LimitConfig limitConfig) {
        this.fileFormatDAO = new FileFormatDAO(sessionFactory);
        this.fileDAO = new FileDAO(sessionFactory);
        this.elasticManager = new ElasticManager();
        this.userDAO = new UserDAO(sessionFactory);
        this.permissionsInterface = permissionsInterface;
        this.calculatedEntryLimit = MoreObjects.firstNonNull(limitConfig.getWorkflowLimit(), Integer.MAX_VALUE);
        this.calculatedEntryVersionLimit = MoreObjects.firstNonNull(limitConfig.getWorkflowVersionLimit(), Integer.MAX_VALUE);
    }

    /**
     * Convenience method to return a DAO responsible for creating T
     *
     * @return a DAO that handles T
     */
    protected abstract W getEntryDAO();

    /**
     * Convenience method to return a DAO responsible for creating U
     *
     * @return a DAO that handles U
     */
    protected abstract X getVersionDAO();

    @POST
    @Path("/hostedEntry")
    @Timed
    @UnitOfWork
    public T createHosted(@ApiParam(hidden = true) @Auth User user,
        @ApiParam(value = "The Docker registry (Tools only)") @QueryParam("registry") String registry,
        @ApiParam(value = "The repository name", required = true) @QueryParam("name") String name,
        @ApiParam(value = "The descriptor type (Workflows only)") @QueryParam("descriptorType") String descriptorType,
        @ApiParam(value = "The Docker namespace (Tools only)") @QueryParam("namespace") String namespace,
            @ApiParam(value = "Optional entry name (Tools only)") @QueryParam("entryName") String entryName) {

        // check if the user has hit a limit yet
        final long currentCount = getEntryDAO().countAllHosted(user.getId());
        final int limit = user.getHostedEntryCountLimit() != null ? user.getHostedEntryCountLimit() : calculatedEntryLimit;
        if (currentCount >= limit) {
            throw new CustomWebApplicationException("You have " + currentCount + " workflows which is at the current limit of " + limit, HttpStatus.SC_PAYMENT_REQUIRED);
        }

        // Only check type for workflows
        DescriptorLanguage convertedDescriptorType = checkType(descriptorType);
        Registry convertedRegistry = checkRegistry(registry);
        T entry = getEntry(user, convertedRegistry, name, convertedDescriptorType, namespace, entryName);
        checkForDuplicatePath(entry);
        long l = getEntryDAO().create(entry);
        T byId = getEntryDAO().findById(l);
        elasticManager.handleIndexUpdate(byId, ElasticMode.UPDATE);
        return byId;
    }

    protected abstract void checkForDuplicatePath(T entry);

    /**
     * TODO: ugly, too many strings lead to an easy mix-up of order.
     * @param user User object
     * @param registry Registry of tool (Tools only)
     * @param name Repository name
     * @param descriptorType Type of descriptor (Workflows only)
     * @param namespace Namespace of tool (Tools only)
     * @param entryName Optional entry name
     * @return Newly created entry
     */
    protected abstract T getEntry(User user, Registry registry, String name, DescriptorLanguage descriptorType, String namespace, String entryName);

    @Override
    public void checkUserCanUpdate(User user, Entry entry) {
        try {
            checkUser(user, entry);
        } catch (CustomWebApplicationException ex) {
            if (entry instanceof Workflow) {
                if (!permissionsInterface.canDoAction(user, (Workflow)entry, Role.Action.WRITE)) {
                    throw ex;
                }
            } else {
                throw ex;
            }
        }
    }

    @PATCH
    @io.swagger.jaxrs.PATCH
    @Path("/hostedEntry/{entryId}")
    @Timed
    @UnitOfWork
    public T editHosted(@ApiParam(hidden = true) @Auth User user,
        @ApiParam(value = "Entry to modify.", required = true) @PathParam("entryId") Long entryId,
        @ApiParam(value = "Set of updated sourcefiles, add files by adding new files with unknown paths, delete files by including them with emptied content", required = true) Set<SourceFile> sourceFiles) {
        T entry = getEntryDAO().findById(entryId);
        checkEntry(entry);
        checkUserCanUpdate(user, entry);
        checkHosted(entry);

        // check if the user has hit a limit yet
        final long currentCount = entry.getVersions().size();
        final int limit = user.getHostedEntryVersionsLimit() != null ? user.getHostedEntryVersionsLimit() : calculatedEntryVersionLimit;
        if (currentCount >= limit) {
            throw new CustomWebApplicationException("You have " + currentCount + " workflow versions which is at the current limit of " + limit, HttpStatus.SC_PAYMENT_REQUIRED);
        }

        updateUnsetAbsolutePaths(sourceFiles);

        U version = getVersion(entry);
        Set<SourceFile> versionSourceFiles = handleSourceFileMerger(entryId, sourceFiles, entry, version);

        return saveVersion(user, entryId, entry, version, versionSourceFiles, Optional.empty());
    }

    /**
     * Saves a version of a hosted entry.
     *
     * This code was extracted from the editHosted method for reuse with with the zip posting functionality. For zips, the
     * mainDescriptor parameter was added.
     *
     * Until the zip support, hosted entries only supported a workflow path of /Dockstore.[wdl|cwl]. With zips, the user can
     * specify any workflow path. The mainDescriptor is used to override the default /Dockstore.[wdl\cwl] within a version.
     *
     *
     * @param user
     * @param entryId
     * @param entry
     * @param version
     * @param versionSourceFiles
     * @param mainDescriptor the path of the main descriptor if different than the workflow default
     * @return
     */
    T saveVersion(User user, Long entryId, T entry, U version, Set<SourceFile> versionSourceFiles, Optional<SourceFile> mainDescriptor) {
        final U validatedVersion = versionValidation(version, entry, mainDescriptor);

        boolean isValidVersion = isValidVersion(validatedVersion);
        if (!isValidVersion) {
            String fallbackMessage = "Your edited files are invalid. No new version was created. Please check your syntax and try again.";
            String validationMessages = createValidationMessages(validatedVersion);
            validationMessages = (validationMessages != null && !validationMessages.isEmpty()) ? validationMessages : fallbackMessage;
            throw new CustomWebApplicationException(validationMessages, HttpStatus.SC_BAD_REQUEST);
        }

        validatedVersion.setValid(true); // Hosted entry versions must be valid to save
        validatedVersion.setVersionEditor(user);
        populateMetadata(versionSourceFiles, entry, validatedVersion);
        long l = getVersionDAO().create(validatedVersion);
        entry.getVersions().add(getVersionDAO().findById(l));
        entry.setLastModified(validatedVersion.getLastModified());
        FileFormatHelper.updateFileFormats(entry.getVersions(), fileFormatDAO);
        userDAO.clearCache();
        T newTool = getEntryDAO().findById(entryId);
        elasticManager.handleIndexUpdate(newTool, ElasticMode.UPDATE);
        return newTool;
    }

    /**
     * For all source files whose absolutePath is not set, set the absolutePath to the path.
     *
     * The absolutePath may not be null in the database, but it is not set when the UI invokes
     * the Webservice API.
     *
     * @param sourceFiles
     */
    private void updateUnsetAbsolutePaths(Set<SourceFile> sourceFiles) {
        sourceFiles.stream().forEach(sourceFile -> {
            if (sourceFile.getAbsolutePath() == null) {
                sourceFile.setAbsolutePath(sourceFile.getPath());
            }
        });
    }

    /**
     * Prints out all of the invalid validations
     * Used for returning error messages on attempting to save
     * @param version version of interest
     * @return String containing all invalid validation messages
     */
    protected String createValidationMessages(U version) {
        StringBuilder result = new StringBuilder();
        result.append("Unable to save the new version due to the following error(s): ");
        Gson g = new Gson();
        for (Validation versionValidation : version.getValidations()) {
            if (!versionValidation.isValid() && versionValidation.getMessage() != null) {
                Map<String, String> message = g.fromJson(versionValidation.getMessage(), HashMap.class);
                for (Map.Entry<String, String> entry : message.entrySet()) {
                    result.append(entry.getKey() + ": " + entry.getValue() + " ");
                }
            }
        }

        return result.toString();
    }

    /**
     * Checks if the given version is valid based on existing version validations
     * @param version Version to validate
     * @return True if valid version, false otherwise
     */
    protected abstract boolean isValidVersion(U version);

    protected abstract void populateMetadata(Set<SourceFile> sourceFiles, T entry, U version);

    /**
     * Will update the version with validation information
     * Note: There is one validation entry for each sourcefile type. This is true for test parameter files too.
     * @param version Version to validate
     * @param entry Entry for the version
     * @param mainDescriptor the main descriptor if different than the default /Dockstore.wdl or /Dockstore.cwl
     * @return Version with updated validation information
     */
    protected abstract U versionValidation(U version, T entry, Optional<SourceFile> mainDescriptor);

    protected void checkHosted(T entry) {
        if (entry instanceof Tool) {
            if (((Tool)entry).getMode() != ToolMode.HOSTED) {
                throw new CustomWebApplicationException("cannot modify non-hosted entries this way", HttpStatus.SC_BAD_REQUEST);
            }
        } else if (entry instanceof Workflow) {
            if (((Workflow)entry).getMode() != WorkflowMode.HOSTED) {
                throw new CustomWebApplicationException("cannot modify non-hosted entries this way", HttpStatus.SC_BAD_REQUEST);
            }
        }
    }

    /**
     * Check that the descriptor type is a valid type and return the descriptor type object. Not required for tools, since a tool has many types.
     * @param descriptorType
     * @return Verified type
     */
    protected abstract DescriptorLanguage checkType(String descriptorType);

    /**
     * Check that the registry is a valid registry and return the registry object
     * @param registry
     * @return
     */
    protected abstract Registry checkRegistry(String registry);

    /**
     * Create new version of a workflow or tag of a tool
     * @param entry the parent for the new version
     * @return the new version
     */
    protected abstract U getVersion(T entry);

    @DELETE
    @Path("/hostedEntry/{entryId}")
    @Timed
    @UnitOfWork
    public T deleteHostedVersion(@ApiParam(hidden = true) @Auth User user,
        @ApiParam(value = "Entry to modify.", required = true) @PathParam("entryId") Long entryId,
        @ApiParam(value = "version", required = true) @QueryParam("version") String version) {
        T entry = getEntryDAO().findById(entryId);
        checkEntry(entry);
        checkUserCanUpdate(user, entry);
        checkHosted(entry);
        entry.getVersions().removeIf(v -> Objects.equals(v.getName(), version));
        elasticManager.handleIndexUpdate(entry, ElasticMode.UPDATE);
        return entry;
    }

    private Set<SourceFile> handleSourceFileMerger(Long entryId, Set<SourceFile> sourceFiles, T entry, U tag) {
        Set<U> versions = entry.getVersions();
        Map<String, SourceFile> map = new HashMap<>();
        tag.setName(calculateNextVersionName(versions));

        if (versions.size() > 0) {
            // get the last one and modify files accordingly
            U versionWithTheLargestName = versionWithLargestName(versions);
            // carry over old files
            versionWithTheLargestName.getSourceFiles().forEach(v -> {
                SourceFile newfile = new SourceFile();
                newfile.setPath(v.getPath());
                newfile.setAbsolutePath(v.getAbsolutePath());
                newfile.setContent(v.getContent());
                newfile.setType(v.getType());
                map.put(newfile.getPath(), newfile);
            });

            boolean changed = false;
            // mutate sourcefiles accordingly
            // 1) matching filenames are updated with the new content
            // 2) empty files are deleted
            // 3) new files are created
            for (SourceFile file : sourceFiles) {
                // ignore IDs if they were populated
                file.setId(0);

                if (map.containsKey(file.getPath())) {
                    if (file.getContent() != null) {
                        // case 1)
                        final SourceFile sourceFile = map.get(file.getPath());
                        if (!sourceFile.getContent().equals(file.getContent())) {
                            sourceFile.setContent(file.getContent());
                            changed = true;
                        }
                    } else {
                        // case 2)
                        map.remove(file.getPath());
                        LOG.info("deleted " + file.getPath() + " for new revision of " + entryId);
                        changed = true;
                    }
                } else {
                    // case 3
                    map.put(file.getPath(), file);
                    changed = true;
                }
            }

            if (!changed) {
                LOG.info("aborting change, there were no differences detected for new revision of " + entryId);
                throw new CustomWebApplicationException("no changes detected", HttpStatus.SC_NO_CONTENT);
            }
        } else {
            // for brand new hosted tools
            sourceFiles.forEach(f -> map.put(f.getPath(), f));
        }
        persistSourceFiles(tag, map.values());

        return tag.getSourceFiles();
    }

    void persistSourceFiles(U tag, Collection<SourceFile> sourceFiles) {
        // create everything still in the map
        for (SourceFile e : sourceFiles) {
            long l = fileDAO.create(e);
            tag.getSourceFiles().add(fileDAO.findById(l));
        }
    }

    /**
     * Calculates the next version name. Currently assumes versions are always stringified numbers, and returns
     * the highest number + 1 as a string. Need to update when we support arbitrary names for the version.
     * @param versions
     * @return
     */
    String calculateNextVersionName(Set<U> versions) {
        if (versions.isEmpty()) {
            return "1";
        } else {
            U versionWithTheLargestName = versionWithLargestName(versions);
            return (String.valueOf(Integer.parseInt(versionWithTheLargestName.getName()) + 1));

        }
    }

    private U versionWithLargestName(Set<U> versions) {
        Comparator<Version> comp = Comparator.comparingInt(p -> Integer.parseInt(p.getName()));
        // there should always be a max with size() > 0
        return versions.stream().max(comp).orElseThrow(RuntimeException::new);
    }

}
