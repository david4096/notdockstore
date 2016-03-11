package io.swagger.client.model;

import io.swagger.client.StringUtil;
import io.swagger.client.model.User;
import io.swagger.client.model.Label;
import java.util.*;
import java.util.Date;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-11T15:28:43.725-05:00")
public class Entry   {
  
  private Long id = null;
  private String author = null;
  private String description = null;
  private List<Label> labels = new ArrayList<Label>();
  private List<User> users = new ArrayList<User>();
  private String email = null;
  private Date lastUpdated = null;
  private String gitUrl = null;
  private Boolean isStarred = null;
  private Boolean isPublic = null;
  private Integer lastModified = null;
  private Boolean isRegistered = null;

  
  /**
   * Implementation specific ID for the container in this web service
   **/
  @ApiModelProperty(value = "Implementation specific ID for the container in this web service")
  @JsonProperty("id")
  public Long getId() {
    return id;
  }
  public void setId(Long id) {
    this.id = id;
  }

  
  /**
   * This is the name of the author stated in the Dockstore.cwl
   **/
  @ApiModelProperty(value = "This is the name of the author stated in the Dockstore.cwl")
  @JsonProperty("author")
  public String getAuthor() {
    return author;
  }
  public void setAuthor(String author) {
    this.author = author;
  }

  
  /**
   * This is a human-readable description of this container and what it is trying to accomplish, required GA4GH
   **/
  @ApiModelProperty(value = "This is a human-readable description of this container and what it is trying to accomplish, required GA4GH")
  @JsonProperty("description")
  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  
  /**
   * Labels (i.e. meta tags) for describing the purpose and contents of containers
   **/
  @ApiModelProperty(value = "Labels (i.e. meta tags) for describing the purpose and contents of containers")
  @JsonProperty("labels")
  public List<Label> getLabels() {
    return labels;
  }
  public void setLabels(List<Label> labels) {
    this.labels = labels;
  }

  
  /**
   * This indicates the users that have control over this entry, dockstore specific
   **/
  @ApiModelProperty(value = "This indicates the users that have control over this entry, dockstore specific")
  @JsonProperty("users")
  public List<User> getUsers() {
    return users;
  }
  public void setUsers(List<User> users) {
    this.users = users;
  }

  
  /**
   * This is the email of the git organization
   **/
  @ApiModelProperty(value = "This is the email of the git organization")
  @JsonProperty("email")
  public String getEmail() {
    return email;
  }
  public void setEmail(String email) {
    this.email = email;
  }

  
  /**
   * Implementation specific timestamp for last updated on webservice
   **/
  @ApiModelProperty(value = "Implementation specific timestamp for last updated on webservice")
  @JsonProperty("lastUpdated")
  public Date getLastUpdated() {
    return lastUpdated;
  }
  public void setLastUpdated(Date lastUpdated) {
    this.lastUpdated = lastUpdated;
  }

  
  /**
   * This is a link to the associated repo with a descriptor, required GA4GH
   **/
  @ApiModelProperty(required = true, value = "This is a link to the associated repo with a descriptor, required GA4GH")
  @JsonProperty("gitUrl")
  public String getGitUrl() {
    return gitUrl;
  }
  public void setGitUrl(String gitUrl) {
    this.gitUrl = gitUrl;
  }

  
  /**
   * Implementation specific hook for social starring in this web service
   **/
  @ApiModelProperty(value = "Implementation specific hook for social starring in this web service")
  @JsonProperty("is_starred")
  public Boolean getIsStarred() {
    return isStarred;
  }
  public void setIsStarred(Boolean isStarred) {
    this.isStarred = isStarred;
  }

  
  /**
   * Implementation specific visibility in this web service
   **/
  @ApiModelProperty(value = "Implementation specific visibility in this web service")
  @JsonProperty("is_public")
  public Boolean getIsPublic() {
    return isPublic;
  }
  public void setIsPublic(Boolean isPublic) {
    this.isPublic = isPublic;
  }

  
  /**
   * Implementation specific timestamp for last modified
   **/
  @ApiModelProperty(value = "Implementation specific timestamp for last modified")
  @JsonProperty("last_modified")
  public Integer getLastModified() {
    return lastModified;
  }
  public void setLastModified(Integer lastModified) {
    this.lastModified = lastModified;
  }

  
  /**
   * Implementation specific indication as to whether this is properly registered with this web service
   **/
  @ApiModelProperty(value = "Implementation specific indication as to whether this is properly registered with this web service")
  @JsonProperty("is_registered")
  public Boolean getIsRegistered() {
    return isRegistered;
  }
  public void setIsRegistered(Boolean isRegistered) {
    this.isRegistered = isRegistered;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class Entry {\n");
    
    sb.append("    id: ").append(StringUtil.toIndentedString(id)).append("\n");
    sb.append("    author: ").append(StringUtil.toIndentedString(author)).append("\n");
    sb.append("    description: ").append(StringUtil.toIndentedString(description)).append("\n");
    sb.append("    labels: ").append(StringUtil.toIndentedString(labels)).append("\n");
    sb.append("    users: ").append(StringUtil.toIndentedString(users)).append("\n");
    sb.append("    email: ").append(StringUtil.toIndentedString(email)).append("\n");
    sb.append("    lastUpdated: ").append(StringUtil.toIndentedString(lastUpdated)).append("\n");
    sb.append("    gitUrl: ").append(StringUtil.toIndentedString(gitUrl)).append("\n");
    sb.append("    isStarred: ").append(StringUtil.toIndentedString(isStarred)).append("\n");
    sb.append("    isPublic: ").append(StringUtil.toIndentedString(isPublic)).append("\n");
    sb.append("    lastModified: ").append(StringUtil.toIndentedString(lastModified)).append("\n");
    sb.append("    isRegistered: ").append(StringUtil.toIndentedString(isRegistered)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
