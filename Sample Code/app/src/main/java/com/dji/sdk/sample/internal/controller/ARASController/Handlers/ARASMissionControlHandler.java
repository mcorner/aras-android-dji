package com.dji.sdk.sample.internal.controller.ARASController.Handlers;

import com.dji.sdk.sample.internal.controller.ARASMessageManager;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.koushikdutta.async.AsyncSocket;
import dji.common.error.DJIError;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.gimbal.Attitude;
import dji.common.model.LocationCoordinate2D;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.FlightAssistant;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.timeline.actions.AircraftYawAction;
import dji.sdk.mission.timeline.actions.GimbalAttitudeAction;
import dji.sdk.mission.timeline.actions.GoHomeAction;
import dji.sdk.mission.timeline.actions.GoToAction;
import dji.sdk.mission.timeline.actions.LandAction;
import dji.sdk.mission.timeline.actions.TakeOffAction;
import dji.sdk.products.Aircraft;


public class ARASMissionControlHandler {
    private static ARASMissionControlHandler SINGLE_INSTANCE = null;

    private ARASMissionControlHandler() { }

    public static ARASMissionControlHandler getInstance() {
        if (SINGLE_INSTANCE == null) {
            synchronized(ARASFlightControlHandler.class) {
                SINGLE_INSTANCE = new ARASMissionControlHandler();
            }
        }
        return SINGLE_INSTANCE;
    }

    public DJIError goToLocationAction(float latitude, float longitude, float altitude){
        MissionControl missionControl = MissionControl.getInstance();
        if(missionControl != null && missionControl.isTimelineRunning()){
            missionControl.stopTimeline();
        }

        if (missionControl != null && missionControl.scheduledCount() > 0) {
            missionControl.unscheduleEverything();
            missionControl.removeAllListeners();
        }
        GoToAction action = new GoToAction(new LocationCoordinate2D(latitude, longitude), altitude){};
        action.run();
        return action.checkValidity();
    }

    public void setGimbalAttitudeAction(final float pitch, final float roll, final float yaw){
        //DJISampleApplication.getProductInstance().getGimbal().reset(new CommonCallbacks.CompletionCallback() {
        //    @Override
        //    public void onResult(DJIError resultCode) {
        //
        //    }
        //});
        MissionControl missionControl = MissionControl.getInstance();
        if(missionControl != null && missionControl.isTimelineRunning()){
            missionControl.stopTimeline();
        }

        if (missionControl != null && missionControl.scheduledCount() > 0) {
            missionControl.unscheduleEverything();
            missionControl.removeAllListeners();
        }

        missionControl.scheduleElement(new GimbalAttitudeAction(new Attitude(pitch, roll, yaw)));
        missionControl.startTimeline();

    }

    public DJIError makeTakeOffAction(){
        MissionControl missionControl = MissionControl.getInstance();
        if(missionControl != null && missionControl.isTimelineRunning()){
            missionControl.stopTimeline();
        }

        if (missionControl != null && missionControl.scheduledCount() > 0) {
            missionControl.unscheduleEverything();
            missionControl.removeAllListeners();
        }
        TakeOffAction takeOffAction = new TakeOffAction();
        takeOffAction.run();
        return takeOffAction.checkValidity();
    }

    public DJIError makeLandingAction(){
        // Cuando se hace landing, verificar si es seguro, detener landing si es necesario y bajar el gimbal nuevamente
        ARASFlightControlHandler.getInstance().setVisionLandingCallback();

        MissionControl missionControl = MissionControl.getInstance();
        if(missionControl != null && missionControl.isTimelineRunning()){
            missionControl.stopTimeline();
        }

        if (missionControl != null && missionControl.scheduledCount() > 0) {
            missionControl.unscheduleEverything();
            missionControl.removeAllListeners();
        }
        LandAction landAction = new LandAction();
        landAction.setAutoConfirmLandingEnabled(true);
        landAction.run();
        return landAction.checkValidity();
    }

    public DJIError goToLocationActionDebug(float altitude) {
        MissionControl missionControl = MissionControl.getInstance();

        if(missionControl != null && missionControl.isTimelineRunning()){
            missionControl.stopTimeline();
        }

        if (missionControl != null && missionControl.scheduledCount() > 0) {
            missionControl.unscheduleEverything();
            missionControl.removeAllListeners();
        }
        GoToAction action = null;
        action = new GoToAction(altitude);
        action.run();
        return action.checkValidity();

    }

    public DJIError setYawAction(float angle, final AsyncSocket clientStream) {
        MissionControl missionControl = MissionControl.getInstance();
        if(missionControl != null && missionControl.isTimelineRunning()){
            missionControl.stopTimeline();
        }

        if (missionControl != null && missionControl.scheduledCount() > 0) {
            missionControl.unscheduleEverything();
            missionControl.removeAllListeners();
        }
        AircraftYawAction action = new AircraftYawAction(angle, false){
            @Override
            public void didRun(){
                try {
                    ARASFlightControlHandler.getInstance().setVirtualStickModeEnabled(true, "VELOCITY_MODE", 5,null);
                } catch (ARASFlightControlHandler.FlightControllerNotAvailableException e) {
                    e.printStackTrace();
                }
                ARASGimbalHander.getInstance().rotate(-90, 0, 0);
                ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(true, "NO_ERROR", null));

            }
        };
        action.run();
        return action.checkValidity();
    }

    public boolean actionRunning() {
        MissionControl missionControl = MissionControl.getInstance();
        if(missionControl == null){
            return false;
        }else{
            return missionControl.isTimelineRunning();
        }
    }

    public void getOutBase(final float latitude, final float longitude, final float altitude) {
        FlightController flightController = ((Aircraft) DJISampleApplication.getProductInstance()).getFlightController();
        FlightAssistant flight_assistant = flightController.getFlightAssistant();
        flight_assistant.setActiveObstacleAvoidanceEnabled(true, null);
        flight_assistant.setLandingProtectionEnabled(true, null);
        flight_assistant.setCollisionAvoidanceEnabled(true, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if(djiError == null) {

                    MissionControl missionControl = MissionControl.getInstance();
                    if (missionControl != null && missionControl.isTimelineRunning()) {
                        missionControl.stopTimeline();
                    }

                    if (missionControl != null && missionControl.scheduledCount() > 0) {
                        missionControl.unscheduleEverything();
                        missionControl.removeAllListeners();
                    }
                    GoToAction get_out_action = new GoToAction(new LocationCoordinate2D(latitude, longitude), altitude) {
                    };
                    get_out_action.setFlightSpeed(1); // 1 mts

                    GoHomeAction go_home = new GoHomeAction() {
                    };

                    missionControl.scheduleElement(get_out_action);
                    missionControl.scheduleElement(go_home);

                    missionControl.startTimeline();
                }
            }
        });

    }

    public void goToLocationActionSafe(final float latitude, final float longitude, final float altitude_down, final AsyncSocket clientStream) {
        FlightController flightController = ((Aircraft) DJISampleApplication.getProductInstance()).getFlightController();
        flightController.getGoHomeHeightInMeters(new CommonCallbacks.CompletionCallbackWith<Integer>() {
            @Override
            public void onSuccess(Integer altitude_home) {
                GoToAction get_point_with_altitude = new GoToAction(new LocationCoordinate2D(latitude, longitude),  altitude_home){};
                GoToAction get_down = new GoToAction(new LocationCoordinate2D(latitude, longitude), altitude_down){};

                get_point_with_altitude.run();

                // wait for running
                while(get_point_with_altitude.isRunning()){
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }


                // wait for running
                boolean did_run = false;
                while(did_run == false){

                    get_down.run();

                    while(get_down.isRunning()){
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    LocationCoordinate3D currentAircraftLocation = null;
                    try {
                        currentAircraftLocation = ARASFlightControlHandler.getInstance().getAircraftLocation();
                        float altitude = currentAircraftLocation.getAltitude();
                        if(Math.abs(altitude - altitude_down) < 5){
                            did_run = true;
                        }
                    } catch (ARASFlightControlHandler.FlightControllerNotAvailableException e) {
                        e.printStackTrace();
                    }

                }


                /* final MissionControl missionControl = MissionControl.getInstance();
                if(missionControl != null && missionControl.isTimelineRunning()){
                    missionControl.stopTimeline();
                }

                if (missionControl != null && missionControl.scheduledCount() > 0) {
                    missionControl.unscheduleEverything();
                    missionControl.removeAllListeners();
                }
                missionControl.scheduleElement(get_point_with_altitude);
                missionControl.scheduleElement(get_down);
                missionControl.startTimeline();

                while(missionControl.scheduledCount() != 0){
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }*/

                ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(true, "NO_ERROR", null));
            }

            @Override
            public void onFailure(DJIError djiError) {
                goToLocationActionSafe(latitude, longitude, altitude_down, clientStream); // TODO: count failure and stop
            }
        });
    }
}
