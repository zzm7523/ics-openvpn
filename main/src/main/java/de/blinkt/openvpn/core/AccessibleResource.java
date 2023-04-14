/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.Objects;

public class AccessibleResource implements Parcelable, Serializable {

    private String name;
    private String uri;
    private String platform;
    private String program;
    private String description;

    public AccessibleResource() {
    }

    public AccessibleResource(@NonNull String name, @NonNull String uri) {
        this(name, uri, null, null, null);
    }

    public AccessibleResource(@NonNull String name, @NonNull String uri, String description) {
        this(name, uri, null, null, description);
    }

    public AccessibleResource(@NonNull String name, @NonNull String uri, String platform, String program, String description) {
        this.name = name;
        this.uri = uri;
        this.platform = platform;
        this.program = program;
        this.description = description;
    }

    public AccessibleResource(@NonNull Parcel in) {
        name = in.readString();
        uri = in.readString();
        platform = in.readString();
        program = in.readString();
        description = in.readString();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getProgram() {
        return program;
    }

    public void setProgram(String program) {
        this.program = program;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccessibleResource that = (AccessibleResource) o;
        return name.equals(that.name) && uri.equals(that.uri) && Objects.equals(platform, that.platform);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, uri, platform);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(uri);
        dest.writeString(platform);
        dest.writeString(program);
        dest.writeString(description);
    }

    public static final Creator<AccessibleResource> CREATOR = new Creator<AccessibleResource>() {
        @Override
        public AccessibleResource createFromParcel(Parcel in) {
            return new AccessibleResource(in);
        }

        @Override
        public AccessibleResource[] newArray(int size) {
            return new AccessibleResource[size];
        }
    };

}
