package com.dji.sdk.sample.internal.controller.ARASController.Handlers;

import android.util.Log;

import com.dji.sdk.sample.internal.controller.ARASMessageManager;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.utils.ModuleVerificationUtil;
import com.google.gson.JsonObject;
import com.koushikdutta.async.AsyncSocket;

import org.json.JSONException;
import org.json.JSONObject;

import dji.common.error.DJIError;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.gimbal.Attitude;
import dji.common.remotecontroller.AircraftStickMappingTarget;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.FlightAssistant;
import dji.sdk.flightcontroller.FlightController;


public class ARASVirtualRemoteController {
    private static ARASVirtualRemoteController SINGLE_INSTANCE = null;
    private Attitude gimbalAttitude = null;

    public class GimbalNotAvailableException extends Exception
    {
        public GimbalNotAvailableException(String message)
        {
            super(message);
        }
    }

    private ARASVirtualRemoteController() {
    }

    public static ARASVirtualRemoteController getInstance() {
        if (SINGLE_INSTANCE == null) {
            synchronized(ARASFlightControlHandler.class) {
                SINGLE_INSTANCE = new ARASVirtualRemoteController();
            }
        }
        return SINGLE_INSTANCE;
    }


    public void sendQuiet(){
        try{
            ARASFlightControlHandler.getInstance().sendCommandVirtualStick(0.0F, 0.0F, 0.0F, 0.0F, "VELOCITY_MODE", null);
        }catch(ARASFlightControlHandler.FlightControllerNotAvailableException e){
            Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
        }

    }

    public void updateJoystickValue(float pitch, float roll, float yaw, float throttle, String throttle_mode, AsyncSocket clientStream){
        boolean validPitch = pitch >=-15.0 && pitch<=15.0;
        boolean validYaw = yaw >=-100.0 && yaw<=100.0;
        boolean validRoll = roll >=-15.0 && roll<=15.0;
        boolean validThrottle = throttle >=-500 && throttle<=500; // position mode, FIX LATER


        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            // validacion throttle_mode
            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            VerticalControlMode currentVerticalControlMode = flightController.getVerticalControlMode();

            if((currentVerticalControlMode.equals(VerticalControlMode.POSITION) && throttle_mode.equals("POSITION_MODE")) || (currentVerticalControlMode.equals(VerticalControlMode.VELOCITY) && throttle_mode.equals("VELOCITY_MODE"))){
                // ok
            }else{
                JSONObject jsonObject = null;
                try {
                    jsonObject = new JSONObject();
                    jsonObject.put("received_throttle_mode", String.valueOf(throttle_mode));
                    jsonObject.put("current_vertical_control_mode", String.valueOf(currentVerticalControlMode));

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(false, "INVALID_THROTTLE_MODE", jsonObject));
                return;
            }

            if (validPitch && validYaw && validRoll && validThrottle) {
                try {
                    ARASFlightControlHandler.getInstance().sendCommandVirtualStick(pitch, roll, yaw, throttle, throttle_mode, clientStream);
                } catch (ARASFlightControlHandler.FlightControllerNotAvailableException e) {
                    Log.e("NO_HANDLER_AVAILBLE", "CRASH StackTrace: " + e.getStackTrace().toString());
                }

            } else {
                if (clientStream != null) {
                    DJIError resultCode = null;
                    resultCode = DJIError.COMMON_PARAM_INVALID;
                    try {
                        ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(resultCode == null, resultCode == null ? "NO_ERROR" : resultCode.toString(), null));
                    } catch (Exception e) {
                        //fix
                    }
                }
            }

        }
    }

    // Move aircraft for needed_time seconds at speed_cms velocity
    public void moveAircraft(AircraftStickMappingTarget axis, float needed_time, float speed_cms, float sign) throws ARASFlightControlHandler.FlightControllerNotAvailableException {
        ARASFlightControlHandler.getInstance().enablePitchRollVelocityMode();
        ARASFlightControlHandler.getInstance().enableThrottleVelocityMode();

        final float nanoSecToSec = 1.0F/1000000000.0F;
        final float cmsToMts = 1.0F/100.0F;
        final float freq_send = 25F; // hz

        float time_0 = System.nanoTime() * nanoSecToSec;
        long time_millis= System.currentTimeMillis();
        long end = time_millis + (long)(needed_time*1000);
        while(true){
            if (needed_time == 0) {
                break;
            }
            float now = System.nanoTime() * nanoSecToSec;
            if(now >= time_0 + 1.0 / freq_send){

                time_0 = now;

                if(axis == AircraftStickMappingTarget.PITCH){
                    ARASFlightControlHandler.getInstance().sendCommandVirtualStick(sign * speed_cms * cmsToMts, 0.0F, 0.0F, 0.0F, "VELOCITY_MODE", null);
                }else if(axis == AircraftStickMappingTarget.ROLL){
                    ARASFlightControlHandler.getInstance().sendCommandVirtualStick(0.0F, sign * speed_cms * cmsToMts, 0.0F, 0.0F, "VELOCITY_MODE", null);
                }else if(axis == AircraftStickMappingTarget.THROTTLE){
                    ARASFlightControlHandler.getInstance().sendCommandVirtualStick(0.0F, 0.0F, 0.0F, sign * speed_cms * cmsToMts, "VELOCITY_MODE", null);
                }

                if(System.currentTimeMillis() >= end){
                    break;
                }

            }
        }
    }

    public void moveDistance(final float pitch_cm, final float roll_cm, final float throttle_cm, final float speed_cms, final String axis,final int attempts_left, final AsyncSocket clientStream) throws ARASFlightControlHandler.FlightControllerNotAvailableException {

        if(attempts_left <= 0){
            ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(false, "MAX_FAILED_ATTEMPTS", null));
            return;
        }

        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = ModuleVerificationUtil.getFlightController();
            flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
            flightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
            flightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
            flightController.setVerticalControlMode(VerticalControlMode.VELOCITY);

            FlightAssistant flight_assistant = flightController.getFlightAssistant();

            // desactivamos la sensorica para que no evite obstaculos
            flight_assistant.setCollisionAvoidanceEnabled(false, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {

                    if(djiError == null){
                        // activamos el virtual stick
                        flightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError resultCode) {
                                if(djiError == null){
                                    float needed_time_pitch = Math.abs(pitch_cm  /  speed_cms); // seconds
                                    float needed_time_roll = Math.abs(roll_cm  /  speed_cms); // seconds
                                    float needed_time_throttle = Math.abs(throttle_cm  /  speed_cms); // seconds

                                    float pitch_sign = pitch_cm < 0 ? -1 : 1;
                                    float roll_sign = roll_cm < 0 ? -1 : 1;
                                    float throttle_sign = throttle_cm < 0 ? -1 : 1;

                                    try {

                                        // INICIO MOVIMIENTOS
                                        if(axis.equals("PITCH")){
                                            moveAircraft(AircraftStickMappingTarget.PITCH, needed_time_pitch, speed_cms, pitch_sign);
                                            moveAircraft(AircraftStickMappingTarget.ROLL, needed_time_roll, speed_cms, roll_sign);
                                        }else if(axis.equals("ROLL")){
                                            moveAircraft(AircraftStickMappingTarget.ROLL, needed_time_roll, speed_cms, roll_sign);
                                            moveAircraft(AircraftStickMappingTarget.PITCH, needed_time_pitch, speed_cms, pitch_sign);
                                        }
                                        moveAircraft(AircraftStickMappingTarget.THROTTLE, needed_time_throttle, speed_cms, throttle_sign);
                                        // FIN MOVIMIENTOS

                                        // desactivamos el virtual stick y volvemos a activar el evitar obstaculos
                                        ARASFlightControlHandler.getInstance().setVirtualStickModeEnabled(false, "VELOCITY_MODE", 5, null);
                                        //TODO: activar evitar obstaculos


                                        ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(true, "NO_ERROR", null));
                                    } catch (ARASFlightControlHandler.FlightControllerNotAvailableException e) {
                                        e.printStackTrace();
                                        ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(false, "NO_FLIGHT_CONTROLLER", null));
                                    }

                                }else{
                                    try {
                                        ARASVirtualRemoteController.getInstance().moveDistance(pitch_cm, roll_cm, throttle_cm, speed_cms, axis, attempts_left - 1, clientStream);
                                    } catch (ARASFlightControlHandler.FlightControllerNotAvailableException e) {
                                        e.printStackTrace();
                                        ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(false, "NO_FLIGHT_CONTROLLER", null));
                                    }
                                }

                            }
                        });
                    }else{
                        try {
                            ARASVirtualRemoteController.getInstance().moveDistance(pitch_cm, roll_cm, throttle_cm, speed_cms, axis, attempts_left - 1, clientStream);
                        } catch (ARASFlightControlHandler.FlightControllerNotAvailableException e) {
                            e.printStackTrace();
                            ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(false, "NO_FLIGHT_CONTROLLER", null));
                        }
                    }
                }
            });
        }else{
            ARASVirtualRemoteController.getInstance().moveDistance(pitch_cm, roll_cm, throttle_cm, speed_cms, axis, attempts_left - 1, clientStream);
        }
    }
}
