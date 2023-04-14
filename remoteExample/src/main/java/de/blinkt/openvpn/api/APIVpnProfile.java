package de.blinkt.openvpn.api;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class APIVpnProfile implements Parcelable {

    public final String uuid;
    public final String name;
    public final boolean userEditable;

    public APIVpnProfile(@NonNull Parcel in) {
        uuid = in.readString();
        name = in.readString();
        userEditable = in.readInt() != 0;
    }

    public APIVpnProfile(@NonNull String uuid, @NonNull String name, boolean userEditable) {
        this.uuid = uuid;
        this.name = name;
        this.userEditable = userEditable;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(uuid);
        dest.writeString(name);
        if (userEditable)
            dest.writeInt(0);
        else
            dest.writeInt(1);
    }

    public static final Parcelable.Creator<APIVpnProfile> CREATOR = new Parcelable.Creator<APIVpnProfile>() {
        public APIVpnProfile createFromParcel(Parcel in) {
            return new APIVpnProfile(in);
        }

        public APIVpnProfile[] newArray(int size) {
            return new APIVpnProfile[size];
        }
    };

}
