/*
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
 */
package io.dockstore.common.yaml;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotNull;

/**
 * A workflow as described in a .dockstore.yml
 */
public class YamlWorkflow {
    private String name;
    @NotNull
    private String subclass;
    @NotNull
    private String primaryDescriptorPath;

    private List<String> testParameterFiles = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getSubclass() {
        return subclass;
    }

    public void setSubclass(final String subclass) {
        this.subclass = subclass;
    }

    public String getPrimaryDescriptorPath() {
        return primaryDescriptorPath;
    }

    public void setPrimaryDescriptorPath(final String primaryDescriptorPath) {
        this.primaryDescriptorPath = primaryDescriptorPath;
    }

    public List<String> getTestParameterFiles() {
        return testParameterFiles;
    }

    public void setTestParameterFiles(final List<String> testParameterFiles) {
        this.testParameterFiles = testParameterFiles;
    }
}
