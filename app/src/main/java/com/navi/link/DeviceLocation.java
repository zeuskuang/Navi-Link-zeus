package com.navi.link;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

/**
 * 持续获取车机自身 GPS 位置，缓存最新经纬度，供「设备位置 → 灯位置」距离计算使用。
 * 单例，懒初始化；GPS 不可用/无权限时静默降级（last 保持 null）。
 */
public class DeviceLocation {

    private static final String TAG = "DeviceLocation";
    private static DeviceLocation instance;

    private final LocationManager lm;
    private Location last;
    private boolean started = false;

    private DeviceLocation(Context ctx) {
        Context c = ctx.getApplicationContext();
        lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
    }

    public static synchronized DeviceLocation get(Context ctx) {
        if (instance == null) {
            instance = new DeviceLocation(ctx);
        }
        instance.ensureStarted();
        return instance;
    }

    /** 仅在已有权限时启动监听，避免重复注册 */
    private synchronized void ensureStarted() {
        if (started || lm == null) return;
        LocationListener lis = new LocationListener() {
            @Override
            public void onLocationChanged(Location l) {
                last = l;
            }

            @Override
            public void onStatusChanged(String p, int s, Bundle b) {
            }

            @Override
            public void onProviderEnabled(String p) {
            }

            @Override
            public void onProviderDisabled(String p) {
            }
        };
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, lis);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            Log.w(TAG, "GPS provider 不可用", ignored);
        }
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 0, lis);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            Log.w(TAG, "NETWORK provider 不可用", ignored);
        }
        // 立即取一次最后已知位置
        try {
            if (last == null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (last == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            Log.w(TAG, "getLastKnownLocation 被拒", ignored);
        }
        started = true;
    }

    public synchronized Location getLast() {
        return last;
    }

    /** 地球两点球面距离（米），haversine 公式，地球半径 6371000m */
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
