package io.swagger.client.model;

import io.swagger.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * A tool dockerfile is a document that describes how to build a particular Docker image.
 **/
@ApiModel(description = "A tool dockerfile is a document that describes how to build a particular Docker image.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-02-24T16:55:56.211-05:00")
public class ToolDockerfile   {
  
  private String dockerfile = null;

  
  /**
   * The dockerfile content for this tool.
   **/
  @ApiModelProperty(required = true, value = "The dockerfile content for this tool.")
  @JsonProperty("dockerfile")
  public String getDockerfile() {
    return dockerfile;
  }
  public void setDockerfile(String dockerfile) {
    this.dockerfile = dockerfile;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class ToolDockerfile {\n");
    
    sb.append("    dockerfile: ").append(StringUtil.toIndentedString(dockerfile)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
