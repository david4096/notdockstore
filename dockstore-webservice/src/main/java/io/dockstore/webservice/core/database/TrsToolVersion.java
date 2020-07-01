package io.dockstore.webservice.core.database;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.dockstore.common.DescriptorLanguage;
import io.openapi.model.ImageData;

public class TrsToolVersion {
    private final long id;
    private final long entryId;

    private final List<String> author = new ArrayList<>();
    private final String name;
    //    private final String url;
    private final boolean production;
    private final List<ImageData> images = new ArrayList<>();
    private final List<DescriptorLanguage.FileType> descriptorTypes = new ArrayList<>();
    //    private final Boolean containerFile;
    private final String metaVersion;
    //    private final Boolean verified;
    private final List<String> verifiedSource = new ArrayList<>();
    //    private final Boolean signed;
    private final List<String> includedApps = new ArrayList<>();

    public TrsToolVersion(final long id, final long entryId, final String author, final String name, final boolean production,
            final Date date) {
        this.entryId = entryId;
        this.name = name;
        this.production = production;
        this.id = id;
        this.getAuthor().add(author);
        this.metaVersion = String.valueOf(date != null ? date : new Date(0));
    }

    public long getEntryId() {
        return entryId;
    }

    public List<String> getAuthor() {
        return author;
    }

    public String getName() {
        return name;
    }

    public boolean isProduction() {
        return production;
    }

    public long getId() {
        return id;
    }

    public List<ImageData> getImages() {
        return images;
    }

    public List<DescriptorLanguage.FileType> getDescriptorTypes() {
        return descriptorTypes;
    }

    public List<String> getVerifiedSource() {
        return verifiedSource;
    }

    public List<String> getIncludedApps() {
        return includedApps;
    }

    public String getMetaVersion() {
        return metaVersion;
    }

}


