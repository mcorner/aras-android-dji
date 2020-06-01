package com.dji.sdk.sample.internal.controller;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.flightcontroller.Attitude;
import dji.common.flightcontroller.FlightMode;
import dji.common.flightcontroller.GPSSignalLevel;
import dji.common.flightcontroller.LocationCoordinate3D;
// import dji.common.flightcontroller.flightassistant.BottomAuxiliaryLightMode;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.mission.waypoint.WaypointMissionState;
import dji.common.model.LocationCoordinate2D;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.FlightAssistant;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

import com.dji.sdk.sample.internal.controller.ARASController.Handlers.ArasFlightControllerKeyHandler;
import com.dji.sdk.sample.internal.controller.ARASController.Handlers.ARASBatteryHandler;
import com.dji.sdk.sample.internal.controller.ARASController.Handlers.ARASGimbalHander;
import com.dji.sdk.sample.internal.controller.ARASController.Handlers.ARASMissionControlHandler;
import com.dji.sdk.sample.internal.controller.ARASController.Handlers.ARASVideoHandler;
import com.dji.sdk.sample.internal.controller.ARASController.Handlers.ARASVirtualRemoteController;
import com.dji.sdk.sample.internal.controller.ARASController.Handlers.ARASWaypointMissionOperatorHandler;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.Util;
import com.koushikdutta.async.callback.CompletedCallback;
import android.content.Context;
import org.json.JSONObject;
import org.json.JSONException;
import android.util.Log;
import java.math.BigDecimal;

//informers
import com.dji.sdk.sample.internal.controller.ARASController.Handlers.ARASFlightControlHandler;

public class ARASMessageManager {

    private static ARASMessageManager SINGLE_INSTANCE = null;
    private Context ctx = null;

    private ARASMessageManager() {}

    public static ARASMessageManager getInstance() {
        if (SINGLE_INSTANCE == null) {
            synchronized (ARASMessageManager.class) {
                SINGLE_INSTANCE = new ARASMessageManager();
            }
        }
        return SINGLE_INSTANCE;
    }

    public void setContext(Context ctx){
        this.ctx = ctx;
    }

    public static void sendStringMessageToClient(final AsyncSocket socket, final String message) {
        try{
            //System.out.println(message);
            if(socket!= null && socket.isOpen()){
                Integer message_bytes_size = message.getBytes().length;
                String message_bytes_size_str = message_bytes_size.toString();
                int n = message_bytes_size_str.length();
                for(int i= 0; i<7-n; i++){
                    message_bytes_size_str = "0" + message_bytes_size_str;
                }
                byte[] array = message_bytes_size_str.getBytes();
                Util.writeAll(socket, array, new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        if (ex == null){
                            Util.writeAll(socket, message.getBytes(), new CompletedCallback() {
                                @Override
                                public void onCompleted(Exception ex) {
                                    //if (ex != null) throw new RuntimeException(ex);
                                    //ToastUtils.setResultToToast(ctx.getResources().getString(R.string.server_wrote_sucessfully_message) + ": " + message);
                                }
                            });
                        }
                    }
                });
            }
        }catch (Exception e){
            System.out.println(e.toString());
        }
    }

    public void sendBytesToClient(final AsyncSocket socket, final byte[] videoBuffer) {
        // write whole bytes
        Util.writeAll(socket, videoBuffer, new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (ex == null){

                }
            }
        });

        /**
         * write length and then frame
        try{
            if(socket!= null && socket.isOpen()){
                ByteArrayOutputStream out = new ByteArrayOutputStream(videoBuffer.length);
                GZIPOutputStream gzip = null;
                gzip = new GZIPOutputStream(out);
                gzip.write(videoBuffer);
                gzip.close();
                final byte[] compressedBytes = out.toByteArray();
                Integer message_bytes_size = compressedBytes.length;
                String message_bytes_size_str = message_bytes_size.toString();
                int n = message_bytes_size_str.length();
                for(int i= 0; i<7-n; i++){
                    message_bytes_size_str = "0" + message_bytes_size_str;
                }
                byte[] array = message_bytes_size_str.getBytes();
                Util.writeAll(socket, array, new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        if (ex == null){
                            Util.writeAll(socket, compressedBytes, new CompletedCallback() {
                                @Override
                                public void onCompleted(Exception ex) {
                                    //if (ex != null) throw new RuntimeException(ex);
                                    //ToastUtils.setResultToToast(ctx.getResources().getString(R.string.server_wrote_sucessfully_message) + ": " + message);
                                }
                            });
                        }
                    }
                });
            }
        }catch (Exception e){
            System.out.println(e.toString());
        }
         */

    }

    public static String buildResponse(boolean success, String resultCode, JSONObject data)
    {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("sucess", String.valueOf(success));
            jsonObject.put("resultCode",  resultCode == null ? "NULL" : resultCode);
            jsonObject.put("data", data == null ? new JSONObject() : data );
        }
        catch (JSONException e) {
            e.printStackTrace();
        }

        String jsonString = jsonObject.toString();
        return jsonString;
    }

    public void processMessage(final AsyncSocket clientStream, String message)
    {
        // {"COMMAND": 'GET_LOCATION',
        //  "COMMAND_TYPE": 'TELEMETRY',
        //  "COMMAND_INFO": "NONE",
        // }

        JSONObject messageObject = null;
        String command = null;
        String commandType = null;
        boolean sentByMethod = false;
        try
        {
            messageObject = new JSONObject(message);
            command = (String) messageObject.get("COMMAND");
            commandType = (String)messageObject.get("COMMAND_TYPE");
        }
        catch (JSONException e)
        {
            sendStringMessageToClient(clientStream, buildResponse(false, "JSON_PARSE_ERROR", null));
            Log.e("JSON_PARSE_ERROR","CRASH StackTrace: "+ e.getStackTrace().toString());
            return;
        }

        DJIError resultCode = null;
        JSONObject dataToClient = new JSONObject();

        if (commandType.equals("FLIGHT_ASSISTANT"))
        {
            if (command.equals("SET_SENSORIAL_ENABLED"))
            {
                FlightController flightController = ((Aircraft) DJISampleApplication.getProductInstance()).getFlightController();
                FlightAssistant flight_assistant = flightController.getFlightAssistant();
                flight_assistant.setActiveObstacleAvoidanceEnabled(true, null);
                flight_assistant.setCollisionAvoidanceEnabled(true, null);
                flight_assistant.setLandingProtectionEnabled(true, null);
                //flight_assistant.setBottomAuxiliaryLightMode(BottomAuxiliaryLightMode.AUTO, null);

            }else if (command.equals("SET_SENSORIAL_DISABLED"))
            {
                FlightController flightController = ((Aircraft) DJISampleApplication.getProductInstance()).getFlightController();
                FlightAssistant flight_assistant = flightController.getFlightAssistant();
                flight_assistant.setActiveObstacleAvoidanceEnabled(false, null);
                flight_assistant.setCollisionAvoidanceEnabled(false, null);
                flight_assistant.setLandingProtectionEnabled(true, null);
                //flight_assistant.setBottomAuxiliaryLightMode(BottomAuxiliaryLightMode.OFF, null);

            }else if (command.equals("SET_HOME_LOCATION"))
            {
                JSONObject infoCommand = null;
                try {
                    FlightController flightController = ((Aircraft) DJISampleApplication.getProductInstance()).getFlightController();
                    infoCommand = (JSONObject)messageObject.get("COMMAND_INFO");
                    float latitude = BigDecimal.valueOf(infoCommand.getDouble("latitude")).floatValue();
                    float longitude = BigDecimal.valueOf(infoCommand.getDouble("longitude")).floatValue();
                    float altitude = BigDecimal.valueOf(infoCommand.getDouble("altitude")).floatValue();
                    flightController.setHomeLocation(new LocationCoordinate2D(latitude, longitude), null);
                    flightController.setGoHomeHeightInMeters((int) altitude, null);

                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }else if (command.equals("GET_BATTERY_NEEDED_TO_GO_HOME"))
            {
                JSONObject jsonObject = new JSONObject();
                try{
                    try{
                        int battery_needed = ARASFlightControlHandler.getInstance().getBateryNeededyToGoHome();
                        int current_battery = ARASBatteryHandler.getInstance().getAircraftChargeRemainingInPercent();
                        jsonObject.put("needed", String.valueOf(battery_needed));
                        jsonObject.put("current", String.valueOf(current_battery));

                    }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                        Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                        resultCode = DJIError.COMMON_DISCONNECTED;
                    } catch (ARASBatteryHandler.BatteryNotAvailableException e) {
                        Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                        resultCode = DJIError.COMMON_DISCONNECTED;
                    }
                }catch(JSONException e){
                    Log.e("JSON_ERROR","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_PARAM_INVALID;
                }
                dataToClient = jsonObject;
            }else if (command.equals("GET_IS_FLYING"))
            {
                JSONObject jsonObject = new JSONObject();
                try{
                    try{
                        boolean is_flying = ARASFlightControlHandler.getInstance().getIsFlying();
                        jsonObject.put("flying", String.valueOf(is_flying));

                    }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                        Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                        resultCode = DJIError.COMMON_DISCONNECTED;
                    }
                    dataToClient = jsonObject;
                }catch(JSONException e){
                    Log.e("JSON_ERROR","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_PARAM_INVALID;
                }
            }else if (command.equals("GET_IS_CONNECTED"))
            {
                JSONObject jsonObject = new JSONObject();
                try{
                    try{
                        boolean connected = ARASFlightControlHandler.getInstance().getIsConnected();
                        boolean streaming = DJISDKManager.getInstance().getLiveStreamManager().isStreaming();
                        jsonObject.put("connected", String.valueOf(connected));
                        jsonObject.put("streaming", String.valueOf(streaming));

                    }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                        Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                        resultCode = DJIError.COMMON_DISCONNECTED;
                    }
                    dataToClient = jsonObject;
                }catch(JSONException e){
                    Log.e("JSON_ERROR","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_PARAM_INVALID;
                }
            }else if (command.equals("CANCEL_GO_HOME"))
            {
                FlightController flightController = ((Aircraft) DJISampleApplication.getProductInstance()).getFlightController();
                flightController.cancelGoHome(null);
                flightController.cancelLanding(null);
            }
        } else if (commandType.equals("MISSION"))
        {
            if (command.equals("SET_PRECISION_LANDING"))
            {
                try{
                    ARASFlightControlHandler.getInstance().setPrecisionLandingEnabled(true, clientStream);
                    sentByMethod = true; // Method has to reponse to client
                }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                    Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_DISCONNECTED;
                }
            }
            else if (command.equals("SET_GSME"))
            {

            }
            else if (command.equals("LOAD_MISSION"))
            {
                //try {
                //    JSONObject infoCommand = messageObject.getJSONObject("COMMAND_INFO");
                //    resultCode = ARASWaypointMissionOperatorHandler.getInstance().LoadMission(infoCommand.toString());
                //}catch (JSONException e){
                //    resultCode = DJIError.COMMON_PARAM_INVALID;
                //}
            }
            if (command.equals("UPLOAD_MISSION"))
            {
                ARASWaypointMissionOperatorHandler.getInstance().uploadMission(clientStream);
                sentByMethod = true;

            }
            else if (command.equals("START_MISSION"))
            {
                ARASWaypointMissionOperatorHandler.getInstance().startMission(clientStream);
                sentByMethod = true;
            }
            else if (command.equals("GET_MISSION_STATE"))
            {
                WaypointMissionState currentWaypointMissionState = ARASWaypointMissionOperatorHandler.getInstance().getMissionState();
                boolean going_home_execution = ARASWaypointMissionOperatorHandler.getInstance().getGoingHomeExecution();
                JSONObject jsonObject = new JSONObject();
                try {
                    going_home_execution = going_home_execution || ARASFlightControlHandler.getInstance().getIsGoingHome();
                    jsonObject.put("MISSION_STATE", currentWaypointMissionState.toString());
                    jsonObject.put("GOING_HOME_EXECUTION", going_home_execution);
                }catch (JSONException e){
                    resultCode = DJIError.COMMON_EXECUTION_FAILED;
                } catch (ARASFlightControlHandler.FlightControllerNotAvailableException e) {
                    e.printStackTrace();
                    resultCode = DJIError.COMMON_EXECUTION_FAILED;
                }
                dataToClient = jsonObject;
            }
            else if (command.equals("PAUSE_MISSION"))
            {

            }
            else if (command.equals("RESUME_MISSION"))
            {

            }
            else if (command.equals("STOP_MISSION"))
            {
                JSONObject infoCommand = null;
                try {
                    infoCommand = (JSONObject)messageObject.get("COMMAND_INFO");
                    float latitude = BigDecimal.valueOf(infoCommand.getDouble("latitude")).floatValue();
                    float longitude = BigDecimal.valueOf(infoCommand.getDouble("longitude")).floatValue();
                    float altitude = BigDecimal.valueOf(infoCommand.getDouble("altitude")).floatValue();
                    ARASWaypointMissionOperatorHandler.getInstance().stopMission(latitude, longitude, altitude);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }else if (command.equals("EXECUTE_MISSION"))
            {
                sentByMethod = true;
                try {
                    JSONObject infoCommand = messageObject.getJSONObject("COMMAND_INFO");
                    ARASWaypointMissionOperatorHandler.getInstance().ExecuteMission(infoCommand.toString(), clientStream);
                }catch (JSONException e){
                    resultCode = DJIError.COMMON_PARAM_INVALID;
                }
            }
        }
        else if (commandType.equals("AIRCRAFT_INFORMATION"))
        {
            if (command.equals("GET_ALL"))
            {
                JSONObject jsonObject = new JSONObject();
                try{
                    jsonObject.put("latitude", "TEST");
                    try{
                        LocationCoordinate3D currentAircraftLocation = ARASFlightControlHandler.getInstance().getAircraftLocation();
                        Attitude currentAircraftAttitude = ARASFlightControlHandler.getInstance().getAircraftAttitude();
                        GPSSignalLevel currentGPSSignalLevel = ARASFlightControlHandler.getInstance().getAircraftGPSSignalLevel();

                        jsonObject.put("latitude", String.valueOf(currentAircraftLocation.getLatitude()));
                        jsonObject.put("longitude", String.valueOf(currentAircraftLocation.getLongitude()));
                        jsonObject.put("altitude", String.valueOf(currentAircraftLocation.getAltitude()));
                        jsonObject.put("aircraft_pitch", String.valueOf(currentAircraftAttitude.pitch));
                        jsonObject.put("aircraft_roll", String.valueOf(currentAircraftAttitude.roll));
                        jsonObject.put("aircraft_yaw", String.valueOf(currentAircraftAttitude.yaw));
                        jsonObject.put("gps", String.valueOf(currentGPSSignalLevel.value()));
                        jsonObject.put("battery", String.valueOf(ARASBatteryHandler.getInstance().getAircraftChargeRemainingInPercent()));
                        jsonObject.put("velocity", String.valueOf(ARASFlightControlHandler.getInstance().getAircraftVelocity()));
                        dji.common.gimbal.Attitude gimbalAttitude = ARASGimbalHander.getInstance().getAircraftAttitude();
                        jsonObject.put("gimbal_pitch", String.valueOf(gimbalAttitude.getPitch()));
                        jsonObject.put("gimbal_yaw", String.valueOf(gimbalAttitude.getYaw()));
                        jsonObject.put("gimbal_roll", String.valueOf(gimbalAttitude.getRoll()));

                        dataToClient = jsonObject;
                    }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                        Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                        resultCode = DJIError.COMMON_DISCONNECTED;
                    }catch (ARASGimbalHander.GimbalNotAvailableException e){
                        Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                        resultCode = DJIError.COMMON_DISCONNECTED;
                    }catch (ARASBatteryHandler.BatteryNotAvailableException e){
                        Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                        resultCode = DJIError.COMMON_DISCONNECTED;
                    }
                }catch(JSONException e){
                    Log.e("JSON_ERROR","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_PARAM_INVALID;
                }
            } else if (command.equals("GET_LAT_LNG"))
            {
                JSONObject jsonObject = new JSONObject();
                try{
                    try{
                        LocationCoordinate3D currentAircraftLocation = ARASFlightControlHandler.getInstance().getAircraftLocation();
                        jsonObject.put("latitude", String.valueOf(currentAircraftLocation.getLatitude()));
                        jsonObject.put("longitude", String.valueOf(currentAircraftLocation.getLongitude()));
                        jsonObject.put("altitude", String.valueOf(currentAircraftLocation.getAltitude()));

                        dataToClient = jsonObject;
                    }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                        Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                        resultCode = DJIError.COMMON_DISCONNECTED;
                    }
                }catch(JSONException e){
                    Log.e("JSON_ERROR","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_PARAM_INVALID;
                }
            }else if (command.equals("LANDING_STATE"))
            {
                JSONObject jsonObject = new JSONObject();
                try{
                    try{
                        String landingState = ARASFlightControlHandler.getInstance().getLandingProtectionState();
                        jsonObject.put("landing_state", landingState);
                        dataToClient = jsonObject;
                    }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                        Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                        resultCode = DJIError.COMMON_DISCONNECTED;
                    }
                }catch(JSONException e){
                    Log.e("JSON_ERROR","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_PARAM_INVALID;
                }
            }
        }
        else if (commandType.equals("VIDEO_STREAMING"))
        {
            if (command.equals("START_VIDEO_STREAMING"))
            {

            }
            else if (command.equals("STOP_VIDEO_STREAMING"))
            {

            }
            else if (command.equals("RESTART_VIDEO_STREAMING"))
            {

            }
            else if (command.equals("ESTABLISH_CONNECTION"))
            {
                JSONObject infoCommand = null;
                try {
                    infoCommand = (JSONObject)messageObject.get("COMMAND_INFO");
                    int h = BigDecimal.valueOf(infoCommand.getDouble("h")).intValue();
                    int w = BigDecimal.valueOf(infoCommand.getDouble("w")).intValue();
                    ARASVideoHandler.getInstance().establishVideoSocketConnection(clientStream, h, w);
                    sentByMethod = true;
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }

        }else if (commandType.equals("CAMERA"))
        {
            if (command.equals("NIGHT"))
            {
                DJISampleApplication.getProductInstance().getCamera().setISO(SettingsDefinitions.ISO.ISO_100, null);
                DJISampleApplication.getProductInstance().getCamera().setShutterSpeed(SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_800, null);
            }else if(command.equals("AUTO")){
                DJISampleApplication.getProductInstance().getCamera().setISO(SettingsDefinitions.ISO.AUTO, null);
                DJISampleApplication.getProductInstance().getCamera().setShutterSpeed(SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_60, null);
            }else if(command.equals("MAX_ZOOM")){
                DJISampleApplication.getProductInstance().getCamera().getOpticalZoomSpec(new CommonCallbacks.CompletionCallbackWith<SettingsDefinitions.OpticalZoomSpec>() {
                    @Override
                    public void onSuccess(SettingsDefinitions.OpticalZoomSpec opticalZoomSpec) {
                        int maxFocalLength = opticalZoomSpec.getMaxFocalLength();
                        maxFocalLength = maxFocalLength / 3 ;
                        DJISampleApplication.getProductInstance().getCamera().setOpticalZoomFocalLength(maxFocalLength, null);
                    }

                    @Override
                    public void onFailure(DJIError djiError) {

                    }
                });
            }else if(command.equals("MIN_ZOOM")){
                DJISampleApplication.getProductInstance().getCamera().getOpticalZoomSpec(new CommonCallbacks.CompletionCallbackWith<SettingsDefinitions.OpticalZoomSpec>() {
                    @Override
                    public void onSuccess(SettingsDefinitions.OpticalZoomSpec opticalZoomSpec) {
                        int minFocalLength = opticalZoomSpec.getMinFocalLength();
                        DJISampleApplication.getProductInstance().getCamera().setOpticalZoomFocalLength(minFocalLength, null);
                    }
                    @Override
                    public void onFailure(DJIError djiError) {

                    }
                });
            }else if(command.equals("GET_MAX_MIN_ZOOM")){
                sentByMethod = true;

                DJISampleApplication.getProductInstance().getCamera().getOpticalZoomSpec(new CommonCallbacks.CompletionCallbackWith<SettingsDefinitions.OpticalZoomSpec>() {
                    @Override
                    public void onSuccess(SettingsDefinitions.OpticalZoomSpec opticalZoomSpec) {
                        int minFocalLength = opticalZoomSpec.getMinFocalLength();
                        int maxFocalLength = opticalZoomSpec.getMaxFocalLength();

                        JSONObject jsonObject = new JSONObject();
                        try {
                            jsonObject.put("MIN_ZOOM", String.valueOf(minFocalLength));
                            jsonObject.put("MAX_ZOOM", String.valueOf(maxFocalLength));
                            ARASMessageManager.sendStringMessageToClient(clientStream, ARASMessageManager.buildResponse(true, "NO_ERROR", jsonObject));

                        }catch (JSONException e){

                        }
                    }
                    @Override
                    public void onFailure(DJIError djiError) {

                    }
                });
            }

        }else if (commandType.equals("GIMBAL"))
        {
            if (command.equals("LOOK_FRONT"))
            {
            }else if(command.equals("LOOK_DOWN")){
            }
        }
        else if (commandType.equals("VIRTUAL_REMOTE_CONTROLLER"))
        {
            if (command.equals("MOVE_DISTANCE"))
            {
                JSONObject infoCommand = null;
                try {
                    infoCommand = (JSONObject)messageObject.get("COMMAND_INFO");
                    float pitch_cm = BigDecimal.valueOf(infoCommand.getDouble("PITCH_CM")).floatValue();
                    float roll_cm = BigDecimal.valueOf(infoCommand.getDouble("ROLL_CM")).floatValue();
                    float throttle_cm = BigDecimal.valueOf(infoCommand.getDouble("THROTTLE_CM")).floatValue();
                    float speed_cms = BigDecimal.valueOf(infoCommand.getDouble("SPEED_CMS")).floatValue();
                    String axis = infoCommand.getString("AXIS");
                    ARASVirtualRemoteController.getInstance().moveDistance(pitch_cm, roll_cm, throttle_cm, speed_cms, axis, 5, clientStream);
                    sentByMethod = true;
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (ARASFlightControlHandler.FlightControllerNotAvailableException e) {
                    e.printStackTrace();
                }

            }else if (command.equals("UPDATE_JOYSTICK_VALUE"))
            {
                JSONObject infoCommand = null;
                try {
                    infoCommand = (JSONObject)messageObject.get("COMMAND_INFO");
                    float pitch = BigDecimal.valueOf(infoCommand.getDouble("PITCH")).floatValue();
                    float roll = BigDecimal.valueOf(infoCommand.getDouble("ROLL")).floatValue();
                    float yaw = BigDecimal.valueOf(infoCommand.getDouble("YAW")).floatValue();
                    float throttle = BigDecimal.valueOf(infoCommand.getDouble("THROTTLE")).floatValue();
                    String throttle_mode = infoCommand.getString("THROTTLE_MODE");

                    ARASVirtualRemoteController.getInstance().updateJoystickValue(pitch, roll, yaw, throttle, throttle_mode, clientStream);
                    sentByMethod = true;
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }else if (command.equals("GO_HOME"))
            {
                JSONObject infoCommand = null;
                try{
                    infoCommand = (JSONObject)messageObject.get("COMMAND_INFO");
                    int altitude = BigDecimal.valueOf(infoCommand.getDouble("ALTITUDE")).intValue();
                    System.out.println("ALTITUDE HOME " + altitude);
                    ARASFlightControlHandler.getInstance().startGoHome(altitude, null);
                }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                    Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_DISCONNECTED;
                }catch (JSONException e) {
                    e.printStackTrace();
                    System.out.println(e);
                }
            }
            else if (command.equals("LANDING"))
            {
                try{
                    ARASFlightControlHandler.getInstance().startLanding(clientStream);
                }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                    Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_DISCONNECTED;
                }
            }else if (command.equals("TAKE_OFF"))
            {
                try{
                    ARASFlightControlHandler.getInstance().startTakeOff(clientStream);
                    sentByMethod = true;
                }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                    Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_DISCONNECTED;
                }
            }else if (command.equals("IS_VIRTUAL_STICK_ENABLED"))
            {
                JSONObject jsonObject = new JSONObject();
                try{
                    boolean virtual_stick_enabled = ARASFlightControlHandler.getInstance().isVirtualStickEnabled();
                    try{
                        jsonObject.put("CURRENT_STATE", String.valueOf(virtual_stick_enabled));
                    }catch(JSONException e){
                        Log.e("JSON_ERROR","CRASH StackTrace: "+ e.getStackTrace().toString());
                        resultCode = DJIError.COMMON_PARAM_INVALID;
                    }
                    dataToClient = jsonObject;
                }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                    Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_DISCONNECTED;
                }
            }else if (command.equals("ENABLE_VIRTUAL_STICK"))
            {
                JSONObject infoCommand = null;
                try{
                    infoCommand = (JSONObject)messageObject.get("COMMAND_INFO");
                    String throttle_mode = infoCommand.getString("THROTTLE_MODE");
                    ARASFlightControlHandler.getInstance().setVirtualStickModeEnabled(true, throttle_mode,5, clientStream);
                    sentByMethod = true;
                }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                    Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_DISCONNECTED;
                } catch (JSONException e) {
                    e.printStackTrace();
                    resultCode = DJIError.COMMON_PARAM_INVALID;
                }
            }else if (command.equals("DISABLE_VIRTUAL_STICK"))
            {
                JSONObject infoCommand = null;
                try{
                    infoCommand = (JSONObject)messageObject.get("COMMAND_INFO");
                    String throttle_mode = infoCommand.getString("THROTTLE_MODE");
                    ARASFlightControlHandler.getInstance().setVirtualStickModeEnabled(false, throttle_mode, 5, clientStream);
                    sentByMethod = true;
                }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                    Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_DISCONNECTED;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }else if (command.equals("GET_AXIS_MODES"))
            {
                JSONObject jsonObject = new JSONObject();
                try{
                    YawControlMode yaw_controll_mode = ARASFlightControlHandler.getInstance().getYawControllerMode();
                    RollPitchControlMode roll_pitch_controll_mode = ARASFlightControlHandler.getInstance().getRollPitchControllerMode();
                    VerticalControlMode vertical_controller_mode =  ARASFlightControlHandler.getInstance().getVerticalControllerMode();
                    try{
                        jsonObject.put("CURRENT_STATE", "YAW MODE: " + yaw_controll_mode.toString() + " ROLL PITCH MODE " + roll_pitch_controll_mode.toString() + " VERTICAL " + vertical_controller_mode.toString());
                        dataToClient = jsonObject;
                    }catch(JSONException e){
                        Log.e("JSON_ERROR","CRASH StackTrace: "+ e.getStackTrace().toString());
                        resultCode = DJIError.COMMON_PARAM_INVALID;
                    }
                    dataToClient = jsonObject;
                }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                    Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_DISCONNECTED;
                }
            }else if (command.equals("TAKE_MANUAL_CONTROL"))
            {
                //try{
                //    ARASWaypointMissionOperatorHandler.getInstance().pauseMission(); // pause if doing mission
                //    ARASFlightControlHandler.getInstance().setVirtualStickModeEnabled(true, 5, clientStream);
                //    sentByMethod = true;
                //}catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                //    Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                //    resultCode = DJIError.COMMON_DISCONNECTED;
                //}
            }else if (command.equals("DROP_MANUAL_CONTROL"))
            {
                //try{
                //    ARASWaypointMissionOperatorHandler.getInstance().resumeMission(); // resume if doing mission
                //    ARASFlightControlHandler.getInstance().setVirtualStickModeEnabled(false, 5, clientStream);
                //    sentByMethod = true;
                //}catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                //  Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                //    resultCode = DJIError.COMMON_DISCONNECTED;
                //}
            }
        }else if (commandType.equals("MISSION_CONTROL"))
        {
            if (command.equals("GO_TO_ACTION"))
            {
                JSONObject infoCommand = null;
                try {
                    infoCommand = (JSONObject)messageObject.get("COMMAND_INFO");
                    float latitude = BigDecimal.valueOf(infoCommand.getDouble("latitude")).floatValue();
                    float longitude = BigDecimal.valueOf(infoCommand.getDouble("longitude")).floatValue();
                    float altitude = BigDecimal.valueOf(infoCommand.getDouble("altitude")).floatValue();
                    ARASMissionControlHandler.getInstance().goToLocationAction(latitude, longitude, altitude);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            else if (command.equals("GO_TO_ACTION_DEBUG"))
            {
                JSONObject infoCommand = null;
                try {
                    infoCommand = (JSONObject)messageObject.get("COMMAND_INFO");
                    float altitude = BigDecimal.valueOf(infoCommand.getDouble("altitude")).floatValue();
                    resultCode = ARASMissionControlHandler.getInstance().goToLocationActionDebug(altitude);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
            else if (command.equals("GO_TO_ACTION_SAFE"))
            {
                JSONObject infoCommand = null;
                sentByMethod = true;
                try {
                    infoCommand = (JSONObject)messageObject.get("COMMAND_INFO");
                    float latitude = BigDecimal.valueOf(infoCommand.getDouble("latitude")).floatValue();
                    float longitude = BigDecimal.valueOf(infoCommand.getDouble("longitude")).floatValue();
                    float altitude = BigDecimal.valueOf(infoCommand.getDouble("altitude")).floatValue();
                    ARASMissionControlHandler.getInstance().goToLocationActionSafe(latitude, longitude, altitude, clientStream);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
            else if (command.equals("LANDING_ACTION"))
            {
                resultCode = ARASMissionControlHandler.getInstance().makeLandingAction();
            }
            else if (command.equals("TAKEOFF_ACTION"))
            {
                resultCode = ARASMissionControlHandler.getInstance().makeTakeOffAction();
            }
            else if (command.equals("GIMBAL_ATTITUDE_ACTION"))
            {
                JSONObject infoCommand = null;
                JSONObject jsonObject = new JSONObject();
                try {
                    infoCommand = (JSONObject)messageObject.get("COMMAND_INFO");
                    float pitch = BigDecimal.valueOf(infoCommand.getDouble("pitch")).floatValue();
                    float roll = BigDecimal.valueOf(infoCommand.getDouble("roll")).floatValue();
                    float yaw = BigDecimal.valueOf(infoCommand.getDouble("yaw")).floatValue();
                    //ARASMissionControlHandler.getInstance().setGimbalAttitudeAction(pitch, roll, yaw);
                    ARASGimbalHander.getInstance().rotate(pitch, roll, yaw);
                    dataToClient = jsonObject;
                } catch (JSONException e) {
                    e.printStackTrace();
                    resultCode = DJIError.COMMON_PARAM_INVALID;
                }
            }else if (command.equals("YAW_ACTION"))
            {
                JSONObject infoCommand = null;
                JSONObject jsonObject = new JSONObject();
                try {
                    infoCommand = (JSONObject)messageObject.get("COMMAND_INFO");
                    float angle = BigDecimal.valueOf(infoCommand.getDouble("angle")).floatValue();
                    ARASMissionControlHandler.getInstance().setYawAction(angle, clientStream);
                    sentByMethod = true; // Method has to reponse to client
                } catch (JSONException e) {
                    e.printStackTrace();
                    resultCode = DJIError.COMMON_PARAM_INVALID;
                }
            }else if (command.equals("GET_ACTION_RUNNING"))
            {

                JSONObject jsonObject = new JSONObject();
                try{
                    jsonObject.put("action_running", ARASMissionControlHandler.getInstance().actionRunning());
                    dataToClient = jsonObject;

                }catch(JSONException e){
                    Log.e("JSON_ERROR","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_PARAM_INVALID;
                }
            }else if (command.equals("GET_OUT_BASE"))
            {
                JSONObject infoCommand = null;
                try {
                    infoCommand = (JSONObject)messageObject.get("COMMAND_INFO");
                    float latitude = BigDecimal.valueOf(infoCommand.getDouble("latitude")).floatValue();
                    float longitude = BigDecimal.valueOf(infoCommand.getDouble("longitude")).floatValue();
                    float altitude = BigDecimal.valueOf(infoCommand.getDouble("altitude")).floatValue();
                    ARASMissionControlHandler.getInstance().getOutBase(latitude, longitude, altitude);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }else if (commandType.equals("AIRCRAFT_MODES"))
        {
            if (command.equals("ENABLE_TRIPOD_MODE"))
            {
                try{
                    ARASFlightControlHandler.getInstance().setTripodModeEnabled(true);
                }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                    Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_DISCONNECTED;
                }
            }else if (command.equals("DISABLE_TRIPOD_MODE"))
            {
                try{
                    ARASFlightControlHandler.getInstance().setTripodModeEnabled(false);
                }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                    Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_DISCONNECTED;
                }
            }else if (command.equals("ENABLE_MULTIPLE_MODES"))
            {
                try{
                    ARASFlightControlHandler.getInstance().setMultipleModesEnabled(true);
                }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                    Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_DISCONNECTED;
                }
            }else if (command.equals("DISABLE_MULTIPLE_MODES")){
                try{
                    ARASFlightControlHandler.getInstance().setMultipleModesEnabled(false);
                }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                    Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_DISCONNECTED;
                }
            }else if (command.equals("GET_CURRENT_MODE"))
            {
                JSONObject jsonObject = new JSONObject();
                try{
                    FlightMode flight_mode = ARASFlightControlHandler.getInstance().getCurrentAircraftMode();
                    try{
                        jsonObject.put("CURRENT_MODE", String.valueOf(flight_mode));
                    }catch(JSONException e){
                        Log.e("JSON_ERROR","CRASH StackTrace: "+ e.getStackTrace().toString());
                        resultCode = DJIError.COMMON_PARAM_INVALID;
                    }
                    dataToClient = jsonObject;
                }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                    Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_DISCONNECTED;
                }
            }else if (command.equals("ENABLE_VELOCITY_MODE_PITCH_ROLL")){
                try{
                    ARASFlightControlHandler.getInstance().enablePitchRollVelocityMode();
                }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                    Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_DISCONNECTED;
                }
            }else if (command.equals("ENABLE_VELOCITY_MODE_THROTTLE")){
                try{
                    ARASFlightControlHandler.getInstance().enableThrottleVelocityMode();
                }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                    Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_DISCONNECTED;
                }
            }else if (command.equals("ENABLE_POSITION_MODE_THROTTLE")){
                try{
                    ARASFlightControlHandler.getInstance().enableThrottlePositionMode();
                }catch (ARASFlightControlHandler.FlightControllerNotAvailableException e){
                    Log.e("NO_HANDLER_AVAILBLE","CRASH StackTrace: "+ e.getStackTrace().toString());
                    resultCode = DJIError.COMMON_DISCONNECTED;
                }
            }
        }else if (commandType.equals("FLIGHT_CONTROLLLER_KEY"))
        {
            if (command.equals("SET_ALTITUDE"))
            {
                sentByMethod = true;
                JSONObject infoCommand = null;
                try {
                    infoCommand = (JSONObject)messageObject.get("COMMAND_INFO");
                    float altitude = BigDecimal.valueOf(infoCommand.getDouble("altitude")).floatValue();
                    ArasFlightControllerKeyHandler.getInstance().setAircraftAltitudeKey(altitude, clientStream);

                } catch (JSONException e) {
                    e.printStackTrace();
                    resultCode = DJIError.COMMON_PARAM_INVALID;
                }
            }
        }

        if(sentByMethod == false){
            sendStringMessageToClient(clientStream, buildResponse(resultCode == null, resultCode == null ? "NO_ERROR" : resultCode.toString(), dataToClient));
        }

    }

}