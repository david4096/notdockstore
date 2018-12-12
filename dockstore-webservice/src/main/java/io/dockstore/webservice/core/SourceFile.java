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

import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.MapKeyColumn;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ComparisonChain;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * This describes a cached copy of a remotely accessible file. Implementation specific.
 *
 * @author xliu
 */
@ApiModel("SourceFile")
@Entity
@Table(name = "sourcefile")
@SuppressWarnings("checkstyle:magicnumber")
public class SourceFile implements Comparable<SourceFile> {

    public static final EnumSet<FileType> TEST_FILE_TYPES = EnumSet.of(FileType.CWL_TEST_JSON, FileType.WDL_TEST_JSON, FileType.NEXTFLOW_TEST_PARAMS);

    /**
     * NextFlow parameter files are described here https://github.com/nextflow-io/nextflow/issues/208
     *
     */
    public enum FileType {
        // Add supported descriptor types here
        DOCKSTORE_CWL, DOCKSTORE_WDL, DOCKERFILE, CWL_TEST_JSON, WDL_TEST_JSON, NEXTFLOW, NEXTFLOW_CONFIG, NEXTFLOW_TEST_PARAMS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty(value = "Implementation specific ID for the source file in this web service", position = 0)
    private long id;

    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "Enumerates the type of file", required = true, position = 1)
    private FileType type;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "Cache for the contents of the target file", position = 2)
    private String content;

    @Column(nullable = false)
    @ApiModelProperty(value = "Path to sourcefile relative to its parent", required = true, position = 3)
    private String path;

    @Column(nullable = false)
    @ApiModelProperty(value = "Absolute path of sourcefile in git repo", required = true, position = 4)
    private String absolutePath;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    @ElementCollection(targetClass = VerificationInformation.class, fetch = FetchType.EAGER)
    @JoinTable(name = "sourcefile_verified", joinColumns = @JoinColumn(name = "id"), uniqueConstraints = @UniqueConstraint(columnNames = {
        "id", "source" }))
    @MapKeyColumn(name = "source", columnDefinition = "text")
    @ApiModelProperty(value = "maps from platform to whether an entry successfully ran on it using this test json")
    private Map<String, VerificationInformation> verifiedBySource = new HashMap<>();

    public Map<String, VerificationInformation> getVerifiedBySource() {
        return verifiedBySource;
    }

    public void setVerifiedBySource(Map<String, VerificationInformation> verifiedBySource) {
        this.verifiedBySource = verifiedBySource;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public FileType getType() {
        return type;
    }

    public void setType(FileType type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPath() {
        return this.path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public void setAbsolutePath(String absolutePath) {
        this.absolutePath = absolutePath;
    }

    @JsonIgnore
    public Timestamp getDbCreateDate() {
        return dbCreateDate;
    }

    @JsonIgnore
    public Timestamp getDbUpdateDate() {
        return dbUpdateDate;
    }

    // removed overridden hashcode and equals, resulted in issue due to https://hibernate.atlassian.net/browse/HHH-3799

    @Override
    public int compareTo(@NotNull SourceFile that) {
        if (this.absolutePath == null || that.absolutePath == null) {
            return ComparisonChain.start().compare(this.path, that.path).result();
        } else {
            return ComparisonChain.start().compare(this.absolutePath, that.absolutePath).result();
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("type", type).add("path", path).add("absolutePath", absolutePath).toString();
    }

    /**
     * Stores verification information for a given (test) file
     */
    @Embeddable
    public static class VerificationInformation {
        public boolean verified = false;
        @Column(columnDefinition = "text")
        public String metadata = "";

        @Column(columnDefinition = "text", nullable = false)
        public String platformVersion = "";

        // database timestamps
        @Column(updatable = false)
        @CreationTimestamp
        private Timestamp dbCreateDate;

        @Column()
        @UpdateTimestamp
        private Timestamp dbUpdateDate;
    }
}
