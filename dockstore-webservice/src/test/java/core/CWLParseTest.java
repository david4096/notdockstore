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
package core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

public class CWLParseTest {


    @Test
    public void testOldMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example0.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(SourceFile.FileType.DOCKSTORE_CWL);
        Entry entry = sInterface.parseWorkflowContent(new Tool(), filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>());
        Assert.assertEquals("incorrect author", "Keiran Raine", entry.getAuthor());
        Assert.assertEquals("incorrect email", "keiranmraine@gmail.com", entry.getEmail());
    }

    @Test
    public void testNewMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example2.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(SourceFile.FileType.DOCKSTORE_CWL);
        Entry entry = sInterface.parseWorkflowContent(new Tool(), filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>());
        Assert.assertEquals("incorrect author", "Denis Yuen", entry.getAuthor());
        Assert.assertEquals("incorrect email", "dyuen@oicr.on.ca", entry.getEmail());
    }

    @Test
    public void testCombinedMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example3.cwl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(SourceFile.FileType.DOCKSTORE_CWL);
        Entry entry = sInterface.parseWorkflowContent(new Tool(), filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>());
        Assert.assertEquals("incorrect author", "Denis Yuen", entry.getAuthor());
        Assert.assertEquals("incorrect email", "dyuen@oicr.on.ca", entry.getEmail());
    }
}
