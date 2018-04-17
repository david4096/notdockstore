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

import java.util.List;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import io.dockstore.common.CommonTestUtilities;
import io.swagger.client.model.MetadataV1;
import io.swagger.client.model.ToolClass;
import io.swagger.client.model.ToolV1;
import io.swagger.client.model.ToolVersionV1;
import io.swagger.model.ToolDockerfile;
import org.junit.Test;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author gluu
 * @since 19/04/17
 */
public class GA4GHV1IT extends GA4GHIT {
    private static final String apiVersion = "api/ga4gh/v1/";

    public String getApiVersion() {
        return apiVersion;
    }

    @Test
    public void metadata() throws Exception {
        Response response = checkedResponse(basePath + "metadata");
        MetadataV1 metadata = response.readEntity(MetadataV1.class);
        assertThat(SUPPORT.getObjectMapper().writeValueAsString(metadata)).contains("api-version");
        assertThat(SUPPORT.getObjectMapper().writeValueAsString(metadata)).contains("friendly-name");
        assertThat(SUPPORT.getObjectMapper().writeValueAsString(metadata)).doesNotContain("api_version");
        assertThat(SUPPORT.getObjectMapper().writeValueAsString(metadata)).doesNotContain("friendly_name");
    }

    @Test
    public void tools() throws Exception {
        Response response = checkedResponse(basePath + "tools");
        List<ToolV1> responseObject = response.readEntity(new GenericType<List<ToolV1>>() {
        });
        assertTool(SUPPORT.getObjectMapper().writeValueAsString(responseObject), true);
    }

    @Test
    public void toolsId() throws Exception {
        toolsIdTool();
        toolsIdWorkflow();
    }

    private void toolsIdTool() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6");
        ToolV1 responseObject = response.readEntity(ToolV1.class);
        assertTool(SUPPORT.getObjectMapper().writeValueAsString(responseObject), true);
    }

    private void toolsIdWorkflow() throws Exception {
        Response response = checkedResponse(basePath + "tools/%23workflow%2Fgithub.com%2FA%2Fl");
        ToolV1 responseObject = response.readEntity(ToolV1.class);
        assertTool(SUPPORT.getObjectMapper().writeValueAsString(responseObject), false);
    }

    @Test
    public void toolsIdVersions() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions");
        List<ToolVersionV1> responseObject = response.readEntity(new GenericType<List<ToolVersionV1>>() {
        });
        assertVersion(SUPPORT.getObjectMapper().writeValueAsString(responseObject));
    }

    @Test
    public void toolClasses() throws Exception {
        Response response = checkedResponse(basePath + "tool-classes");
        List<ToolClass> responseObject = response.readEntity(new GenericType<List<ToolClass>>() {
        });
        final String expected = SUPPORT.getObjectMapper()
                .writeValueAsString(SUPPORT.getObjectMapper().readValue(fixture("fixtures/toolClasses.json"), new TypeReference<List<ToolClass>>() {
                }));
        assertThat(SUPPORT.getObjectMapper().writeValueAsString(responseObject)).isEqualTo(expected);
    }

    @Test
    public void toolsIdVersionsVersionId() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName");
        ToolVersionV1 responseObject = response.readEntity(ToolVersionV1.class);
        assertVersion(SUPPORT.getObjectMapper().writeValueAsString(responseObject));
    }

    @Override
    public void toolsIdVersionsVersionIdTypeDockerfile() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/dockerfile");
        ToolDockerfile responseObject = response.readEntity(ToolDockerfile.class);
        assertThat(SUPPORT.getObjectMapper().writeValueAsString(responseObject).contains("dockerfile"));
    }

    protected void assertVersion(String version) {
        assertThat(version).contains("meta-version");
        assertThat(version).contains("descriptor-type");
        assertThat(version).contains("verified-source");
        assertThat(version).doesNotContain("meta_version");
        assertThat(version).doesNotContain("descriptor_type");
        assertThat(version).doesNotContain("verified_source");
    }

    protected void assertTool(String tool, boolean isTool) {
        assertThat(tool).contains("meta-version");
        assertThat(tool).contains("verified-source");
        assertThat(tool).doesNotContain("meta_version");
        assertThat(tool).doesNotContain("verified_source");
        if (isTool) {
            assertVersion(tool);
        }
    }

    /**
     * This tests if the 4 workflows with a combination of different repositories and either same or matching workflow name
     * can be retrieved separately
     *
     * @throws Exception
     */
    @Test
    public void toolsIdGet4Workflows() throws Exception {
        // Insert the 4 workflows into the database using migrations
        CommonTestUtilities.setupSamePathsTest(SUPPORT);
        Response response2 = checkedResponse(basePath + "tools");
        List<ToolV1> responseObject2 = response2.readEntity(new GenericType<List<ToolV1>>() {
        });
        // Check responses
        Response response = checkedResponse(basePath + "tools/%23workflow%2Fgithub.com%2FfakeOrganization%2FfakeRepository");
        ToolV1 responseObject = response.readEntity(ToolV1.class);
        assertThat(SUPPORT.getObjectMapper().writeValueAsString(responseObject)).contains("author1");
        response = checkedResponse(basePath + "tools/%23workflow%2Fbitbucket.org%2FfakeOrganization%2FfakeRepository");
        responseObject = response.readEntity(ToolV1.class);
        assertThat(SUPPORT.getObjectMapper().writeValueAsString(responseObject)).contains("author2");
        response = checkedResponse(basePath + "tools/%23workflow%2Fgithub.com%2FfakeOrganization%2FfakeRepository%2FPotato");
        responseObject = response.readEntity(ToolV1.class);
        assertThat(SUPPORT.getObjectMapper().writeValueAsString(responseObject)).contains("author3");
        response = checkedResponse(basePath + "tools/%23workflow%2Fbitbucket.org%2FfakeOrganization%2FfakeRepository%2FPotato");
        responseObject = response.readEntity(ToolV1.class);
        assertThat(SUPPORT.getObjectMapper().writeValueAsString(responseObject)).contains("author4");
    }
}
