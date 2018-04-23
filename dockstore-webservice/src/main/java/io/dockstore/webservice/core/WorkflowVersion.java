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

package io.dockstore.webservice.core;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.io.FilenameUtils;

/**
 * This implements version for a Workflow.
 *
 * @author dyuen
 */
@ApiModel(value = "WorkflowVersion", description = "This describes one workflow version associated with a workflow.")
@Entity
@SuppressWarnings("checkstyle:magicnumber")
public class WorkflowVersion extends Version<WorkflowVersion> implements Comparable<WorkflowVersion> {

    @Column(columnDefinition = "text", nullable = false)
    @JsonProperty("workflow_path")
    @ApiModelProperty(value = "Path for the workflow", position = 12)
    private String workflowPath;

    public WorkflowVersion() {
        super();
    }

    @Override
    public String getWorkingDirectory() {
        if (!workflowPath.isEmpty()) {
            return FilenameUtils.getPathNoEndSeparator(workflowPath);
        }
        return "";
    }

    public void updateByUser(final WorkflowVersion workflowVersion) {
        super.updateByUser(workflowVersion);
        workflowPath = workflowVersion.workflowPath;
    }

    public void update(WorkflowVersion workflowVersion) {
        super.update(workflowVersion);
        super.setReference(workflowVersion.getReference());
        workflowPath = workflowVersion.getWorkflowPath();
    }

    public void clone(WorkflowVersion tag) {
        super.clone(tag);
        super.setReference(tag.getReference());
    }

    @JsonProperty
    public String getWorkflowPath() {
        return workflowPath;
    }

    public void setWorkflowPath(String workflowPath) {
        this.workflowPath = workflowPath;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final WorkflowVersion other = (WorkflowVersion)obj;
        return Objects.equals(super.getName(), other.getName()) && Objects.equals(super.getReference(), other.getReference());
    }

    @Override
    public int compareTo(WorkflowVersion that) {
        return ComparisonChain.start().compare(this.getName(), that.getName(), Ordering.natural().nullsLast()).compare(this.getReference(), that.getReference()).result();
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, reference);
    }


    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("name", name).add("reference", reference).toString();
    }
}
