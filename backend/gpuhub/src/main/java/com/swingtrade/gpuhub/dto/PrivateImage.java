package com.swingtrade.gpuhub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Private image entry from POST /api/v1/dev/image/private/list.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrivateImage {

    private int id;

    @JsonProperty("image_name")
    private String imageName;

    @JsonProperty("image_uuid")
    private String imageUuid;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getImageName() { return imageName; }
    public void setImageName(String imageName) { this.imageName = imageName; }
    public String getImageUuid() { return imageUuid; }
    public void setImageUuid(String imageUuid) { this.imageUuid = imageUuid; }
}