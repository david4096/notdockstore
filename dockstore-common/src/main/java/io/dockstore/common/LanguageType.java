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
package io.dockstore.common;

/**
 * Looks redundant with DescriptorType with some case-changes.
 * Moved here from dockstore-client.
 */
public enum LanguageType {
    CWL("cwl"), WDL("wdl"), NEXTFLOW("nextflow"), NONE("none");
    public final String desc;

    LanguageType(String name) {
        desc = name;
    }

    @Override
    public String toString() {
        return desc;
    }

}
