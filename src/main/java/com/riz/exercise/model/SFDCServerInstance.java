package com.riz.exercise.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.MoreObjects;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by rizjoj on 12/1/16.
 */
@Document(collection = "serverInstances")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SFDCServerInstance {

    @Id
    public String id;

    private String key;
    private String location;
    private String environment;
    private String releaseVersion;
    private String status;

    public SFDCServerInstance() {
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }

    public void setReleaseVersion(String releaseVersion) {
        this.releaseVersion = releaseVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this.getClass())
                        .add("key", this.getKey())
                        .add("location", this.getLocation())
                        .add("environment", this.getEnvironment())
                        .add("releaseVersion", this.getReleaseVersion())
                        .add("status", this.getStatus())
                        .add("_id", this.id)
                        .omitNullValues()
                        .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SFDCServerInstance that = (SFDCServerInstance) o;

        if (key != null ? !key.equals(that.key) : that.key != null) return false;
        return status != null ? status.equals(that.status) : that.status == null;
    }

    @Override
    public int hashCode() {
        int result = key != null ? key.hashCode() : 0;
        result = 31 * result + (status != null ? status.hashCode() : 0);
        return result;
    }
}
