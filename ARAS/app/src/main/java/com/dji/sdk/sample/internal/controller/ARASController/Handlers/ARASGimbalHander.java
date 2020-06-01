package com.dji.sdk.sample.internal.controller.ARASController.Handlers;

import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.utils.ModuleVerificationUtil;
import dji.common.gimbal.Attitude;
import dji.common.gimbal.Axis;
import dji.common.gimbal.GimbalState;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.sdk.gimbal.Gimbal;


public class ARASGimbalHander {
    private static ARASGimbalHander SINGLE_INSTANCE = null;
    private Attitude gimbalAttitude = null;
    public class GimbalNotAvailableException extends Exception
    {
        public GimbalNotAvailableException(String message)
        {
            super(message);
        }
    }

    private ARASGimbalHander() { }

    public static ARASGimbalHander getInstance() {
        if (SINGLE_INSTANCE == null) {
            synchronized(ARASFlightControlHandler.class) {
                SINGLE_INSTANCE = new ARASGimbalHander();
            }
        }
        return SINGLE_INSTANCE;
    }

    public void restartGimbalUpdater(){
        if (ModuleVerificationUtil.isGimbalModuleAvailable()) {
            DJISampleApplication.getProductInstance().getGimbal().setStateCallback(null);
            DJISampleApplication.getProductInstance().getGimbal().setStateCallback(new GimbalState.Callback() {
                @Override
                public void onUpdate(GimbalState gimbalState) {
                    gimbalAttitude = gimbalState.getAttitudeInDegrees();
                }
            });
        }
        // configuraciones globales para el gimbal
        lockAxis();
        setStrength();
    }

    public Attitude getAircraftAttitude() throws GimbalNotAvailableException {
        if(gimbalAttitude == null){
            restartGimbalUpdater();
            return new Attitude(1000, 1000, 100);
        }

        if (ModuleVerificationUtil.isGimbalModuleAvailable() == false) {
            gimbalAttitude = null;
            return new Attitude(1000, 1000, 1000);
        }
        return gimbalAttitude;
    }

    public void startCalibration() {
        if (ModuleVerificationUtil.isGimbalModuleAvailable()) {
            Gimbal gimbal = DJISampleApplication.getProductInstance().getGimbal();
            gimbal.startCalibration(null);
        }
    }

    public void setStrength() {
        if (ModuleVerificationUtil.isGimbalModuleAvailable()) {
            Gimbal gimbal = DJISampleApplication.getProductInstance().getGimbal();
            gimbal.setMotorControlStrength(Axis.PITCH, 100, null);
            gimbal.setMotorControlStrength(Axis.ROLL, 100, null);
            gimbal.setMotorControlStrength(Axis.YAW, 100, null);
        }
    }

    public void rotate(float pitch, float roll, float yaw){
        if (ModuleVerificationUtil.isGimbalModuleAvailable()) {
            Gimbal gimbal = DJISampleApplication.getProductInstance().getGimbal();
            gimbal.rotate(new Rotation.Builder().pitch(pitch).roll(roll).yaw(yaw)
                            .mode(RotationMode.ABSOLUTE_ANGLE)
                            .time(0)
                            .build(), null);

        }
    }

    public void lockAxis() {
        if (ModuleVerificationUtil.isGimbalModuleAvailable()) {
            Gimbal gimbal = DJISampleApplication.getProductInstance().getGimbal();
            gimbal.setYawSimultaneousFollowEnabled(true, null);
            gimbal.setMotorControlStiffness(Axis.PITCH, 100, null);
            gimbal.setMotorControlStiffness(Axis.ROLL, 100, null);
            gimbal.setMotorControlStiffness(Axis.YAW, 100, null);
            gimbal.setMotorControlStrength(Axis.PITCH, 100, null);
            gimbal.setMotorControlStrength(Axis.ROLL, 100, null);
            gimbal.setMotorControlStrength(Axis.YAW, 100, null);
            gimbal.setMotorControlGyroFilteringFactor(Axis.PITCH, 100, null);
            gimbal.setMotorControlGyroFilteringFactor(Axis.ROLL, 100, null);
            gimbal.setMotorControlGyroFilteringFactor(Axis.YAW, 100, null);
            gimbal.setMotorControlPreControl(Axis.PITCH, 100, null);
            gimbal.setMotorControlPreControl(Axis.ROLL, 100, null);
            gimbal.setMotorControlPreControl(Axis.YAW, 100, null);
        }
    }

}
