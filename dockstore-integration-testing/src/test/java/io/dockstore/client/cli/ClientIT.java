/*
 *    Copyright 2016 OICR
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
import java.util.concurrent.TimeoutException;

import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.FileUtils;
import org.junit.*;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import com.google.common.io.Files;

import io.dockstore.common.CommonTestUtilities.TestingPostgres;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Registry;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.swagger.client.ApiException;

import static io.dockstore.common.CommonTestUtilities.DUMMY_TOKEN_1;
import static io.dockstore.common.CommonTestUtilities.clearState;
import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;

/**
 *
 * @author dyuen
 */
public class ClientIT {

    @ClassRule
    public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
            DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstore.yml"));

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Before
    public void clearDB() throws IOException, TimeoutException {
        clearState();
    }

    public static String getConfigFileLocation(boolean correctUser) throws IOException {
        return getConfigFileLocation(correctUser, true);
    }

    public static String getConfigFileLocation(boolean correctUser, boolean validPort) throws IOException {
        File tempDir = Files.createTempDir();
        final File tempFile = File.createTempFile("config", "config", tempDir);
        FileUtils.write(tempFile, "token: " + (correctUser ? DUMMY_TOKEN_1 : "foobar") + "\n");
        FileUtils.write(tempFile, "server-url: http://localhost:" + (validPort ? "8000" : "9001") + "\n", true);

        return tempFile.getAbsolutePath();
    }

    @Test
    public void testListEntries() throws IOException, TimeoutException, ApiException {
        Client.main(new String[] { "--config", getConfigFileLocation(true), "list" });
    }

    @Test
    public void testDebugModeListEntries() throws IOException, TimeoutException, ApiException {
        Client.main(new String[] { "--debug", "--config", getConfigFileLocation(true), "list" });
    }

    @Test
    public void testListEntriesWithoutCreds() throws IOException, TimeoutException, ApiException {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", getConfigFileLocation(false), "list" });
    }

    @Test
    public void testListEntriesOnWrongPort() throws IOException, TimeoutException, ApiException {
        systemExit.expectSystemExitWithStatus(Client.CONNECTION_ERROR);
        Client.main(new String[] { "--config", getConfigFileLocation(true, false), "list" });
    }

    // Won't work as entry must be valid
    @Ignore
    public void quickRegisterValidEntry() throws IOException {
        Client.main(new String[] { "--config", getConfigFileLocation(true), "publish", "quay.io/test_org/test6" });

        // verify DB
        final TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement("select count(*) from tool where name = 'test6'", new ScalarHandler<>());
        Assert.assertTrue("should see three entries", count == 1);
    }

    @Ignore
    public void quickRegisterDuplicateEntry() throws IOException {
        Client.main(new String[] { "--config", getConfigFileLocation(true), "publish", "quay.io/test_org/test6" });
        Client.main(new String[] { "--config", getConfigFileLocation(true), "publish", "quay.io/test_org/test6", "view1" });
        Client.main(new String[] { "--config", getConfigFileLocation(true), "publish", "quay.io/test_org/test6", "view2" });

        // verify DB
        final TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement("select count(*) from container where name = 'test6'", new ScalarHandler<>());
        Assert.assertTrue("should see three entries", count == 3);
    }

    @Test
    public void quickRegisterInValidEntry() throws IOException {
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", getConfigFileLocation(true), "publish", "quay.io/test_org/test1" });
    }

    @Test
    public void quickRegisterUnknownEntry() throws IOException {
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", getConfigFileLocation(true), "publish", "quay.io/funky_container_that_does_not_exist" });
    }

    /* When you manually publish on the dockstore CLI, it will now refresh the container after it is added.
     Since the below containers use dummy data and don't connect with Github/Bitbucket/Quay, the refresh will throw an error.
     Todo: Set up these tests with real data (not confidential)
     */
    @Ignore("Since dockstore now checks for associated tags for Quay container, manual publishing of nonexistant images won't work")
    public void manualRegisterABunchOfValidEntries() throws IOException {
        Client.main(new String[] { "--config", getConfigFileLocation(true), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master" });
        Client.main(new String[] { "--config", getConfigFileLocation(true), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master", "--toolname", "test1" });
        Client.main(new String[] { "--config", getConfigFileLocation(true), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master", "--toolname", "test2" });
        Client.main(new String[] { "--config", getConfigFileLocation(true), "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master" });
        Client.main(new String[] { "--config", getConfigFileLocation(true), "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master", "--toolname", "test1" });

        // verify DB
        final TestingPostgres testingPostgres = getTestingPostgres();
        final long count = testingPostgres.runSelectStatement("select count(*) from container where name = 'bd2k-python-lib'",
                new ScalarHandler<>());
        Assert.assertTrue("should see three entries", count == 5);
    }

    @Test
    public void manualRegisterADuplicate() throws IOException {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", getConfigFileLocation(true), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master" });
        Client.main(new String[] { "--config", getConfigFileLocation(true), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master", "--toolname", "test1" });
        Client.main(new String[] { "--config", getConfigFileLocation(true), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                "--namespace", "pypi", "--name", "bd2k-python-lib", "--git-url", "git@github.com:funky-user/test2.git", "--git-reference",
                "refs/head/master", "--toolname", "test1" });
    }




}
