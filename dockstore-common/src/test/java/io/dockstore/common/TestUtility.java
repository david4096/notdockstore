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

package io.dockstore.common;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static io.dockstore.common.CommonTestUtilities.DUMMY_TOKEN_1;

/**
 * Created by jpatricia on 12/05/16.
 */
public class TestUtility {
    public static String getConfigFileLocation(boolean correctUser) throws IOException {
        return getConfigFileLocation(correctUser, true);
    }

    public static String getConfigFileLocation(boolean correctUser, boolean validPort) throws IOException {
        File tempDir = Files.createTempDir();
        final File tempFile = File.createTempFile("config", "config", tempDir);
        FileUtils.write(tempFile, "token: " + (correctUser ? DUMMY_TOKEN_1 : "foobar") + "\n", StandardCharsets.UTF_8);
        FileUtils.write(tempFile, "server-url: http://localhost:" + (validPort ? "8000" : "9001") + "\n", StandardCharsets.UTF_8, true);

        return tempFile.getAbsolutePath();
    }
}
