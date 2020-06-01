package com.dji.sdk.sample.internal.controller.ARASController.Handlers;

import androidx.annotation.NonNull;

import com.dji.sdk.sample.internal.controller.ARASMessageManager;
import com.koushikdutta.async.AsyncSocket;

import dji.common.error.DJIError;
import dji.keysdk.FlightControllerKey;
import dji.keysdk.callback.SetCallback;
import dji.sdk.sdkmanager.DJISDKManager;

public class ArasFlightControllerKeyHandler {
    private static ArasFlightControllerKeyHandler SINGLE_INSTANCE = null;


    private ArasFlightControllerKeyHandler() { }

    public static ArasFlightControllerKeyHandler getInstance() {
        if (SINGLE_INSTANCE == null) {
            synchronized(ARASFlightControlHandler.class) {
                SINGLE_INSTANCE = new ArasFlightControllerKeyHandler();
            }
        }
        return SINGLE_INSTANCE;
    }

    // NO SOPORTA CAMBIAR LA ALTURA, USAR ESTE METODO DE EJEMPLO PARA OTRAS KEY CON ACCESO SET
    public void setAircraftAltitudeKey(float new_altitude, final AsyncSocket clientStream) {
        FlightControllerKey key = FlightControllerKey.create(FlightControllerKey.ALTITUDE);
        DJISDKManager.getInstance().getKeyManager().setValue(key, new_altitude,  new SetCallback() {

            @Override
            public void onSuccess(){
                ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(true, "NO_ERROR", null));
            }

            @Override
            public void onFailure(@NonNull DJIError djiError) {
                ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(false, djiError.toString() + " " + djiError.getDescription(), null));

            }

        });
    }

}
