package com.dji.sdk.sample.internal.controller.ARASController.Handlers;

import android.util.Log;

import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.utils.ModuleVerificationUtil;
import com.dji.sdk.sample.internal.controller.ARASMessageManager;

import com.koushikdutta.async.AsyncSocket;

import org.json.JSONException;
import org.json.JSONObject;

import dji.common.error.DJIError;
 import dji.common.flightcontroller.Attitude;
import dji.common.flightcontroller.FlightMode;
import dji.common.flightcontroller.GPSSignalLevel;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.flightcontroller.VisionControlState;
import dji.common.flightcontroller.VisionLandingProtectionState;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.FlightAssistant;
import dji.sdk.flightcontroller.FlightController;


public class ARASFlightControlHandler {
    private static ARASFlightControlHandler SINGLE_INSTANCE = null;
    private VisionControlState visionControlState = null;

    public class FlightControllerNotAvailableException extends Exception
    {
        public FlightControllerNotAvailableException(String message)
        {
            super(message);
        }
    }

    private ARASFlightControlHandler() { }

    public static ARASFlightControlHandler getInstance() {
        if (SINGLE_INSTANCE == null) {
            synchronized(ARASFlightControlHandler.class) {
                ARASGimbalHander.getInstance().setStrength();
                ARASGimbalHander.getInstance().lockAxis();
                SINGLE_INSTANCE = new ARASFlightControlHandler();
            }
        }
        return SINGLE_INSTANCE;
    }

    public LocationCoordinate3D getAircraftLocation() throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {

            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            return flightController.getState().getAircraftLocation();

        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    public Attitude getAircraftAttitude() throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {

            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            return flightController.getState().getAttitude();

        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    public GPSSignalLevel getAircraftGPSSignalLevel() throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {

            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            return flightController.getState().getGPSSignalLevel();

        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    public void startGoHome(int altitude, final AsyncSocket clientStream) throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {

            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            flightController.setGoHomeHeightInMeters(altitude, null);
            flightController.startGoHome(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError resultCode) {
                    ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(resultCode == null, resultCode == null ? "NO_ERROR" : resultCode.toString(), null));
                }
            });

        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    public void startLanding(final AsyncSocket clientStream) throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {

            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            flightController.startLanding(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError resultCode) {
                    ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(resultCode == null, resultCode == null ? "NO_ERROR" : resultCode.toString(), null));
                }
            });

        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    public void startTakeOff(final AsyncSocket clientStream) throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            flightController.startTakeoff(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError resultCode) {
                    ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(resultCode == null, resultCode == null ? "NO_ERROR" : resultCode.toString(), null));
                }
            });

        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }


    public void setPrecisionLandingEnabled(boolean value, final AsyncSocket clientStream) throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {

            FlightAssistant flightAssistant = DJISampleApplication.getAircraftInstance().getFlightController().getFlightAssistant();
            flightAssistant.setPrecisionLandingEnabled(value, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError resultCode) {
                    ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(resultCode == null, resultCode == null ? "NO_ERROR" : resultCode.toString(), null));
                }
            });

        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    public void setVirtualStickModeEnabled(boolean value, String throttle_mode, final int attempts_left, final AsyncSocket clientStream) throws FlightControllerNotAvailableException {
        if(attempts_left <= 0){
            ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(false,  "MAX_FAILED_ATTEMPTS", null));
            return;
        }
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            flightController.setVirtualStickModeEnabled(value, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError resultCode) {
                    if(resultCode == null){

                        // precondiciones dependiendo del tipo de movimiento
                        FlightController flightController = ModuleVerificationUtil.getFlightController();
                        flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
                        flightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
                        flightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);

                        if (throttle_mode.equals("POSITION_MODE")) {
                            flightController.setVerticalControlMode(VerticalControlMode.POSITION);
                        } else if (throttle_mode.equals("VELOCITY_MODE")) {
                            flightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
                        }

                        if(clientStream != null){
                            JSONObject jsonObject = new JSONObject();
                            try {
                                jsonObject.put("setVirtualStickModeEnabled", String.valueOf(value));
                                jsonObject.put("left", String.valueOf(attempts_left));
                                jsonObject.put("received_throttle_mode", throttle_mode);

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(resultCode == null, resultCode == null ? "NO_ERROR" : resultCode.toString(), jsonObject));
                        }
                    }else{
                        try {
                            ARASFlightControlHandler.getInstance().setVirtualStickModeEnabled(value, throttle_mode, attempts_left - 1,  clientStream);
                        } catch (FlightControllerNotAvailableException e) {
                            if(clientStream != null){
                                ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(false,  "NO_FLIGHT_CONTROLLER", null));
                            }
                            e.printStackTrace();
                        }
                    }

                }
            });

        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    public void sendCommandVirtualStick(final float pitch, final float roll, final float yaw, final float throttle, String throttle_mode, final AsyncSocket clientStream) throws FlightControllerNotAvailableException{
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            flightController.sendVirtualStickFlightControlData(new FlightControlData(roll, pitch, yaw, throttle),
            new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if(clientStream!=null){
                        ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(djiError == null, djiError == null ? "NO_ERROR" : djiError.toString(), null));
                    }

                    if(djiError != null){
                        try{
                            setVirtualStickModeEnabled(true, throttle_mode,5,null);
                        }catch(FlightControllerNotAvailableException e){
                            Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                        }

                    }
                }
            });
        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    public float getAircraftVelocity()  throws FlightControllerNotAvailableException{
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            float x = flightController.getState().getVelocityX();
            float y = flightController.getState().getVelocityY();
            float z = flightController.getState().getVelocityY();
            return (float) Math.sqrt(x*x + y*y + z*z);
        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    public void setTripodModeEnabled(boolean value)  throws FlightControllerNotAvailableException{
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            flightController.setTripodModeEnabled(true, null);
        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    public void setMultipleModesEnabled(boolean value) throws FlightControllerNotAvailableException{
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            flightController.setMultipleFlightModeEnabled(value, null);
        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    public FlightMode getCurrentAircraftMode()  throws FlightControllerNotAvailableException{
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            return flightController.getState().getFlightMode();
        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    public boolean isVirtualStickEnabled() throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            return flightController.isVirtualStickControlModeAvailable();
        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    public YawControlMode getYawControllerMode() throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            return flightController.getYawControlMode();
        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    public RollPitchControlMode getRollPitchControllerMode() throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            return flightController.getRollPitchControlMode();
        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    public VerticalControlMode getVerticalControllerMode() throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            return flightController.getVerticalControlMode();
        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }


    public void enablePitchRollVelocityMode() throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = ModuleVerificationUtil.getFlightController();
            flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
            flightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);

        }else{
            throw new FlightControllerNotAvailableException("Fligsht controller not available");
        }
    }


    public void enableThrottleVelocityMode() throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = ModuleVerificationUtil.getFlightController();
            flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
            flightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
        }else{
            throw new FlightControllerNotAvailableException("Fligsht controller not available");
        }
    }


    public void enableThrottlePositionMode() throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = ModuleVerificationUtil.getFlightController();
            flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
            flightController.setVerticalControlMode(VerticalControlMode.POSITION);
        }else{
            throw new FlightControllerNotAvailableException("Fligsht controller not available");
        }
    }

    public int getBateryNeededyToGoHome() throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            return flightController.getState().getGoHomeAssessment().getBatteryPercentageNeededToGoHome();
        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    public boolean getIsFlying() throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            return flightController.getState().isFlying();
        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    public void setVisionLandingCallback(){
        final FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
        FlightAssistant flightAssistant = flightController.getFlightAssistant();

        flightAssistant.setVisionControlStateUpdatedcallback(new VisionControlState.Callback() {
            @Override
            public void onUpdate(VisionControlState state) {
                visionControlState = state;
                if(state.landingProtectionState() == VisionLandingProtectionState.NOT_SAFE_TO_LAND){
                    flightController.cancelLanding(new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError resultCode) {
                            ARASGimbalHander.getInstance().rotate(-90, 0, 0);
                        }
                    });
                }
            }
        });
    }

    public String getLandingProtectionState() throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {

            if (visionControlState == null) {
                return "NO_DATA";
            }

            return visionControlState.landingProtectionState().toString();

        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    public boolean getIsGoingHome() throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {

            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            return flightController.getState().isGoingHome();

        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

    // move to Aircraft module
    public boolean getIsConnected() throws FlightControllerNotAvailableException {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightController flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            return flightController.isConnected();
        }else{
            throw new FlightControllerNotAvailableException("Flight controller not available");
        }
    }

}
