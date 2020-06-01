package com.dji.sdk.sample.internal.controller.ARASController.Handlers;

import android.util.Log;
import com.dji.sdk.sample.internal.controller.ARASMessageManager;
import com.dji.sdk.sample.internal.utils.ModuleVerificationUtil;
import com.koushikdutta.async.AsyncSocket;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import dji.common.error.DJIError;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointExecutionProgress;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionGotoWaypointMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionState;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.mission.waypoint.WaypointTurnMode;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;


public class ARASWaypointMissionOperatorHandler {

    private static ARASWaypointMissionOperatorHandler SINGLE_INSTANCE = null;

    private ARASWaypointMissionOperatorHandler() { }
    private WaypointMissionOperatorListener listener;
    private boolean going_home_execution = false;

    public static ARASWaypointMissionOperatorHandler getInstance() {
        if (SINGLE_INSTANCE == null) {
            synchronized(ARASWaypointMissionOperatorHandler.class) {
                SINGLE_INSTANCE = new ARASWaypointMissionOperatorHandler();
                SINGLE_INSTANCE.setUpListener();
            }
        }
        return SINGLE_INSTANCE;
    }

    private static Waypoint InitWaypoint(double latitude, double longitude, float altitude, float gimbalPitch, float speed, int stayTimeSeconds, int rotation, int orientation)
    {
        Waypoint waypoint = new Waypoint(latitude, longitude, altitude);
        waypoint.gimbalPitch = gimbalPitch;
        waypoint.turnMode = WaypointTurnMode.CLOCKWISE;
        waypoint.heading = orientation;
        waypoint.actionRepeatTimes = 1;
        waypoint.actionTimeoutInSeconds = 60;
        waypoint.cornerRadiusInMeters = 0.2F;
        waypoint.speed = speed;
        //waypoint.addAction(new WaypointAction(WaypointActionType.ROTATE_AIRCRAFT, rotation));
        //waypoint.addAction(new WaypointAction(WaypointActionType.STAY, stayTimeSeconds*1000));
        return waypoint;
    }


    public void ExecuteMission(String json_mission, final AsyncSocket clientStream)
    {
        // Configuraciones de seguridad al inicio de una mision
        // TODO: validar los callbacks
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = ModuleVerificationUtil.getFlightController();
            flightController.setLowBatteryWarningThreshold(16, null);
            flightController.setSeriousLowBatteryWarningThreshold(11, null);
            flightController.setSmartReturnToHomeEnabled(true, null);
        }


        try {
            JSONObject missionData = new JSONObject(json_mission);
            String landingType = (String) missionData.get("tipo_aterrizaje");
            JSONArray points = missionData.getJSONArray("puntos_mision");

            JSONObject safePoints = (JSONObject) missionData.get("puntos_salida_segura");
            JSONObject wp_safe_level = (JSONObject) safePoints.get("wp_nivel_seguro");

            //wp_safe_level
            float wp_safe_level_latitude = BigDecimal.valueOf(wp_safe_level.getDouble("latitud")).floatValue();
            float wp_safe_level_longitude = BigDecimal.valueOf(wp_safe_level.getDouble("longitud")).floatValue();
            float wp_safe_level_altitude = BigDecimal.valueOf(wp_safe_level.getDouble("altitud")).floatValue();

            int waypointCount = points.length();
            float maxFlightSpeed = 15F; // meters per second TODO
            float autoFlightSpeed = 10F; // meters per second TODO
            int missionID = 0; //TODO

            List<Waypoint> waypoints = new ArrayList<>();

            float last_latitude = 0.0F, last_longitude = 0.0F, last_altitude = 0.0F;
            for (int i = 0; i < points.length(); i++) {
                JSONObject point = points.getJSONObject(i);
                float latitude = BigDecimal.valueOf(point.getDouble("latitud")).floatValue();
                float longitude = BigDecimal.valueOf(point.getDouble("longitud")).floatValue();
                float gimbalPitch = BigDecimal.valueOf(point.getDouble("angulo")).floatValue();
                float speed = BigDecimal.valueOf(point.getDouble("velocidad")).floatValue(); // meters per second
                float altitude = BigDecimal.valueOf(point.getDouble("altitud")).floatValue();
                int stayTime = (int) point.get("tiempo");
                int rotation = (int) point.get("rotacion");
                int orientation = (int) point.get("orientacion");
                last_latitude = latitude;
                last_longitude = longitude;
                last_altitude = altitude;
                waypoints.add(InitWaypoint(latitude, longitude, altitude, gimbalPitch, speed, stayTime, rotation, orientation));
            }


            // TODO use class to retrieve constants
            if (landingType.equals("USE_PL_ARAS")) {
                float goOverFirstPointSpeed = 8F;
                float returnToHomeGimbalAngle = -90F;

                // get up from last waypoint
                if(Math.abs(last_altitude - wp_safe_level_altitude) > 1.0){
                    waypoints.add(InitWaypoint(last_latitude, last_longitude, wp_safe_level_altitude, returnToHomeGimbalAngle, goOverFirstPointSpeed, 0, 180, 180));
                    waypointCount += 1; //additional waypoints
                }

                // go to safe point next to base
                waypoints.add(InitWaypoint(wp_safe_level_latitude, wp_safe_level_longitude, wp_safe_level_altitude, returnToHomeGimbalAngle, goOverFirstPointSpeed, 0, 180, 180));
                waypointCount += 1; //additional waypoints

            }

            WaypointMission.Builder builder = new WaypointMission.Builder();
            builder.waypointCount(waypointCount);
            builder.maxFlightSpeed(maxFlightSpeed);
            builder.autoFlightSpeed(autoFlightSpeed);
            builder.waypointList(waypoints);
            builder.setMissionID(missionID);
            builder.repeatTimes(1);
            builder.setGimbalPitchRotationEnabled(true);
            builder.setExitMissionOnRCSignalLostEnabled(false);
            builder.gotoFirstWaypointMode(WaypointMissionGotoWaypointMode.SAFELY);
            builder.flightPathMode(WaypointMissionFlightPathMode.NORMAL);
            builder.headingMode(WaypointMissionHeadingMode.AUTO);
            builder.finishedAction(landingType.equals("USE_PL_ARAS") ? WaypointMissionFinishedAction.NO_ACTION : WaypointMissionFinishedAction.GO_HOME);

            WaypointMission mission = new WaypointMission(builder);

            //load mission
            final WaypointMissionOperator waypointMissionOperator = MissionControl.getInstance().getWaypointMissionOperator();
            DJIError loadError = waypointMissionOperator.loadMission(mission);
            int attempts_load = 5;
            while(loadError != null){
                //load mission
                loadError = waypointMissionOperator.loadMission(mission);

                attempts_load = attempts_load - 1;
                if(attempts_load <= 0){
                    break;
                }
                Thread.sleep(500);

            }

            WaypointMissionState currentWaypointMissionState = ARASWaypointMissionOperatorHandler.getInstance().getMissionState();

            if(loadError == null && currentWaypointMissionState.equals(WaypointMissionState.READY_TO_UPLOAD)){
                waypointMissionOperator.uploadMission(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError resultCode) {
                        WaypointMissionState currentWaypointMissionState = ARASWaypointMissionOperatorHandler.getInstance().getMissionState();

                        while(currentWaypointMissionState.equals(WaypointMissionState.UPLOADING)){
                            currentWaypointMissionState = ARASWaypointMissionOperatorHandler.getInstance().getMissionState();
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        if(resultCode == null && currentWaypointMissionState.equals(WaypointMissionState.READY_TO_EXECUTE)){
                            waypointMissionOperator.startMission(new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError resultCode) {
                                    ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(resultCode == null, resultCode == null ? "NO_ERROR" : resultCode.getDescription(), null));
                                }
                            });
                        }else{
                            waypointMissionOperator.retryUploadMission(new CommonCallbacks.CompletionCallback() {

                                @Override
                                public void onResult(DJIError djiError) {
                                    WaypointMissionState currentWaypointMissionState = ARASWaypointMissionOperatorHandler.getInstance().getMissionState();

                                    while(currentWaypointMissionState.equals(WaypointMissionState.UPLOADING)) {
                                        currentWaypointMissionState = ARASWaypointMissionOperatorHandler.getInstance().getMissionState();
                                        try {
                                            Thread.sleep(500);
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }

                                    if(resultCode == null && currentWaypointMissionState.equals(WaypointMissionState.READY_TO_EXECUTE)){
                                        waypointMissionOperator.startMission(new CommonCallbacks.CompletionCallback() {
                                            @Override
                                            public void onResult(DJIError resultCode) {
                                                ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(resultCode == null, resultCode == null ? "NO_ERROR" : resultCode.getDescription(), null));
                                            }
                                        });
                                    }else{
                                        ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(false, resultCode.getDescription(), null));
                                    }
                                }
                            });

                        }
                    }
                });
            }else{
                ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(false, loadError.getDescription(), null));
            }

        }catch (JSONException e)
        {
            Log.e("JSON_PARSE_ERROR","CRASH StackTrace: "+ e.getStackTrace().toString());
            ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(false, DJIError.COMMON_PARAM_INVALID.getDescription(), null));
        } catch (InterruptedException e) {
            e.printStackTrace();
            ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(false, DJIError.COMMON_SYSTEM_BUSY.getDescription(), null));

        }

    }

    public void uploadMission(final AsyncSocket clientStream){
        WaypointMissionOperator waypointMissionOperator = MissionControl.getInstance().getWaypointMissionOperator();
        if (WaypointMissionState.READY_TO_RETRY_UPLOAD.equals(waypointMissionOperator.getCurrentState())
                || WaypointMissionState.READY_TO_UPLOAD.equals(waypointMissionOperator.getCurrentState())) {
            waypointMissionOperator.uploadMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError resultCode) {
                    ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(resultCode == null, resultCode == null ? "NO_ERROR" : resultCode.getDescription(), null));
                }
            });
        }
    }

    public void startMission(final AsyncSocket clientStream) {
        WaypointMissionOperator waypointMissionOperator = MissionControl.getInstance().getWaypointMissionOperator();
        if (WaypointMissionState.READY_TO_EXECUTE.equals(waypointMissionOperator.getCurrentState())) {
            waypointMissionOperator.startMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError resultCode) {
                    ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(resultCode == null, resultCode == null ? "NO_ERROR" : resultCode.toString(), null));
                }
            });
        }
    }

    public WaypointMissionState getMissionState(){
        WaypointMissionOperator waypointMissionOperator = MissionControl.getInstance().getWaypointMissionOperator();
        return waypointMissionOperator.getCurrentState();
    }


    public void resumeMission() {
        WaypointMissionOperator waypointMissionOperator = MissionControl.getInstance().getWaypointMissionOperator();
        if (WaypointMissionState.EXECUTION_PAUSED.equals(waypointMissionOperator.getCurrentState())) {
            waypointMissionOperator.resumeMission(null);
        }
    }

    public void pauseMission() {
        WaypointMissionOperator waypointMissionOperator = MissionControl.getInstance().getWaypointMissionOperator();
        if (WaypointMissionState.EXECUTING.equals(waypointMissionOperator.getCurrentState())) {
            waypointMissionOperator.pauseMission(null);
        }
    }

    public void stopMission(final float latitude, final float longitude, final float altitude) {
        WaypointMissionOperator waypointMissionOperator = MissionControl.getInstance().getWaypointMissionOperator();
        if (WaypointMissionState.EXECUTING.equals(waypointMissionOperator.getCurrentState())) {
            waypointMissionOperator.stopMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError resultCode) {
                    ARASMissionControlHandler.getInstance().goToLocationAction(latitude, longitude, altitude);
                }
            });
        }
    }

    private void setUpListener() {
        // Example of Listener
        listener = new WaypointMissionOperatorListener() {
            @Override
            public void onDownloadUpdate(WaypointMissionDownloadEvent waypointMissionDownloadEvent) {
                // Example of Download Listener
            }

            @Override
            public void onUploadUpdate(WaypointMissionUploadEvent waypointMissionUploadEvent) {
                // Example of Upload Listener
            }

            @Override
            public void onExecutionUpdate(WaypointMissionExecutionEvent waypointMissionExecutionEvent) {
                // Example of Execution Listener
                WaypointExecutionProgress progress = waypointMissionExecutionEvent.getProgress();
                // proximo a casa
                if(progress.targetWaypointIndex >= progress.totalWaypointCount - 1){
                    ARASWaypointMissionOperatorHandler.getInstance().going_home_execution = true;
                }else{
                    ARASWaypointMissionOperatorHandler.getInstance().going_home_execution = false;
                }
            }

            @Override
            public void onExecutionStart() {

            }

            @Override
            public void onExecutionFinish(DJIError djiError) {
                ARASWaypointMissionOperatorHandler.getInstance().going_home_execution = false;
            }
        };

        WaypointMissionOperator waypointMissionOperator = MissionControl.getInstance().getWaypointMissionOperator();

        if (waypointMissionOperator != null && listener != null) {
            // Example of adding listeners
            waypointMissionOperator.addListener(listener);
        }
    }


    public boolean getGoingHomeExecution() {
        return ARASWaypointMissionOperatorHandler.getInstance().going_home_execution;
    }
}