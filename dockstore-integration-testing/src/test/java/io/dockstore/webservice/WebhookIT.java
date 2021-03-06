/*
 *
 *    Copyright 2020 OICR
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
 *
 */

package io.dockstore.webservice;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.common.collect.Lists;
import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BasicIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.api.LambdaEventsApi;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.LambdaEvent;
import io.swagger.client.model.Validation;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

import static io.dockstore.client.cli.WorkflowIT.DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME;
import static io.dockstore.webservice.Constants.DOCKSTORE_YML_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author agduncan
 */
@Category(ConfidentialTest.class)
public class WebhookIT extends BaseIT {
    private static final int LAMBDA_ERROR = 418;

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private Session session;

    private final String workflowRepo = "DockstoreTestUser2/workflow-dockstore-yml";
    private final String installationId = "1179416";

    @Before
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();

        // non-confidential test database sequences seem messed up and need to be iterated past, but other tests may depend on ids
        testingPostgres.runUpdateStatement("alter sequence enduser_id_seq increment by 50 restart with 100");
        testingPostgres.runUpdateStatement("alter sequence token_id_seq increment by 50 restart with 100");

        // used to allow us to use tokenDAO outside of the web service
        this.session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @Test
    public void testWorkflowMigration() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);
        Workflow workflow = workflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), workflowRepo, "/Dockstore.wdl",
                "foobar", "wdl", "/test.json");
        workflowApi.manualRegister(SourceControl.GITHUB.name(), DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME, "/Dockstore.cwl", "",
                DescriptorLanguage.CWL.getLowerShortName(), "/test.json");
        
        // Refresh should work
        workflow = workflowApi.refresh(workflow.getId());
        assertEquals("Workflow should be FULL mode", Workflow.ModeEnum.FULL, workflow.getMode());
        assertTrue("All versions should be legacy", workflow.getWorkflowVersions().stream().allMatch(WorkflowVersion::isLegacyVersion));

        // Webhook call should convert workflow to DOCKSTORE_YML
        workflowApi.
                handleGitHubRelease(workflowRepo, "DockstoreTestUser2", "refs/tags/0.1", installationId);
        workflow = workflowApi.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", "", false);
        assertEquals("Workflow should be DOCKSTORE_YML mode", Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode());
        assertTrue("One version should be not legacy", workflow.getWorkflowVersions().stream().anyMatch(workflowVersion -> !workflowVersion.isLegacyVersion()));

        // Refresh should now no longer work
        try {
            workflowApi.refresh(workflow.getId());
            fail("Should fail on refresh and not reach this point");
        } catch (ApiException ex) {
            assertEquals("Should not be able to refresh a dockstore.yml workflow.", HttpStatus.SC_BAD_REQUEST, ex.getCode());
        }

        // Should be able to refresh a legacy version
        workflow = workflowApi.refreshVersion(workflow.getId(), "0.2");

        // Should not be able to refresh a GitHub App version
        try {
            workflowApi.refreshVersion(workflow.getId(), "0.1");
            fail("Should not be able to refresh");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode());
        }

        // Refresh a version that doesn't already exist
        try {
            workflowApi.refreshVersion(workflow.getId(), "dne");
            fail("Should not be able to refresh");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode());
        }

        List<Workflow> workflows = usersApi.addUserToDockstoreWorkflows(usersApi.getUser().getId(), "");
        assertTrue("There should still be a dockstore.yml workflow", workflows.stream().anyMatch(wf -> Objects.equals(wf.getMode(), Workflow.ModeEnum.DOCKSTORE_YML)));
        assertTrue("There should be at least one stub workflow", workflows.stream().anyMatch(wf -> Objects.equals(wf.getMode(), Workflow.ModeEnum.STUB)));

        // Test that refreshing a frozen version doesn't update the version
        testingPostgres.runUpdateStatement("UPDATE workflowversion SET commitid = NULL where name = '0.2'");

        // Refresh before frozen should populate the commit id
        workflow = workflowApi.refreshVersion(workflow.getId(), "0.2");
        WorkflowVersion workflowVersion = workflow.getWorkflowVersions().stream().filter(wv -> Objects.equals(wv.getName(), "0.2")).findFirst().get();
        assertNotNull(workflowVersion.getCommitID());

        // Refresh after freezing should not update
        testingPostgres.runUpdateStatement("UPDATE workflowversion SET commitid = NULL where name = '0.2'");

        // Freeze legacy version
        workflowVersion.setFrozen(true);
        List<WorkflowVersion> workflowVersions = workflowApi
                 .updateWorkflowVersion(workflow.getId(), Lists.newArrayList(workflowVersion));
        workflowVersion = workflowVersions.stream().filter(v -> v.getName().equals("0.2")).findFirst().get();
        assertTrue(workflowVersion.isFrozen());

        // Ensure refresh does not touch frozen legacy version
        workflow = workflowApi.refreshVersion(workflow.getId(), "0.2");
        assertNotNull(workflow);
        workflowVersion = workflow.getWorkflowVersions().stream().filter(wv -> Objects.equals(wv.getName(), "0.2")).findFirst().get();
        assertNull(workflowVersion.getCommitID());
    }

    /**
     * This tests the GitHub release process
     */
    @Test
    public void testGitHubReleaseNoWorkflowOnDockstore() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi client = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);
        io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(webClient);
        LambdaEventsApi lambdaEventsApi = new LambdaEventsApi(webClient);

        // Track install event
        client.handleGitHubInstallation(installationId, workflowRepo, BasicIT.USER_2_USERNAME);

        // Release 0.1 on GitHub - one new wdl workflow
        List<io.dockstore.openapi.client.model.Workflow> workflows = client.handleGitHubRelease("refs/tags/0.1", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        assertEquals("Should only have one service", 1, workflows.size());

        // Ensure that new workflow is created and is what is expected
        io.dockstore.openapi.client.model.Workflow workflow = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", "", false);
        assertEquals("Should be a WDL workflow", io.dockstore.openapi.client.model.Workflow.DescriptorTypeEnum.WDL, workflow.getDescriptorType());
        assertEquals("Should be type DOCKSTORE_YML", io.dockstore.openapi.client.model.Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode());
        assertEquals("Should have one version 0.1", 1, workflow.getWorkflowVersions().size());

        // Release 0.2 on GitHub - one existing wdl workflow, one new cwl workflow
        workflows = client.handleGitHubRelease("refs/tags/0.2", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        assertEquals("Should only have two services", 2, workflows.size());

        // Ensure that existing workflow is updated
        workflow = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", "", false);

        // Ensure that new workflow is created and is what is expected
        io.dockstore.openapi.client.model.Workflow workflow2 = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar2", "", false);
        assertEquals("Should be a CWL workflow", io.dockstore.openapi.client.model.Workflow.DescriptorTypeEnum.CWL, workflow2.getDescriptorType());
        assertEquals("Should be type DOCKSTORE_YML", io.dockstore.openapi.client.model.Workflow.ModeEnum.DOCKSTORE_YML, workflow2.getMode());
        assertEquals("Should have one version 0.2", 1, workflow2.getWorkflowVersions().size());

        // Branch master on GitHub - updates two existing workflows
        workflows = client.handleGitHubRelease("refs/heads/master", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        assertEquals("Should only have two services", 2, workflows.size());

        workflow = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", "", false);
        assertTrue("Should have a master version.", workflow.getWorkflowVersions().stream().anyMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "master")));
        assertTrue("Should have a 0.1 version.", workflow.getWorkflowVersions().stream().anyMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "0.1")));
        assertTrue("Should have a 0.2 version.", workflow.getWorkflowVersions().stream().anyMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")));

        workflow2 = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar2", "", false);
        assertTrue("Should have a master version.", workflow2.getWorkflowVersions().stream().anyMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "master")));
        assertTrue("Should have a 0.2 version.", workflow2.getWorkflowVersions().stream().anyMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")));

        // Master version should have metadata set
        Optional<io.dockstore.openapi.client.model.WorkflowVersion> masterVersion = workflow.getWorkflowVersions().stream().filter((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "master")).findFirst();
        assertEquals("Should have author set", "Test User", masterVersion.get().getAuthor());
        assertEquals("Should have email set", "test@dockstore.org", masterVersion.get().getEmail());
        assertEquals("Should have email set", "This is a description", masterVersion.get().getDescription());

        masterVersion = workflow2.getWorkflowVersions().stream().filter((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "master")).findFirst();
        assertEquals("Should have author set", "Test User", masterVersion.get().getAuthor());
        assertEquals("Should have email set", "test@dockstore.org", masterVersion.get().getEmail());
        assertEquals("Should have email set", "This is a description", masterVersion.get().getDescription());

        boolean hasLegacyVersion = workflow.getWorkflowVersions().stream().anyMatch(workflowVersion -> workflowVersion.isLegacyVersion());
        assertFalse("Workflow should not have any legacy refresh versions.", hasLegacyVersion);

        // Delete tag 0.2
        client.handleGitHubBranchDeletion(workflowRepo, BasicIT.USER_2_USERNAME, "refs/tags/0.2", installationId);
        workflow = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", "", false);
        assertTrue("Should not have a 0.2 version.", workflow.getWorkflowVersions().stream().noneMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")));
        workflow2 = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar2", "", false);
        assertTrue("Should not have a 0.2 version.", workflow2.getWorkflowVersions().stream().noneMatch((io.dockstore.openapi.client.model.WorkflowVersion version) -> Objects.equals(version.getName(), "0.2")));

        // Add version that doesn't exist
        try {
            client.handleGitHubRelease("refs/heads/idonotexist", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
            fail("Should fail and not reach this point");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            List<io.dockstore.openapi.client.model.LambdaEvent> failureEvents = usersApi.getUserGitHubEvents("0", 10);
            assertTrue("There should be 1 unsuccessful event", failureEvents.stream().filter(lambdaEvent -> !lambdaEvent.isSuccess()).count() == 1);
        }

        // There should be 5 successful lambda events
        List<io.dockstore.openapi.client.model.LambdaEvent> events = usersApi.getUserGitHubEvents("0", 10);
        assertTrue("There should be 5 successful events", events.stream().filter(io.dockstore.openapi.client.model.LambdaEvent::isSuccess).count() == 5);

        // Test pagination for user github events
        events = usersApi.getUserGitHubEvents("2", 2);
        assertEquals("There should be 2 events (id 3 and 4)", 2, events.size());
        assertTrue("Should have event with ID 3", events.stream().anyMatch(lambdaEvent -> Objects.equals(3L, lambdaEvent.getId())));
        assertTrue("Should have event with ID 4", events.stream().anyMatch(lambdaEvent -> Objects.equals(4L, lambdaEvent.getId())));

        // Test the organization events endpoint
        List<io.dockstore.openapi.client.model.LambdaEvent> orgEvents = lambdaEventsApi.getLambdaEventsByOrganization("DockstoreTestUser2", "0", 10);
        assertEquals("There should be 6 events", 6, orgEvents.size());

        // Test pagination
        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization("DockstoreTestUser2", "2", 2);
        assertEquals("There should be 2 events (id 3 and 4)", 2, orgEvents.size());
        assertTrue("Should have event with ID 3", orgEvents.stream().anyMatch(lambdaEvent -> Objects.equals(3L, lambdaEvent.getId())));
        assertTrue("Should have event with ID 4", orgEvents.stream().anyMatch(lambdaEvent -> Objects.equals(4L, lambdaEvent.getId())));

        // Change organization to test filter
        testingPostgres.runUpdateStatement("UPDATE lambdaevent SET repository = 'workflow-dockstore-yml', organization = 'DockstoreTestUser3' WHERE id = '1'");

        orgEvents = lambdaEventsApi.getLambdaEventsByOrganization("DockstoreTestUser2", "0", 10);
        assertEquals("There should now be 5 events", 5, orgEvents.size());

        try {
            lambdaEventsApi.getLambdaEventsByOrganization("IAmMadeUp", "0", 10);
            fail("Should not reach this statement");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertEquals("Should fail because user cannot access org.", HttpStatus.SC_UNAUTHORIZED, ex.getCode());
        }

        // Try adding version with empty test parameter file (should work)
        client.handleGitHubRelease("refs/heads/emptytestparameter", installationId, workflowRepo, BasicIT.USER_2_USERNAME);
        workflow2 = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar2", "", false);

        assertTrue("Should have emptytestparameter version that is valid", workflow2.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), "emptytestparameter")).findFirst().get().isValid());

    }

    /**
     * This tests calling refresh on a workflow with a Dockstore.yml
     */
    @Test
    public void testManualRefreshWorkflowWithGitHubApp() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        // Release 0.1 on GitHub - one new wdl workflow
        List<Workflow> workflows = client.handleGitHubRelease(workflowRepo, "DockstoreTestUser2", "refs/tags/0.1", installationId);
        assertEquals("Should only have one service", 1, workflows.size());

        // Ensure that new workflow is created and is what is expected
        Workflow workflow = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", "", false);
        assertEquals("Should be a WDL workflow", Workflow.DescriptorTypeEnum.WDL, workflow.getDescriptorType());
        assertEquals("Should be type DOCKSTORE_YML", Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode());
        assertTrue("Should have a 0.1 version.", workflow.getWorkflowVersions().stream().anyMatch((WorkflowVersion version) -> Objects.equals(version.getName(), "0.1")));
        boolean hasLegacyVersion = workflow.getWorkflowVersions().stream().anyMatch(workflowVersion -> workflowVersion.isLegacyVersion());
        assertFalse("Workflow should not have any legacy refresh versions.", hasLegacyVersion);

        // Refresh
        try {
            client.refresh(workflow.getId());
            fail("Should fail on refresh and not reach this point");
        } catch (ApiException ex) {
            assertEquals("Should not be able to refresh a dockstore.yml workflow.", HttpStatus.SC_BAD_REQUEST, ex.getCode());
        }
    }

    /**
     * This tests the GitHub release process does not work for users that do not exist on Dockstore
     */
    @Test
    public void testGitHubReleaseNoWorkflowOnDockstoreNoUser() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);

        try {
            client.handleGitHubRelease(workflowRepo, "thisisafakeuser", "refs/tags/0.1", installationId);
            Assert.fail("Should not reach this statement.");
        } catch (ApiException ex) {
            assertEquals("Should not be able to add a workflow when user does not exist on Dockstore.", LAMBDA_ERROR, ex.getCode());
        }
    }

    /**
     * This tests the GitHub release process when the dockstore.yml is
     * * Missing the primary descriptor
     * * Missing a test parameter file
     */
    @Test
    public void testInvalidDockstoreYmlFiles() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
        final ApiClient webClient = getWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi client = new WorkflowsApi(webClient);
        UsersApi usersApi = new UsersApi(webClient);

        // Release 0.1 on GitHub - one new wdl workflow
        List<Workflow> workflows = client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/tags/0.1", installationId);
        assertEquals("Should only have one service", 1, workflows.size());

        // Ensure that new workflow is created and is what is expected
        Workflow workflow = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", "", false);
        assertEquals("Should be a WDL workflow", Workflow.DescriptorTypeEnum.WDL, workflow.getDescriptorType());
        assertEquals("Should be type DOCKSTORE_YML", Workflow.ModeEnum.DOCKSTORE_YML, workflow.getMode());
        assertEquals("Should have one version 0.1", 1, workflow.getWorkflowVersions().size());
        assertTrue("Should be valid", workflow.getWorkflowVersions().get(0).isValid());

        // Push missingPrimaryDescriptor on GitHub - one existing wdl workflow, missing primary descriptor
        workflows = client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/missingPrimaryDescriptor", installationId);
        assertEquals("Should only have one service", 1, workflows.size());

        // Ensure that new version is in the correct state (invalid)
        workflow = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", "validations", false);
        assertNotNull(workflow);
        assertEquals("Should have two versions", 2, workflow.getWorkflowVersions().size());

        WorkflowVersion missingPrimaryDescriptorVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), "missingPrimaryDescriptor")).findFirst().get();
        assertFalse("Version should be invalid", missingPrimaryDescriptorVersion.isValid());

        // Check existence of files and validations
        assertTrue("Should have .dockstore.yml file", missingPrimaryDescriptorVersion.getSourceFiles().stream().filter(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), DOCKSTORE_YML_PATH)).findFirst().isPresent());
        assertTrue("Should not have doesnotexist.wdl file", missingPrimaryDescriptorVersion.getSourceFiles().stream().filter(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), "/doesnotexist.wdl")).findFirst().isEmpty());
        assertFalse("Should have invalid .dockstore.yml", missingPrimaryDescriptorVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), Validation.TypeEnum.DOCKSTORE_YML)).findFirst().get().isValid());
        assertFalse("Should have invalid doesnotexist.wdl", missingPrimaryDescriptorVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), Validation.TypeEnum.DOCKSTORE_WDL)).findFirst().get().isValid());

        // Push missingTestParameterFile on GitHub - one existing wdl workflow, missing a test parameter file
        workflows = client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/missingTestParameterFile", installationId);
        assertEquals("Should only have one service", 1, workflows.size());

        // Ensure that new version is in the correct state (invalid)
        workflow = client.getWorkflowByPath("github.com/" + workflowRepo + "/foobar", "validations", false);
        assertNotNull(workflow);
        assertEquals("Should have three versions", 3, workflow.getWorkflowVersions().size());

        WorkflowVersion missingTestParameterFileVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getName(), "missingTestParameterFile")).findFirst().get();
        assertTrue("Version should be valid (missing test parameter doesn't make the version invalid)", missingTestParameterFileVersion.isValid());

        // Check existence of files and validations
        assertTrue("Should have .dockstore.yml file", missingTestParameterFileVersion.getSourceFiles().stream().filter(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), DOCKSTORE_YML_PATH)).findFirst().isPresent());
        assertTrue("Should not have /test/doesnotexist.txt file", missingTestParameterFileVersion.getSourceFiles().stream().filter(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), "/test/doesnotexist.txt")).findFirst().isEmpty());
        assertTrue("Should have Dockstore2.wdl file", missingTestParameterFileVersion.getSourceFiles().stream().filter(sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), "/Dockstore2.wdl")).findFirst().isPresent());
        assertFalse("Should have invalid .dockstore.yml", missingTestParameterFileVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), Validation.TypeEnum.DOCKSTORE_YML)).findFirst().get().isValid());
        assertTrue("Should have valid Dockstore2.wdl", missingTestParameterFileVersion.getValidations().stream().filter(validation -> Objects.equals(validation.getType(), Validation.TypeEnum.DOCKSTORE_WDL)).findFirst().get().isValid());

        // There should be 3 successful lambda events
        List<LambdaEvent> events = usersApi.getUserGitHubEvents("0", 10);
        assertTrue("There should be 3 successful events", events.stream().filter(LambdaEvent::isSuccess).count() == 3);

        // Push branch with invalid dockstore.yml
        try {
            workflows = client.handleGitHubRelease(workflowRepo, BasicIT.USER_2_USERNAME, "refs/heads/invalidDockstoreYml", installationId);
            fail("Should not reach this statement");
        } catch (ApiException ex) {
            List<LambdaEvent> failEvents = usersApi.getUserGitHubEvents("0", 10);
            assertTrue("There should be 1 unsuccessful event", failEvents.stream().filter(lambdaEvent -> !lambdaEvent.isSuccess()).count() == 1);
        }
    }
}
