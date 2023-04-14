/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.views;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import java.util.Vector;

/**
 * Created by arne on 18.11.14.
 */
public class ScreenSlidePagerAdapter extends FragmentPagerAdapter {

    private final Resources res;
    private Bundle mFragmentArguments;

    public void setFragmentArgs(Bundle fragmentArguments) {
        mFragmentArguments = fragmentArguments;
    }

    static class Tab {
        Class<? extends Fragment> fragmentClass;
        String mName;

        Tab(Class<? extends Fragment> fClass, String name) {
            mName = name;
            fragmentClass = fClass;
        }
    }

    private Vector<Tab> mTabs = new Vector<>();

    public ScreenSlidePagerAdapter(FragmentManager fm, Context c) {
        super(fm);
        res = c.getResources();
    }

    @Override
    public Fragment getItem(int position) {
        try {
            Fragment fragment = mTabs.get(position).fragmentClass.newInstance();
            if (mFragmentArguments != null)
                fragment.setArguments(mFragmentArguments);
            return fragment;

        } catch (IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
        }
        return null;
    }

    public int getPageIndex(Class<? extends Fragment> clazz) {
        int position = 0;
        for (ScreenSlidePagerAdapter.Tab tab : mTabs) {
            if (tab.fragmentClass == clazz)
                return position;
            else
                position += 1;
        }
        return -1;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return mTabs.get(position).mName;
    }

    @Override
    public int getCount() {
        return mTabs.size();
    }

    public void addTab(@StringRes int name, Class<? extends Fragment> fragmentClass) {
        mTabs.add(new Tab(fragmentClass, res.getString(name)));
    }

}
