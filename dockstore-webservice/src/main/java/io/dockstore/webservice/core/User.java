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

package io.dockstore.webservice.core;

import java.security.Principal;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.dockstore.webservice.helpers.UserStarredSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * Stores end user information
 *
 * @author xliu
 */
@ApiModel(value = "User", description = "End users for the dockstore")
@Entity
@Table(name = "enduser")
@NamedQueries({ @NamedQuery(name = "io.dockstore.webservice.core.User.findAll", query = "SELECT t FROM User t"),
        @NamedQuery(name = "io.dockstore.webservice.core.User.findByUsername", query = "SELECT t FROM User t WHERE t.username = :username") })
public class User implements Principal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    @ApiModelProperty("Implementation specific ID for the container in this web service")
    private long id;

    @Column(nullable = false, unique = true)
    @ApiModelProperty("Username on dockstore")
    private String username;

    @Column
    @ApiModelProperty(value = "Indicates whether this user is an admin", required = true)
    private boolean isAdmin;

    @Column
    @ApiModelProperty(value = "Company of user", required = false)
    private String company;

    @Column
    @ApiModelProperty(value = "Bio of user", required = false)
    private String bio;

    @Column
    @ApiModelProperty(value = "Location of user", required = false)
    private String location;

    @Column
    @ApiModelProperty(value = "Email of user", required = false)
    private String email;

    @Column
    @ApiModelProperty(value = "URL of user avatar on Github.", required = false)
    private String avatarUrl;

    @ManyToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinTable(name = "endusergroup", joinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "groupid", nullable = false, updatable = false, referencedColumnName = "id"))
    @ApiModelProperty("Groups that this user belongs to")
    @JsonIgnore
    private final Set<Group> groups;

    @ManyToMany(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(name = "user_entry", inverseJoinColumns = @JoinColumn(name = "entryid", nullable = false, updatable = false, referencedColumnName = "id"), joinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id"))
    @ApiModelProperty("Entries in the dockstore that this user manages")
    @JsonIgnore
    private final Set<Entry> entries;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "starred", inverseJoinColumns = @JoinColumn(name = "entryid", nullable = false, updatable = false, referencedColumnName = "id"), joinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id"))
    @ApiModelProperty("Entries in the dockstore that this user starred")
    @JsonSerialize(using = UserStarredSerializer.class)
    private final Set<Entry> starredEntries;

    public User() {
        groups = new HashSet<>(0);
        entries = new HashSet<>(0);
        starredEntries = new HashSet<>(0);
    }

    @JsonProperty
    public long getId() {
        return id;
    }

    @JsonProperty
    public String getUsername() {
        return username;
    }

    @JsonProperty
    public boolean getIsAdmin() {
        return isAdmin;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setIsAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    public Set<Group> getGroups() {
        return groups;
    }

    public void addGroup(Group group) {
        groups.add(group);
    }

    public boolean removeGroup(Group group) {
        return groups.remove(group);
    }

    public Set<Entry> getEntries() {
        return entries;
    }

    public void addEntry(Entry entry) {
        entries.add(entry);
    }

    public boolean removeEntry(Entry entry) {
        return entries.remove(entry);
    }

    public Set<Entry> getStarredEntries() {
        return starredEntries;
    }

    public void addStarredEntry(Entry entry) {
        starredEntries.add(entry);
    }

    public boolean removeStarredEntry(Entry entry) {
        return starredEntries.remove(entry);
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username);
    }

    @Override
    public String getName() {
        return getUsername();
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final User other = (User)obj;
        if (id != other.id) {
            return false;
        }
        if (!Objects.equals(username, other.username)) {
            return false;
        }
        // do not depend on lazily loaded collections for equality
        return Objects.equals(isAdmin, other.isAdmin);
    }

    public void update(User user) {
        email = user.getEmail();
        location = user.getLocation();
        bio = user.getBio();
        avatarUrl = user.getAvatarUrl();
        company = user.getCompany();
    }

}
