package com.dji.sdk.sample.internal.controller.ARASController.Handlers;

import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.utils.ModuleVerificationUtil;
import dji.common.battery.BatteryState;


public class ARASBatteryHandler {
    private static ARASBatteryHandler SINGLE_INSTANCE = null;
    private BatteryState batteryState = null;

    public class BatteryNotAvailableException extends Exception
    {
        public BatteryNotAvailableException(String message)
        {
            super(message);
        }
    }

    private ARASBatteryHandler() { }

    public static ARASBatteryHandler getInstance() {
        if (SINGLE_INSTANCE == null) {
            synchronized(ARASFlightControlHandler.class) {
                SINGLE_INSTANCE = new ARASBatteryHandler();
                SINGLE_INSTANCE.restartBatteryUpdater();
            }
        }
        return SINGLE_INSTANCE;
    }

    public void restartBatteryUpdater(){
        if (ModuleVerificationUtil.isBatteryModuleAvailable()) {
            DJISampleApplication.getProductInstance().getBattery().setStateCallback(null);
            DJISampleApplication.getProductInstance().getBattery().setStateCallback(new BatteryState.Callback() {
                @Override
                public void onUpdate(BatteryState bs) {
                    batteryState = bs;
                }
            });
        }

    }

    public int getAircraftChargeRemainingInPercent() throws BatteryNotAvailableException {
        if(batteryState == null){
            restartBatteryUpdater();
            return -1;
        }

        if (ModuleVerificationUtil.isBatteryModuleAvailable() == false) {
            batteryState = null;
            return -1;
        }
        return batteryState.getChargeRemainingInPercent();
    }

}
