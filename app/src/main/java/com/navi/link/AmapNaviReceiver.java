package com.navi.link;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AmapNaviReceiver extends BroadcastReceiver {

    private static final String TAG = "AmapNavi";
    private boolean isLog = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
        String action = intent.getAction();

        // —— 高德注入的巡航设施(红绿灯/摄像头)坐标广播 ——
        if ("com.navi.link.CRUISE_FACILITY".equals(action)) {
            handleCruiseFacility(context, intent);
            return;
        }

        if (!"AUTONAVI_STANDARD_BROADCAST_SEND".equals(action)) return;


        FloatingWindowManager manager = FloatingWindowManager.getInstance();
        if (manager == null) return;

        int keyType = intent.getIntExtra("KEY_TYPE", 0);


        Bundle extras = intent.getExtras();
        if (extras != null && isLog) {
            // 打印所有原始数据
            Log.d(TAG, "========== 🚥所有原始数据包==========");
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                Log.d(TAG, "Key: " + key + " | Value: " + value + " | Type: " + (value != null ? value.getClass().getSimpleName() : "null"));
            }
            Log.d(TAG, "==========================================================");
        }
        if (keyType == 60073) {
            // 红绿灯数据也视为有活动数据，重置 5秒 看门狗
            manager.resetWatchdog();
            // 红绿灯数据
            handleTrafficLight(intent, manager);
            if (manager.getCurrentMode() == FloatingWindowManager.MODE_NAVI) {
                manager.resetNaviTimeout();
            }
            return;
        }

        if (keyType == 13011) {
            // TMC 路况数据
            String tmcSegment = intent.getStringExtra("EXTRA_TMC_SEGMENT");
            if (tmcSegment != null) {
                manager.updateTmcData(tmcSegment);
                txTmc(tmcSegment);
            }
            return;
        }

        if (keyType == 13012) {
            // 车道线数据
            String driveWay = intent.getStringExtra("EXTRA_DRIVE_WAY");
            if (driveWay != null) {
                manager.updateLaneLines(driveWay);
                txLaneLines(driveWay);
            }
            return;
        }

        // 昼夜模式切换及前后台、结束状态广播
        if (keyType == 10019) {
            int extraState = intent.getIntExtra("EXTRA_STATE", -1);
            if (extraState == 37 || extraState == 38) {
                boolean isNight = (extraState == 38);
                manager.onDayNightChanged(isNight);
                txDayNight(isNight);
            } else if (extraState == 3 || extraState == 4) {
                boolean isForeground = (extraState == 3);
                manager.onAmapForegroundChanged(isForeground);
            } else if (extraState == 9) {
                manager.onNavigationEnded();
            } else if (extraState == 25) {
                manager.onCruiseEnded();
            }

            // 路口放大图状态（EXTRA_CROSS_MAP = 1 表示有路口放大图）
            if (intent.hasExtra("EXTRA_CROSS_MAP")) {
                int crossMap = intent.getIntExtra("EXTRA_CROSS_MAP", 0);
                manager.updateCrossMapStatus(crossMap);
                txCrossMap(crossMap);
            }

            return;
        }

        if (keyType == 10001) {
            // 导航或巡航信息
            if (manager.isNavigationJustEnded() || manager.isCruiseJustEnded()) {
                return;
            }
            manager.resetWatchdog();

            int icon = intent.getIntExtra("NEW_ICON", 0);
            if (icon == 0) {
                icon = intent.getIntExtra("ICON", 0);
            }

            if (icon != 0) {
                // 有转向图标，说明在导航模式
                manager.switchToNaviMode();
                handleNaviInfo(intent, manager);
            } else {
                if (manager.getCurrentMode() == FloatingWindowManager.MODE_NAVI || manager.isNaviWindowActive()) {
                    // 导航模式但无新icon，或当前依然是导航窗口，立即切换到巡航模式
                    manager.switchToCruiseMode();
                }
                // 巡航模式：只有巡航启用时才处理数据
                if (manager.isCruiseEnabled()) {
                    handleCruiseInfo(intent, manager);
                }
            }
        }
        } catch (Throwable tr) {
            Log.e(TAG, "onReceive crashed, action=" + (intent != null ? intent.getAction() : "null"), tr);
        }
    }

    private void handleTrafficLight(Intent intent, FloatingWindowManager manager) {
        if (manager.getCurrentMode() == FloatingWindowManager.MODE_NAVI) {
            int status = intent.getIntExtra("trafficLightStatus", 0);
            int dir = intent.getIntExtra("dir", 4);
            int countdown = intent.getIntExtra("redLightCountDownSeconds", 0);
            manager.updateTrafficLight(status, dir, countdown);
            txTrafficLight(status, dir, countdown);
            return;
        }
        // 巡航模式红绿灯数据
        String lightsData = intent.getStringExtra("lightsData");
        if (lightsData != null) {
            try {
                manager.updateCruiseTrafficLights(new JSONArray(lightsData));
                txCruiseTrafficLights(lightsData);
            } catch (Exception e) {
                Log.e(TAG, "解析巡航红绿灯数据失败", e);
            }
        }
    }

    private void handleNaviInfo(Intent intent, FloatingWindowManager manager) {
        String segRemainDis = intent.getStringExtra("SEG_REMAIN_DIS_AUTO");
        String routeRemainDis = intent.getStringExtra("ROUTE_REMAIN_DIS_AUTO");
        String routeRemainTime = intent.getStringExtra("ROUTE_REMAIN_TIME_AUTO");
        String etaText = intent.getStringExtra("ETA_TEXT");
        String nextRoadName = intent.getStringExtra("NEXT_ROAD_NAME");
        String curRoadName = intent.getStringExtra("CUR_ROAD_NAME");

        int icon = intent.getIntExtra("NEW_ICON", 0);
        if (icon == 0) {
            icon = intent.getIntExtra("ICON", 0);
        }

        // 安全兜底防空指针
        if (segRemainDis == null) segRemainDis = "0米";
        if (routeRemainDis == null) routeRemainDis = "0公里";
        if (routeRemainTime == null) routeRemainTime = "0分钟";
        if (nextRoadName == null) nextRoadName = curRoadName;
        if (nextRoadName == null) nextRoadName = "未知道路";
        String roadName = nextRoadName;
        String eta = etaText != null ? etaText : "";

        // 智能拆分距离与单位
        String disUnit = "公里";
        if (segRemainDis.endsWith("公里")) {
            segRemainDis = segRemainDis.replace("公里", "");
        } else {
            disUnit = "米";
            if (segRemainDis.endsWith("米")) {
                segRemainDis = segRemainDis.replace("米", "");
            }
        }
        String disNum = segRemainDis;

        // 拼装底部 Summary 文本
        String summaryStr = routeRemainDis + " · " + routeRemainTime;

        // 进度条计算
        int routeRemainDisInt = intent.getIntExtra("ROUTE_REMAIN_DIS", 0);
        int routeAllDis = intent.getIntExtra("ROUTE_ALL_DIS", 1);
        int progressPercentage = routeAllDis > 0
                ? (int) ((1.0f - (float) routeRemainDisInt / routeAllDis) * 100)
                : 0;

        int curSpeed = intent.getIntExtra("CUR_SPEED", 0);
        int limitedSpeed = intent.getIntExtra("LIMITED_SPEED", 0);
        int cameraDist = intent.getIntExtra("CAMERA_DIST", 0);
        int cameraSpeed = intent.getIntExtra("CAMERA_SPEED", 0);
        int cameraType = intent.getIntExtra("CAMERA_TYPE", 0);
        String endPoiName = intent.getStringExtra("endPOIName");
        int totalLightNum = intent.getIntExtra("TRAFFIC_LIGHT_NUM", 0);
        int remainLightNum = intent.getIntExtra("routeRemainTrafficLightNum", 0);
        int carDirection = intent.getIntExtra("CAR_DIRECTION", -1);

        manager.updateNaviInfo(icon, disNum, disUnit, "进入", roadName,
                summaryStr, eta, progressPercentage, curSpeed,
                limitedSpeed, cameraType, cameraDist, cameraSpeed,
                endPoiName, totalLightNum, remainLightNum, curRoadName, carDirection);

        // 转发导航数据帧
        txNavi(icon, disNum, disUnit, "进入", roadName,
                summaryStr, eta, progressPercentage, curSpeed,
                limitedSpeed, cameraType, cameraDist, cameraSpeed,
                endPoiName, totalLightNum, remainLightNum, curRoadName, carDirection);

        // 出口信息
        String exitName = intent.getStringExtra("EXIT_NAME_INFO");
        String exitDirection = intent.getStringExtra("EXIT_DIRECTION_INFO");
        manager.updateExitInfo(exitName, exitDirection);
        txExit(exitName, exitDirection);

        // 服务区信息
        String sapaName = intent.getStringExtra("SAPA_NAME");
        String sapaDist = intent.getStringExtra("SAPA_DIST_AUTO");
        int sapaType = intent.getIntExtra("SAPA_TYPE", 0);
        String nextSapaName = intent.getStringExtra("NEXT_SAPA_NAME");
        String nextSapaDist = intent.getStringExtra("NEXT_SAPA_DIST_AUTO");
        int nextSapaType = intent.getIntExtra("NEXT_SAPA_TYPE", 0);
        manager.updateSapaInfo(sapaName, sapaDist, sapaType, nextSapaName, nextSapaDist, nextSapaType);
        txSapa(sapaName, sapaDist, sapaType, nextSapaName, nextSapaDist, nextSapaType);
    }

    private void handleCruiseInfo(Intent intent, FloatingWindowManager manager) {
        int curSpeed = intent.getIntExtra("CUR_SPEED", 0);
        String curRoadName = intent.getStringExtra("CUR_ROAD_NAME");
        int cameraSpeed = intent.getIntExtra("CAMERA_SPEED", 0);
        int cameraDist = intent.getIntExtra("CAMERA_DIST", 0);
        int cameraType = intent.getIntExtra("CAMERA_TYPE", 0);
        int carDirection = intent.getIntExtra("CAR_DIRECTION", -1);
        if (curRoadName == null) curRoadName = "未知道路";
        manager.updateCruiseInfo(curSpeed, curRoadName, cameraType, cameraSpeed, cameraDist, carDirection);
        txCruise(curSpeed, curRoadName, cameraType, cameraSpeed, cameraDist, carDirection);
    }

    // ======================== TCP 副屏转发 ========================

    private void tx(JSONObject j) {
        SecondScreenTransmitter tx = SecondScreenTransmitter.getInstance();
        if (tx != null && tx.isRunning()) {
            tx.sendJson(j);
        }
    }

    private void txNavi(int icon, String disNum, String disUnit, String actionStr, String roadName,
                         String summaryStr, String eta, int progress, int curSpeed,
                         int limitedSpeed, int cameraType, int cameraDist, int cameraSpeed,
                         String endPoiName, int totalLightNum, int remainLightNum,
                         String curRoadName, int carDirection) {
        try {
            JSONObject j = new JSONObject();
            j.put("t", 1);
            j.put("icon", icon);
            j.put("disNum", disNum);
            j.put("disUnit", disUnit);
            j.put("actionStr", actionStr);
            j.put("roadName", roadName);
            j.put("summaryStr", summaryStr);
            j.put("eta", eta);
            j.put("progress", progress);
            j.put("curSpeed", curSpeed);
            j.put("limitedSpeed", limitedSpeed);
            j.put("cameraType", cameraType);
            j.put("cameraDist", cameraDist);
            j.put("cameraSpeed", cameraSpeed);
            j.put("endPoiName", endPoiName);
            j.put("totalLightNum", totalLightNum);
            j.put("remainLightNum", remainLightNum);
            j.put("curRoadName", curRoadName);
            j.put("carDirection", carDirection);
            tx(j);
        } catch (JSONException ignored) {
        }
    }

    private void txCruise(int speed, String roadName, int cameraType, int cameraSpeed, int cameraDist, int carDirection) {
        try {
            JSONObject j = new JSONObject();
            j.put("t", 2);
            j.put("speed", speed);
            j.put("roadName", roadName);
            j.put("cameraType", cameraType);
            j.put("cameraSpeed", cameraSpeed);
            j.put("cameraDist", cameraDist);
            j.put("carDirection", carDirection);
            tx(j);
        } catch (JSONException ignored) {
        }
    }

    private void txTrafficLight(int status, int dir, int countdown) {
        try {
            JSONObject j = new JSONObject();
            j.put("t", 3);
            j.put("status", status);
            j.put("dir", dir);
            j.put("countdown", countdown);
            tx(j);
        } catch (JSONException ignored) {
        }
    }

    private void txCruiseTrafficLights(String lightsData) {
        try {
            JSONObject j = new JSONObject();
            j.put("t", 10);
            j.put("lights", lightsData);
            tx(j);
        } catch (JSONException ignored) {
        }
    }

    private void txLaneLines(String driveWayJson) {
        try {
            JSONObject j = new JSONObject();
            j.put("t", 4);
            j.put("data", driveWayJson);
            tx(j);
        } catch (JSONException ignored) {
        }
    }

    private void txTmc(String tmcJson) {
        try {
            JSONObject j = new JSONObject();
            j.put("t", 5);
            j.put("data", tmcJson);
            tx(j);
        } catch (JSONException ignored) {
        }
    }

    private void txExit(String exitName, String exitDirection) {
        try {
            JSONObject j = new JSONObject();
            j.put("t", 6);
            j.put("exitName", exitName);
            j.put("exitDirection", exitDirection);
            tx(j);
        } catch (JSONException ignored) {
        }
    }

    private void txSapa(String sapaName, String sapaDist, int sapaType,
                         String nextSapaName, String nextSapaDist, int nextSapaType) {
        try {
            JSONObject j = new JSONObject();
            j.put("t", 7);
            j.put("sapaName", sapaName);
            j.put("sapaDist", sapaDist);
            j.put("sapaType", sapaType);
            j.put("nextSapaName", nextSapaName);
            j.put("nextSapaDist", nextSapaDist);
            j.put("nextSapaType", nextSapaType);
            tx(j);
        } catch (JSONException ignored) {
        }
    }

    private void txDayNight(boolean isNight) {
        try {
            JSONObject j = new JSONObject();
            j.put("t", 8);
            j.put("night", isNight);
            tx(j);
        } catch (JSONException ignored) {
        }
    }

    private void txCrossMap(int has) {
        try {
            JSONObject j = new JSONObject();
            j.put("t", 9);
            j.put("has", has);
            tx(j);
        } catch (JSONException ignored) {
        }
    }

    // ======================== 巡航设施坐标(高德注入广播) ========================

    private void handleCruiseFacility(Context context, Intent intent) {
        double lng = pickDouble(intent, "cameraLng", "lng", "longitude", "lon");
        double lat = pickDouble(intent, "cameraLat", "lat", "latitude");
        int type = pickInt(intent, "cameraType", "type");
        int limitSpeed = pickInt(intent, "cameraLimitSpeed", "limitSpeed", "speed");

        // 诊断：当坐标全为 0 时打印全部 extra，便于确认高德注入的 key 命名是否对得上
        if (lng == 0 && lat == 0) {
            Bundle ex = intent.getExtras();
            if (ex != null) {
                for (String k : ex.keySet()) {
                    Log.d(TAG, "CRUISE_FACILITY 疑似 key 不匹配 key=" + k + " val=" + ex.get(k));
                }
            }
        }

        // 车机自身 GPS 位置
        double devLat = 0, devLng = 0;
        boolean hasGps = false;
        try {
            android.location.Location dev = DeviceLocation.get(context).getLast();
            if (dev != null) {
                devLat = dev.getLatitude();
                devLng = dev.getLongitude();
                hasGps = true;
            }
        } catch (Exception ignored) {
        }

        // 用「车机位置 → 灯位置」真实距离（haversine），而非高德给的距离
        int distMeters = -1;
        if (hasGps && (lng != 0 || lat != 0)) {
            try {
                distMeters = (int) Math.round(
                        DeviceLocation.haversine(devLat, devLng, lat, lng));
            } catch (Exception ignored) {
            }
        }

        Log.d(TAG, "CRUISE_FACILITY 灯=(" + String.format(java.util.Locale.US, "%.6f", lat)
                + "," + String.format(java.util.Locale.US, "%.6f", lng) + ") 车=("
                + String.format(java.util.Locale.US, "%.6f", devLat) + ","
                + String.format(java.util.Locale.US, "%.6f", devLng) + ") 距离="
                + distMeters + "m");

        CruiseFacilityOverlay.getInstance(context.getApplicationContext())
                .show(lng, lat, devLat, devLng, distMeters, type, limitSpeed);
    }

    /** 依次尝试多个候选 key，返回第一个存在的值；都没有则返回 0 */
    private double pickDouble(Intent intent, String... keys) {
        Bundle b = intent.getExtras();
        if (b != null) {
            for (String k : keys) {
                if (b.containsKey(k)) {
                    return b.getDouble(k, 0);
                }
            }
        }
        return 0;
    }

    private int pickInt(Intent intent, String... keys) {
        Bundle b = intent.getExtras();
        if (b != null) {
            for (String k : keys) {
                if (b.containsKey(k)) {
                    return b.getInt(k, 0);
                }
            }
        }
        return 0;
    }
}
