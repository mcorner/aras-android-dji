package com.dji.sdk.sample.internal.controller.ARASController.Handlers;
import com.dji.sdk.sample.internal.controller.ARASMessageManager;
import com.koushikdutta.async.AsyncSocket;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;

public class ARASVideoHandler {
    private static ARASVideoHandler SINGLE_INSTANCE = null;
    private  AsyncSocket currentClientStream;
    private DJICodecManager currentCodectManager = null;


    private ARASVideoHandler() { }

    public static ARASVideoHandler getInstance() {
        if (SINGLE_INSTANCE == null) {
            synchronized(ARASFlightControlHandler.class) {
                SINGLE_INSTANCE = new ARASVideoHandler();
            }
        }
        return SINGLE_INSTANCE;
    }

    private void sendFrame(byte[] rgbData) {
        if(currentClientStream != null){
            ARASMessageManager.getInstance().sendBytesToClient(currentClientStream, rgbData);
        }
    }


    public VideoFeeder.VideoDataListener getVideoSendSocketListener(){
        VideoFeeder.VideoDataListener videoDataListener = null;
        videoDataListener = new VideoFeeder.VideoDataListener() {
            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                //ARASVideoHandler.getInstance().sendFrame(currentCodectManager.getRgbaData(160, 90));
            }
        };
        return videoDataListener;
    }


    public boolean registerLiveVideo(VideoFeeder.VideoDataListener videoDataListener) {
        VideoFeeder.VideoFeed videoFeed = VideoFeeder.getInstance().provideTranscodedVideoFeed();
        if (videoDataListener != null && videoFeed != null && !videoFeed.getListeners().contains(videoDataListener)) {
            videoFeed.addVideoDataListener(videoDataListener);
            return true;
        }else{
            return false;
        }
    }

    public void establishVideoSocketConnection(final AsyncSocket clientStream, int h, int w){
        this.currentClientStream = clientStream;
        ARASVideoHandler.getInstance().sendFrame(currentCodectManager.getRgbaData(w, h));
        //registerLiveVideo(getVideoSendSocketListener());
    }

    public void updateCodecManager(DJICodecManager codecManager) {
        this.currentCodectManager = codecManager;
    }
}