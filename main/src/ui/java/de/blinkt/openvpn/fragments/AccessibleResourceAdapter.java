package de.blinkt.openvpn.fragments;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import de.blinkt.xp.openvpn.R;

import java.util.List;

import de.blinkt.openvpn.core.AccessibleResource;

public class AccessibleResourceAdapter extends BaseAdapter {

    private List<AccessibleResource> mAccessibleResources;
    private LayoutInflater inflater;

    public AccessibleResourceAdapter(@NonNull Context context, @NonNull List<AccessibleResource> accessibleResources) {
        this.mAccessibleResources = accessibleResources;
        this.inflater = LayoutInflater.from(context);
    }

    public List<AccessibleResource> getAccessibleResources() {
        return mAccessibleResources;
    }

    public void setAccessibleResources(@NonNull List<AccessibleResource> accessibleResources) {
        this.mAccessibleResources = accessibleResources;
    }

    @Override
    public int getCount() {
        return mAccessibleResources.size();
    }

    @Override
    public Object getItem(int position) {
        return mAccessibleResources.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = new ViewHolder();
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.accessible_resource_item, null);
            holder.view = convertView.findViewById(R.id.name);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.view.setText(mAccessibleResources.get(position).getName());
        return convertView;
    }

    public class ViewHolder {
        public TextView view;
    }

}
