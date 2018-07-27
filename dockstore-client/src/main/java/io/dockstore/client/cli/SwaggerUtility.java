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
package io.dockstore.client.cli;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

import javax.ws.rs.core.GenericType;

import com.google.gson.Gson;
import io.swagger.client.ApiClient;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.StarRequest;
import io.swagger.client.model.VerifyRequest;
import org.apache.commons.io.FileUtils;

public final class SwaggerUtility {

    private SwaggerUtility() {

    }

    public static <T> T getArbitraryURL(String url, GenericType<T> type, ApiClient client) {
        return client
            .invokeAPI(url, "GET", new ArrayList<>(), null, new HashMap<>(), new HashMap<>(), "application/zip", "application/zip",
                new String[] { "BEARER" }, type);
    }

    public static void unzipFile(File zipFile) throws IOException {
        ZipFile zipFileActual = new ZipFile(zipFile);
        zipFileActual.stream().forEach(zipEntry -> {
            String fileName = zipEntry.getName();
            File newFile = new File(System.getProperty("user.dir"), fileName);
            try {
                newFile.getParentFile().mkdirs();
                FileUtils.copyInputStreamToFile(zipFileActual.getInputStream(zipEntry), newFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        FileUtils.deleteQuietly(zipFile);
    }

    /**
     * These serialization/deserialization hacks should not be necessary.
     * Why does this version of codegen remove the setters?
     * @param bool
     * @return
     */
    public static PublishRequest createPublishRequest(Boolean bool) {
        Map<String, Object> publishRequest = new HashMap<>();
        publishRequest.put("publish", bool);
        Gson gson = new Gson();
        String s = gson.toJson(publishRequest);
        return gson.fromJson(s, PublishRequest.class);
    }

    public static VerifyRequest createVerifyRequest(Boolean bool, String verifiedSource) {
        Map<String, Object> verifyRequest = new HashMap<>();
        verifyRequest.put("verify", bool);
        verifyRequest.put("verifiedSource", verifiedSource);
        Gson gson = new Gson();
        String s = gson.toJson(verifyRequest);
        return gson.fromJson(s, VerifyRequest.class);
    }

    public static StarRequest createStarRequest(Boolean bool) {
        Map<String, Object> starRequest = new HashMap<>();
        starRequest.put("star", bool);
        Gson gson = new Gson();
        String s = gson.toJson(starRequest);
        return gson.fromJson(s, StarRequest.class);
    }

}
