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

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.ElasticManager;
import io.dockstore.webservice.helpers.ElasticMode;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.VersionDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.dropwizard.jersey.PATCH;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Methods to create and edit hosted tool and workflows.
 * Reuse existing methods to GET them, add labels to them, and other operations.
 *
 * @author dyuen
 */
@Api("hosted")
public abstract class AbstractHostedEntryResource<T extends Entry<T, U>, U extends Version<U>, W extends EntryDAO<T>, X extends VersionDAO<U>> implements AuthenticatedResourceInterface {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractHostedEntryResource.class);
    final ElasticManager elasticManager;
    private final FileDAO fileDAO;
    private final UserDAO userDAO;

    AbstractHostedEntryResource(FileDAO fileDAO, UserDAO userDAO) {
        this.fileDAO = fileDAO;
        this.elasticManager = new ElasticManager();
        this.userDAO = userDAO;
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
        @ApiParam(value = "For tools, the Docker registry") @QueryParam("registry") String registry,
        @ApiParam(value = "name", required = true) @QueryParam("name") String name,
        @ApiParam(value = "Descriptor type", required = true) @QueryParam("descriptorType") String descriptorType) {
        T entry = getEntry(user, registry, name, descriptorType);
        long l = getEntryDAO().create(entry);
        T byId = getEntryDAO().findById(l);
        elasticManager.handleIndexUpdate(byId, ElasticMode.UPDATE);
        return byId;
    }

    protected abstract T getEntry(User user, String registry, String name, String descriptorType);

    @PATCH
    @Path("/hostedEntry/{entryId}")
    @Timed
    @UnitOfWork
    public T editHosted(@ApiParam(hidden = true) @Auth User user,
        @ApiParam(value = "Entry to modify.", required = true) @PathParam("entryId") Long entryId,
        @ApiParam(value = "Set of updated sourcefiles, add files by adding new files with unknown paths, delete files by including them with emptied content", required = true) Set<SourceFile> sourceFiles) {
        T entry = getEntryDAO().findById(entryId);
        checkEntry(entry);
        checkUser(user, entry);
        // TODO: may need to revisit some of these arbitrary decisions
        U version = getVersion(entry);
        handleSourceFileMerger(entryId, sourceFiles, entry, version);
        long l = getVersionDAO().create(version);
        entry.getVersions().add(getVersionDAO().findById(l));
        userDAO.clearCache();
        T newTool = getEntryDAO().findById(entryId);
        elasticManager.handleIndexUpdate(newTool, ElasticMode.UPDATE);
        return newTool;
    }

    protected abstract U getVersion(T entry);

    @DELETE
    @Path("/hostedEntry/{entryId}")
    @Timed
    @UnitOfWork
    public T deleteHostedVersion(@ApiParam(hidden = true) @Auth User user,
        @ApiParam(value = "Entry to modify.", required = true) @PathParam("entryId") Long entryId,
        @ApiParam(value = "version", required = true) @QueryParam("version") String version) {
        T entryById = getEntryDAO().findById(entryId);
        checkEntry(entryById);
        checkUser(user, entryById);
        entryById.getVersions().removeIf(v -> Objects.equals(v.getName(), version));
        return entryById;
    }

    private void handleSourceFileMerger(Long entryId, Set<SourceFile> sourceFiles, T entry, U tag) {
        Set<U> versions = entry.getVersions();
        Map<String, SourceFile> map = new HashMap<>();

        if (versions.size() > 0) {
            // get the last one and modify files accordingly
            Comparator<Version> comp = Comparator.comparingInt(p -> Integer.valueOf(p.getName()));
            // there should always be a max with size() > 0
            U versionWithTheLargestName = versions.stream().max(comp).orElseThrow(RuntimeException::new);
            tag.setName(String.valueOf(Integer.parseInt(versionWithTheLargestName.getName()) + 1));
            // carry over old files
            versionWithTheLargestName.getSourceFiles().forEach(v -> {
                SourceFile newfile = new SourceFile();
                newfile.setPath(v.getPath());
                newfile.setContent(v.getContent());
                newfile.setType(v.getType());
                map.put(newfile.getPath(), newfile);
            });

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
                        map.get(file.getPath()).setContent(file.getContent());
                    } else {
                        // case 3)
                        map.remove(file.getPath());
                        LOG.info("deleted " + file.getPath() + " for new revision of " + entryId);
                    }
                } else {
                    map.put(file.getPath(), file);
                }
            }
        } else {
            // for brand new hosted tools
            tag.setName("0");
            sourceFiles.forEach(f -> map.put(f.getPath(), f));
        }

        // create everything still in the map
        for (SourceFile e : map.values()) {
            long l = fileDAO.create(e);
            tag.getSourceFiles().add(fileDAO.findById(l));
        }
    }
}
