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

package io.github.collaboratory.wdl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.commons.validator.routines.UrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by aduncan on 19/10/16.
 */
public class BridgeHelper {
    private static final Logger LOG = LoggerFactory.getLogger(BridgeHelper.class);

    /**
     * This resolves a URL into file content
     *
     * @param importUrl
     * @return content of file
     */
    public String resolveUrl(String importUrl) {
        StringBuilder content = new StringBuilder();

        // Check if valid URL
        UrlValidator urlValidator = new UrlValidator();
        if (urlValidator.isValid(importUrl)) {
            // Check that url is from GitHub, Bitbucket, or GitLab
            if (importUrl.startsWith("https://raw.githubusercontent.com/") || importUrl.startsWith("https://bitbucket.org") || importUrl
                    .startsWith("https://gitlab.com")) {
                // Grab file located at URL
                try {
                    try (InputStream inputStream = new URL(importUrl).openStream()) {
                        InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            content.append(line);
                        }
                    }
                } catch (MalformedURLException ex) {
                    LOG.debug("Invalid URL: " + importUrl);
                } catch (IOException ex) {
                    LOG.debug("Error parsing contents of " + importUrl);
                }
            } else {
                LOG.debug("Only files from Github, GitLab and Bitbucket are supported for HTTP/HTTPS.");
            }
        } else {
            LOG.debug("Invalid URL: " + importUrl);
        }
        return content.toString();
    }

    /**
     * Resolves local imports
     *
     * @param importPath
     * @param secondaryFileDesc
     * @return content of local import
     */
    public String resolveSecondaryPath(String importPath, Map<String, String> secondaryFileDesc) {
        String content = "";

        // Remove file:// from import path
        importPath = importPath.replaceFirst("file://", "");

        // Check if local path has been imported
        if (secondaryFileDesc.get(importPath) != null) {
            return secondaryFileDesc.get(importPath);
        }
        return content;
    }

    /**
     * Resolves local imports (when files exist locally)
     *
     * @param basePath
     * @param importPath
     * @return content of local import
     */
    public String resolveLocalPath(String basePath, String importPath) {
        String content = "";

        // Remove file:// from import path
        importPath = importPath.replaceFirst("file://", "");

        // Get content of importPath
        try {
            if (basePath != null) {
                content = Files.asCharSource(new File(basePath, importPath), Charsets.UTF_8).read();
            } else {
                content = Files.asCharSource(new File(importPath), Charsets.UTF_8).read();
            }
        } catch (IOException ex) {
            LOG.debug("Invalid filepath: " + importPath);
        }

        return content;
    }
}
