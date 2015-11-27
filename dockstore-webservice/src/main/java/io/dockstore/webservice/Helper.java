/*
 * Copyright (C) 2015 Collaboratory
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.dockstore.webservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.gson.Gson;
import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.SourceFile.FileType;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.ImageRegistryFactory;
import io.dockstore.webservice.helpers.ImageRegistryInterface;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface.FileResponse;
import io.dockstore.webservice.jdbi.ContainerDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.resources.ResourceUtilities;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *
 * @author xliu
 */
public class Helper {
    private static final Logger LOG = LoggerFactory.getLogger(Helper.class);

    private static final String BITBUCKET_URL = "https://bitbucket.org/";

    public static final String DOCKSTORE_CWL = "Dockstore.cwl";
    private static final String DOCKERFILE = "Dockerfile";

    public static class RepoList {

        private List<Container> repositories;

        public void setRepositories(List<Container> repositories) {
            this.repositories = repositories;
        }

        public List<Container> getRepositories() {
            return this.repositories;
        }
    }

    private static void updateTags(Container container, TagDAO tagDAO, FileDAO fileDAO, Map<String, List<Tag>> tagMap) {
        List<Tag> existingTags = new ArrayList(container.getTags());
        List<Tag> newTags = tagMap.get(container.getPath());

        List<Tag> toDelete = new ArrayList<>(0);
        for (Iterator<Tag> iterator = existingTags.iterator(); iterator.hasNext();) {
            Tag oldContainer = iterator.next();
            boolean exists = false;
            for (Tag newContainer : newTags) {
                if (newContainer.getName().equals(oldContainer.getName())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                toDelete.add(oldContainer);
                iterator.remove();
            }
        }

        if (newTags != null) {
            for (Tag tag : newTags) {
                for (SourceFile file : tag.getSourceFiles()) {
                    fileDAO.create(file);
                }

                long tagId = tagDAO.create(tag);
                tag = tagDAO.findById(tagId);
                container.addTag(tag);
            }
        }
        LOG.info("UPDATED Container: " + container.getPath());
    }

    /**
     * Updates the new list of containers to the database. Deletes containers that has no users.
     * 
     * @param newList
     * @param currentList
     * @param user
     * @param containerDAO
     * @param tagDAO
     * @param fileDAO
     * @param tagMap
     * @return list of newly updated containers
     */
    private static List<Container> updateContainers(List<Container> newList, List<Container> currentList, User user,
            ContainerDAO containerDAO, TagDAO tagDAO, FileDAO fileDAO, Map<String, List<Tag>> tagMap) {
        Date time = new Date();

        List<Container> toDelete = new ArrayList<>(0);
        // Find containers that the user no longer has
        for (Iterator<Container> iterator = currentList.iterator(); iterator.hasNext();) {
            Container oldContainer = iterator.next();
            boolean exists = false;
            for (Container newContainer : newList) {
                if (newContainer.getName().equals(oldContainer.getName())
                        && newContainer.getNamespace().equals(oldContainer.getNamespace())
                        && newContainer.getRegistry().equals(oldContainer.getRegistry())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                oldContainer.removeUser(user);
                // user.removeContainer(oldContainer);
                toDelete.add(oldContainer);
                iterator.remove();
            }
        }

        for (Container newContainer : newList) {
            String path = newContainer.getRegistry() + "/" + newContainer.getNamespace() + "/" + newContainer.getName();
            boolean exists = false;

            // Find if user already has the container
            for (Container oldContainer : currentList) {
                if (newContainer.getPath().equals(oldContainer.getPath())) {
                    exists = true;

                    oldContainer.update(newContainer);

                    break;
                }
            }

            // Find if container already exists, but does not belong to user
            if (!exists) {
                Container oldContainer = containerDAO.findByPath(path);
                if (oldContainer != null) {
                    exists = true;
                    oldContainer.update(newContainer);
                    currentList.add(oldContainer);
                }
            }

            // Container does not already exist
            if (!exists) {
                // newContainer.setUserId(userId);
                newContainer.setPath(path);

                currentList.add(newContainer);
            }
        }

        // Save all new and existing containers, and generate new tags
        for (Container container : currentList) {
            container.setLastUpdated(time);
            container.addUser(user);
            containerDAO.create(container);

            container.getTags().clear();

            List<Tag> tags = tagMap.get(container.getPath());
            if (tags != null) {
                for (Tag tag : tags) {
                    for (SourceFile file : tag.getSourceFiles()) {
                        fileDAO.create(file);
                    }

                    long tagId = tagDAO.create(tag);
                    tag = tagDAO.findById(tagId);
                    container.addTag(tag);
                }
            }
            LOG.info("UPDATED Container: " + container.getPath());
        }

        // delete container if it has no users
        for (Container c : toDelete) {
            LOG.info(c.getPath() + " " + c.getUsers().size());

            if (c.getUsers().isEmpty()) {
                LOG.info("DELETING: " + c.getPath());
                c.getTags().clear();
                containerDAO.delete(c);
            }
        }

        return currentList;
    }

    /**
     * Get the list of tags for each container from Quay.io.
     * 
     * @param client
     * @param containers
     * @param objectMapper
     * @param quayToken
     * @param bitbucketToken
     * @param githubToken
     * @param mapOfBuilds
     * @return a map: key = path; value = list of tags
     */
    @SuppressWarnings("checkstyle:parameternumber")
    private static Map<String, List<Tag>> getTags(final HttpClient client, final List<Container> containers, final ObjectMapper objectMapper,
            final Token quayToken, final Token bitbucketToken, final Token githubToken,
            final Map<String, ArrayList<?>> mapOfBuilds) {
        final Map<String, List<Tag>> tagMap = new HashMap<>();

        ImageRegistryFactory factory = new ImageRegistryFactory(client, objectMapper, quayToken);

        for (Container c : containers) {

            final ImageRegistryInterface imageRegistry = factory.createImageRegistry(c.getRegistry());
            final List<Tag> tags = imageRegistry.getTags(c);

            // TODO: this part isn't very good, a true implementation of Docker Hub would need to return
            // a quay.io-like data structure, we need to replace mapOfBuilds
            List builds = mapOfBuilds.get(c.getPath());

            if (builds != null && !builds.isEmpty()) {
                for (Tag tag : tags) {
                    LOG.info("TAG: " + tag.getName());

                    for (final Object build : builds) {
                        Map<String, String> idMap = (Map<String, String>) build;
                        String buildId = idMap.get("id");

                        LOG.info("Build ID: {}", buildId);

                        Map<String, ArrayList<String>> tagsMap = (Map<String, ArrayList<String>>) build;

                        List<String> buildTags = tagsMap.get("tags");

                        if (buildTags.contains(tag.getName())) {
                            LOG.info("Build found with tag: {}", tag.getName());

                            Map<String, Map<String, String>> triggerMetadataMap = (Map<String, Map<String, String>>) build;

                            String ref = triggerMetadataMap.get("trigger_metadata").get("ref");
                            LOG.info("REFERENCE: {}", ref);
                            tag.setReference(ref);

                            FileResponse cwlResponse = readGitRepositoryFile(c, DOCKSTORE_CWL, client, tag, bitbucketToken, githubToken);
                            if (cwlResponse != null) {
                                SourceFile dockstoreCwl = new SourceFile();
                                dockstoreCwl.setType(FileType.DOCKSTORE_CWL);
                                dockstoreCwl.setContent(cwlResponse.getContent());
                                tag.addSourceFile(dockstoreCwl);
                            }

                            FileResponse dockerfileResponse = readGitRepositoryFile(c, DOCKERFILE, client, tag, bitbucketToken, githubToken);
                            if (dockerfileResponse != null) {
                                SourceFile dockerfile = new SourceFile();
                                dockerfile.setType(FileType.DOCKERFILE);
                                dockerfile.setContent(dockerfileResponse.getContent());
                                tag.addSourceFile(dockerfile);
                            }

                            break;
                        }
                    }
                }
            }

            tagMap.put(c.getPath(), tags);
        }

        return tagMap;
    }


    /**
     * Refreshes user's containers
     * 
     * @param userId
     * @param client
     * @param objectMapper
     * @param userDAO
     * @param containerDAO
     * @param tokenDAO
     * @param tagDAO
     * @param fileDAO
     * @return list of updated containers
     */
    @SuppressWarnings("checkstyle:parameternumber")
    public static List<Container> refresh(Long userId, HttpClient client, ObjectMapper objectMapper, UserDAO userDAO,
            ContainerDAO containerDAO, TokenDAO tokenDAO, TagDAO tagDAO, FileDAO fileDAO) {
        User dockstoreUser = userDAO.findById(userId);

        List<Container> currentRepos = new ArrayList(dockstoreUser.getContainers());// containerDAO.findByUserId(userId);
        List<Token> tokens = tokenDAO.findByUserId(userId);

        Token quayToken = null;
        Token githubToken = null;
        Token bitbucketToken = null;

        // Get user's quay and git tokens
        for (Token token : tokens) {
            if (token.getTokenSource().equals(TokenType.QUAY_IO.toString())) {
                quayToken = token;
            }
            if (token.getTokenSource().equals(TokenType.GITHUB_COM.toString())) {
                githubToken = token;
            }
            if (token.getTokenSource().equals(TokenType.BITBUCKET_ORG.toString())) {
                bitbucketToken = token;
            }
        }

        if (githubToken == null || quayToken == null) {
            LOG.info("GIT or QUAY token not found!");
            throw new WebApplicationException(HttpStatus.SC_CONFLICT);
        }
        if (bitbucketToken == null) {
            LOG.info("WARNING: BITBUCKET token not found!");
        }

        ImageRegistryFactory factory = new ImageRegistryFactory(client, objectMapper, quayToken);
        final List<ImageRegistryInterface> allRegistries = factory.getAllRegistries();

        List<String> namespaces = new ArrayList<>();
        // TODO: figure out better approach, for now just smash together stuff from DockerHub and quay.io
        for(ImageRegistryInterface i: allRegistries){
            namespaces.addAll(i.getNamespaces());
        }

        List<Container> allRepos = new ArrayList<>();
        for(ImageRegistryInterface i: allRegistries){
            allRepos.addAll(i.getContainers(namespaces));
        }

        Map<String, ArrayList<?>> mapOfBuilds = new HashMap<>();
        for(ImageRegistryInterface i: allRegistries) {
            mapOfBuilds.putAll(i.getBuildMap(githubToken, bitbucketToken, allRepos));
        }

        Map<String, List<Tag>> tagMap = getTags(client, allRepos, objectMapper, quayToken, bitbucketToken, githubToken, mapOfBuilds);

        updateContainers(allRepos, currentRepos, dockstoreUser, containerDAO, tagDAO, fileDAO, tagMap);
        userDAO.clearCache();
        return new ArrayList(userDAO.findById(userId).getContainers());
    }


    /**
     * Read a file from the container's git repository.
     * 
     * @param container
     * @param fileName
     * @param client
     * @param tag
     * @param bitbucketToken
     * @return a FileResponse instance
     */
    public static FileResponse readGitRepositoryFile(Container container, String fileName, HttpClient client, Tag tag,
                                                        Token bitbucketToken, Token githubToken) {
        final String bitbucketTokenContent = bitbucketToken == null ? null : bitbucketToken.getContent();

        if (container.getGitUrl() == null || container.getGitUrl().isEmpty()) {
            return null;
        }
        final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory.createSourceCodeRepo(container.getGitUrl(), client, bitbucketTokenContent,
            githubToken.getContent());

        final String reference = sourceCodeRepo.getReference(container.getGitUrl(), tag.getReference());

        return sourceCodeRepo.readFile(fileName, reference);
    }

    /**
     * Refreshes user's Bitbucket token.
     * 
     * @param token
     * @param client
     * @param tokenDAO
     * @param bitbucketClientID
     * @param bitbucketClientSecret
     * @return the updated token
     */
    public static Token refreshBitbucketToken(Token token, HttpClient client, TokenDAO tokenDAO, String bitbucketClientID,
            String bitbucketClientSecret) {

        String url = BITBUCKET_URL + "site/oauth2/access_token";

        try {
            Optional<String> asString = ResourceUtilities.bitbucketPost(url, null, client, bitbucketClientID, bitbucketClientSecret,
                    "grant_type=refresh_token&refresh_token=" + token.getRefreshToken());

            String accessToken;
            String refreshToken;
            if (asString.isPresent()) {
                LOG.info("RESOURCE CALL: " + url);
                String json = asString.get();

                Gson gson = new Gson();
                Map<String, String> map = new HashMap<>();
                map = (Map<String, String>) gson.fromJson(json, map.getClass());

                accessToken = map.get("access_token");
                refreshToken = map.get("refresh_token");

                token.setContent(accessToken);
                token.setRefreshToken(refreshToken);

                long create = tokenDAO.create(token);
                return tokenDAO.findById(create);
            } else {
                throw new WebApplicationException("Could not retrieve bitbucket.org token based on code");
            }
        } catch (UnsupportedEncodingException ex) {
            LOG.info(ex.toString());
            throw new WebApplicationException(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Check if admin
     * 
     * @param user
     */
    public static void checkUser(User user) {
        if (!user.getIsAdmin()) {
            throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
        }
    }

    /**
     * Check if admin or correct user
     * 
     * @param user
     * @param id
     */
    public static void checkUser(User user, long id) {
        if (!user.getIsAdmin() && user.getId() != id) {
            throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
        }
    }

    /**
     * Check if admin or if container belongs to user
     * 
     * @param user
     * @param container
     */
    public static void checkUser(User user, Container container) {
        if (!user.getIsAdmin() && !container.getUsers().contains(user)) {
            throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
        }
    }

    /**
     * Check if container is null
     * 
     * @param container
     */
    public static void checkContainer(Container container) {
        if (container == null) {
            throw new WebApplicationException(HttpStatus.SC_BAD_REQUEST);
        }
    }
}
