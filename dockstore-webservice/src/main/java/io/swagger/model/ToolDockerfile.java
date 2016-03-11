package io.swagger.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;



/**
 * A tool dockerfile is a document that describes how to build a particular Docker image.
 **/

@ApiModel(description = "A tool dockerfile is a document that describes how to build a particular Docker image.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-03-11T20:14:17.098Z")
public class ToolDockerfile   {
  
  private String dockerfile = null;
  private String url = null;

  
  /**
   * The dockerfile content for this tool.
   **/
  public ToolDockerfile dockerfile(String dockerfile) {
    this.dockerfile = dockerfile;
    return this;
  }

  
  @ApiModelProperty(required = true, value = "The dockerfile content for this tool.")
  @JsonProperty("dockerfile")
  public String getDockerfile() {
    return dockerfile;
  }
  public void setDockerfile(String dockerfile) {
    this.dockerfile = dockerfile;
  }

  
  /**
   * Optional url to the dockerfile used to build this image, should include version information, and can include a git hash  (e.g. https://raw.githubusercontent.com/ICGC-TCGA-PanCancer/pcawg_delly_workflow/c83478829802b4d36374870843821abe1b625a71/delly_docker/Dockerfile )
   **/
  public ToolDockerfile url(String url) {
    this.url = url;
    return this;
  }

  
  @ApiModelProperty(value = "Optional url to the dockerfile used to build this image, should include version information, and can include a git hash  (e.g. https://raw.githubusercontent.com/ICGC-TCGA-PanCancer/pcawg_delly_workflow/c83478829802b4d36374870843821abe1b625a71/delly_docker/Dockerfile )")
  @JsonProperty("url")
  public String getUrl() {
    return url;
  }
  public void setUrl(String url) {
    this.url = url;
  }

  

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ToolDockerfile toolDockerfile = (ToolDockerfile) o;
    return Objects.equals(dockerfile, toolDockerfile.dockerfile) &&
        Objects.equals(url, toolDockerfile.url);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dockerfile, url);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ToolDockerfile {\n");
    
    sb.append("    dockerfile: ").append(toIndentedString(dockerfile)).append("\n");
    sb.append("    url: ").append(toIndentedString(url)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

