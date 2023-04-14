/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.utils;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ActivityUtils {

    public static void fadein(@NonNull View toggle, @NonNull View layout, int height, int duration) {
        toggle.setEnabled(false);

        ValueAnimator animator = ValueAnimator.ofInt(height, 0);
        animator.setDuration(duration);
        animator.addUpdateListener((animation) -> {
            int value = (Integer) animation.getAnimatedValue();
            if (value == 0) {
                toggle.setEnabled(true);
                layout.setVisibility(View.GONE);
            }
            layout.getLayoutParams().height = value;
            layout.setLayoutParams(layout.getLayoutParams());
        });
        animator.start();
    }

    public static void fadeout(@NonNull View toggle, @NonNull View layout, int height, int duration) {
        toggle.setEnabled(false);
        layout.setVisibility(View.VISIBLE);

        ValueAnimator animator = ValueAnimator.ofInt(0, height);
        animator.setDuration(duration);
        animator.addUpdateListener((animation) -> {
            int value = (Integer) animation.getAnimatedValue();
            if (value == height) {
                toggle.setEnabled(true);
            }
            layout.getLayoutParams().height = value;
            layout.setLayoutParams(layout.getLayoutParams());
        });
        animator.start();
    }

    /**
     * 根据手机的分辨率从 dp 的单位 转成为 px(像素)
     */
    public static int dp2px(@NonNull Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    public static int getResourceIdByName(@NonNull Context context, @NonNull String className, @NonNull String name) {
        String packageName = context.getPackageName();
        Class<?> clazz = null;
        int id = 0;

        try {
            clazz = Class.forName(packageName + ".R");
            Class[] classes = clazz.getClasses();
            Class desireClass = null;

            for (int i = 0; i < classes.length; i++) {
                if (classes[i].getName().split("\\$")[1].equals(className)) {
                    desireClass = classes[i];
                    break;
                }
            }
            if (desireClass != null)
                id = desireClass.getField(name).getInt(desireClass);

        } catch (ClassNotFoundException | IllegalArgumentException | SecurityException | IllegalAccessException | NoSuchFieldException ex) {
            ex.printStackTrace();
        }

        return id;
    }

    /**
     * 判断当前设备是手机还是平板，代码来自 Google I/O App for Android
     */
    public static boolean isTablet(@NonNull Context context) {
        return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >=
            Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    public static void runOnUiThread(@Nullable Activity activity, @Nullable Runnable action) {
        if (activity != null && action != null) {
            activity.runOnUiThread(action);
        }
    }

}
