/************************************************************
 * * EaseMob CONFIDENTIAL
 * __________________
 * Copyright (C) 2016 Hyphenate Inc. All rights reserved.
 * <p/>
 * NOTICE: All information contained herein is, and remains
 * the property of EaseMob Technologies.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from EaseMob Technologies.
 */
package com.jsonse.video;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnErrorListener;
import android.media.MediaRecorder.OnInfoListener;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class RecorderVideoActivity extends Activity implements
        OnClickListener, OnErrorListener,
        OnInfoListener {
    private static final String TAG = "RecorderVideoActivity";
    private final static String CLASS_LABEL = "RecordActivity";
    private PowerManager.WakeLock mWakeLock;
    private TextView btnStart;
    private MediaRecorder mediaRecorder;
    private FrameLayout frameLayout;// to display video
    String localPath = "";// path to save recorded video
    private Camera mCamera;
    private int previewWidth = 640;
    private int previewHeight = 480;
    // 输出宽度
    private static final int OUTPUT_WIDTH = 320;
    // 输出高度
    private static final int OUTPUT_HEIGHT = 240;
    // 宽高比
    private static final float RATIO = 0.7f * OUTPUT_WIDTH / OUTPUT_HEIGHT;
    private Chronometer chronometer;
    private int frontCamera = 0; // 0 is back camera，1 is front camera
    private Button btn_switch;
    private SurfaceHolder mSurfaceHolder;
    int defaultVideoFrameRate = -1;
    private int mTimeCount;// 时间计数
    private Timer mTimer;
    private ProgressBar mProgressBar;
    private Camera.Size mPreviewSize;
    private int maxtime = 6;
    private boolean mAtRemove;
    private boolean mContinueMove;
    private View movecancel;
    private View upcancel;
    private float touchpoint;
    private boolean is_failed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//		requestWindowFeature(Window.FEATURE_NO_TITLE);// no title
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);// full screen
        // translucency mode，used in surface view
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        if (!CameraHelper.checkCameraHardware(this)) {
            Toast.makeText(this, "找不到相机！", Toast.LENGTH_SHORT).show();
            return;
        }

        int cameraId = CameraHelper.getDefaultCameraID();
        if (!CameraHelper.isCameraFacingBack(cameraId)) {
            Toast.makeText(this, "找不到后置相机！", Toast.LENGTH_SHORT).show();
            return;
        }
        // Create an instance of Camera
        mCamera = CameraHelper.getCameraInstance(cameraId);
        if (null == mCamera) {
            Toast.makeText(this, "打开相机失败！", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setupCamera(cameraId);
        setContentView(R.layout.em_recorder_activity);
        maxtime = getIntent().getIntExtra("max_time", 6);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                CLASS_LABEL);
        mWakeLock.acquire();
        initViews();
    }

    private void initViews() {
        btn_switch = (Button) findViewById(R.id.switch_btn);
        findViewById(R.id.back).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
        btn_switch.setOnClickListener(this);
        btn_switch.setVisibility(View.VISIBLE);
        frameLayout = (FrameLayout) findViewById(R.id.mVideoView);
        btnStart = (TextView) findViewById(R.id.recorder_start);
        movecancel = findViewById(R.id.move_cancel);
        upcancel = findViewById(R.id.up_cancel);
        CameraPreview mPreview = new CameraPreview(this, mCamera);
        frameLayout.addView(mPreview);
        // 根据需要输出的视频大小调整预览视图高度
        frameLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                frameLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                ViewGroup.LayoutParams layoutParams = frameLayout.getLayoutParams();
                layoutParams.height = (int) (frameLayout.getWidth() / RATIO);
                frameLayout.setLayoutParams(layoutParams);
            }
        });
        mSurfaceHolder = mPreview.getHolder();
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mProgressBar.setMax(maxtime);
        chronometer = (Chronometer) findViewById(R.id.chronometer);
        btnStart.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // TODO Auto-generated method stub
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    touchpoint = event.getY();
                    startRecording();
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (event.getY() - touchpoint < -100) {
                        mAtRemove = true;
                    } else {
                        mAtRemove = false;
                    }
                    if (event.getY() - touchpoint < -220) {
                        mContinueMove = true;
                    } else {
                        mContinueMove = false;
                    }
                    if (mContinueMove) {
                        movecancel.setVisibility(View.GONE);
                        upcancel.setVisibility(View.VISIBLE);
                    } else {
                        if (mAtRemove) {
                            movecancel.setVisibility(View.VISIBLE);
                            upcancel.setVisibility(View.GONE);
                        } else {
                            upcancel.setVisibility(View.GONE);
                        }
                    }
                }
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (mContinueMove) {
                        if (mediaRecorder != null) {
                            mediaRecorder.setOnErrorListener(null);
                            mediaRecorder.setOnInfoListener(null);
                            try {
                                mediaRecorder.stop();
                            } catch (Exception e) {
                                is_failed = true;
                            }
                        }
                        releaseRecorder();
                        if (mTimer != null) {
                            mTimer.cancel();
                        }
                        btn_switch.setVisibility(View.VISIBLE);
                        chronometer.stop();
                        movecancel.setVisibility(View.GONE);
                        upcancel.setVisibility(View.GONE);
                        mTimer.cancel();
                        mProgressBar.setProgress(0);
                        mTimeCount = 0;
                        mTimer = null;
                        if (localPath != null) {
                            File file = new File(localPath);
                            if (file.exists())
                                file.delete();
                        }
                    } else {
                        if (mTimeCount < maxtime)
                            handler.sendEmptyMessage(1);
                    }
                }
                return true;
            }
        });
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            stopRecording();
            btn_switch.setVisibility(View.VISIBLE);
            chronometer.stop();
            if (is_failed) {
                showShortTimeDialog();
                return;
            }
            new AlertDialog.Builder(RecorderVideoActivity.this)
                    .setMessage(R.string.Whether_to_send)
                    .setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    dialog.dismiss();
                                    sendVideo(null);

                                }
                            })
                    .setNegativeButton(R.string.cancel,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    if (localPath != null) {
                                        File file = new File(localPath);
                                        if (file.exists())
                                            file.delete();
                                    }
                                    finish();

                                }
                            }).setCancelable(false).show();
        }
    };

    public void back(View view) {
        releaseRecorder();
        releaseCamera();
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWakeLock == null) {
            // keep screen on
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                    CLASS_LABEL);
            mWakeLock.acquire();
        }
    }

    /**
     * 设置相机参数
     */
    private void setupCamera(int cameraId) {
        // 设置相机方向
        CameraHelper.setCameraDisplayOrientation(this, cameraId, mCamera);
        // 设置相机参数
        Camera.Parameters parameters = mCamera.getParameters();
        // 若应用就是用来录制视频的，不用拍照功能，设置RecordingHint可以加快录制启动速度
        // 问题：小米手机录制视频支持的Size和相机预览支持的Size不一样（其他类似的手机可能
        // 也存在这个问题），若设置了这个标志位，会使预览效果拉伸，但是开始录制视频，预览
        // 又恢复正常，暂时不知道是为什么
        parameters.setRecordingHint(true);
//        //获取到设备支持的对焦模式
//        List<String> focusModes = parameters.getSupportedFocusModes();
//        if (focusModes != null && focusModes.size() > 0) {
//            if (focusModes.contains("continuous-video"))
//                parameters.setFocusMode("continuous-video");
//        }
        parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        parameters.setFocusAreas(null);
        parameters.setMeteringAreas(null);
        Size mVideoSize = CameraHelper.getCameraPreviewSizeForVideo(cameraId, mCamera);
//        parameters.setPreviewSize(mVideoSize.width, mVideoSize.height);
        mCamera.setParameters(parameters);
    }

//    @SuppressLint("NewApi")
//    private boolean initCamera() {
//        try {
//            if (frontCamera == 0) {
//                mCamera = Camera.open(CameraInfo.CAMERA_FACING_BACK);
//            } else {
//                mCamera = Camera.open(CameraInfo.CAMERA_FACING_FRONT);
//            }
//            Camera.Parameters camParams = mCamera.getParameters();
//            mCamera.lock();
//            mSurfaceHolder = mVideoView.getHolder();
//            mSurfaceHolder.addCallback(this);
//            mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
//            List<Size> mSupportedPreviewSizes = camParams.getSupportedPreviewSizes();
//            if (mSupportedPreviewSizes != null) {
//                mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes,
//                        MeasureUtil.getScreenWidth(), MeasureUtil.getScreenHeight() / 2);
//            }
//            camParams.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
//            //获取到设备支持的对焦模式
//            List<String> focusModes = camParams.getSupportedFocusModes();
//            if (focusModes != null && focusModes.size() > 0) {
//                if (focusModes.contains("continuous-video"))
//                    camParams.setFocusMode("continuous-video");
//            }
//            camParams.setRecordingHint(true);
//            mCamera.setDisplayOrientation(90);
//            mCamera.setParameters(camParams);
//        } catch (RuntimeException ex) {
//            EMLog.e("video", "init Camera fail " + ex.getMessage());
//            return false;
//        }
//        return true;
//    }

    private void handleSurfaceChanged() {
        if (mCamera == null) {
            finish();
            return;
        }
        boolean hasSupportRate = false;
        List<Integer> supportedPreviewFrameRates = mCamera.getParameters()
                .getSupportedPreviewFrameRates();
        if (supportedPreviewFrameRates != null
                && supportedPreviewFrameRates.size() > 0) {
            Collections.sort(supportedPreviewFrameRates);
            for (int i = 0; i < supportedPreviewFrameRates.size(); i++) {
                int supportRate = supportedPreviewFrameRates.get(i);

                if (supportRate == 15) {
                    hasSupportRate = true;
                }

            }
            if (hasSupportRate) {
                defaultVideoFrameRate = 15;
            } else {
                defaultVideoFrameRate = supportedPreviewFrameRates.get(0);
            }

        }

        // get all resolutions which camera provide
        List<Size> resolutionList = Utils.getResolutionList(mCamera);
        if (resolutionList != null && resolutionList.size() > 0) {
            Collections.sort(resolutionList, new Utils.ResolutionComparator());
            Camera.Size previewSize = null;
            boolean hasSize = false;

            // use 60*480 if camera support
            for (int i = 0; i < resolutionList.size(); i++) {
                Size size = resolutionList.get(i);
                if (size != null && size.width == 640 && size.height == 480) {
                    previewSize = size;
                    previewWidth = previewSize.width;
                    previewHeight = previewSize.height;
                    hasSize = true;
                    break;
                }
            }
            // use medium resolution if camera don't support the above resolution
            if (!hasSize) {
                int mediumResolution = resolutionList.size() / 2;
                if (mediumResolution >= resolutionList.size())
                    mediumResolution = resolutionList.size() - 1;
                previewSize = resolutionList.get(mediumResolution);
                previewWidth = previewSize.width;
                previewHeight = previewSize.height;

            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
        if (mTimer != null)
            mTimer.cancel();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.switch_btn:
                switchCamera();
                break;
            default:
                break;
        }
    }


//    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
//        Log.d(TAG, "surfaceChanged w: " + w + "---h: " + h);
//
//        // If your preview can change or rotate, take care of those events here.
//        // Make sure to stop the preview before resizing or reformatting it.
//
//        if (holder.getSurface() == null) {
//            // preview surface does not exist
//            return;
//        }
//
//        // stop preview before making changes
//        try {
//            mCamera.stopPreview();
//        } catch (Exception e) {
//            // ignore: tried to stop a non-existent preview
//        }
//
//        // set preview size and make any resize, rotate or
//        // reformatting changes here
//        Camera.Parameters parameters = mCamera.getParameters();
//        Camera.Size size = getOptimalPreviewSize(parameters.getSupportedPreviewSizes(), Math.min(w, h));
//        Log.d(TAG, "OptimalPreviewSize w: " + size.width + "---h: " + size.height);
//        parameters.setPreviewSize(size.width, size.height);
//        mCamera.setParameters(parameters);
//        // 预览尺寸改变，请求重新布局、计算宽高
//        mVideoView.requestLayout();
//
////        for (PreviewEventListener previewEventListener : mPreviewEventListenerList)
////            previewEventListener.onPrePreviewStart();
//
//        // start preview with new settings
//        try {
//            mCamera.setPreviewDisplay(holder);
//            mCamera.startPreview();
//
////            for (PreviewEventListener previewEventListener : mPreviewEventListenerList)
////                previewEventListener.onPreviewStarted();
////
////            if (!isIndicatorShowed) {
////                mIndicatorView.startAnimation(mIndicatorAnimation);
////                isIndicatorShowed = true;
////            }
////            focusOnTouch(CameraPreviewView.this.getWidth() / 2f, CameraPreviewView.this.getHeight() / 2f);
//        } catch (Exception e) {
////            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
////            for (PreviewEventListener previewEventListener : mPreviewEventListenerList)
////                previewEventListener.onPreviewFailed();
//        }
//    }
//
//    @Override
//    public void surfaceCreated(SurfaceHolder holder) {
////        if (mCamera == null) {
////            showFailDialog();
////            return;
////        }
////        try {
////            mCamera.setPreviewDisplay(mSurfaceHolder);
////            mCamera.startPreview();
////            handleSurfaceChanged();
////        } catch (Exception e1) {
////            EMLog.e("video", "start preview fail " + e1.getMessage());
////            showFailDialog();
////        }
//    }
//
//    @Override
//    public void surfaceDestroyed(SurfaceHolder arg0) {
//        EMLog.v("video", "surfaceDestroyed");
//    }

    public boolean startRecording() {
        if (mediaRecorder == null) {
            if (!initRecorder())
                return false;
        }
        mediaRecorder.setOnInfoListener(this);
        mediaRecorder.setOnErrorListener(this);
        try {
            mediaRecorder.start();
        } catch (Exception e) {
            stopRecording();
        }
        btn_switch.setVisibility(View.GONE);
        return true;
    }
    /**
     * check if sdcard exist
     *
     * @return
     */
    public static boolean isSdcardExist() {
        if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED))
            return true;
        else
            return false;
    }

    @SuppressLint("NewApi")
    private boolean initRecorder() {
        if (!isSdcardExist()) {
            showNoSDCardDialog();
            return false;
        }

        if (mCamera == null) {
            showFailDialog();
            return false;
        }
//        mVideoView.setVisibility(View.VISIBLE);
        mCamera.stopPreview();
        mediaRecorder = new MediaRecorder();
        mCamera.unlock();
        mediaRecorder.setCamera(mCamera);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        if (frontCamera == 1) {
            mediaRecorder.setOrientationHint(270);
        } else {
            mediaRecorder.setOrientationHint(90);
        }

        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoEncodingBitRate(1 * 1024 * 512);
        // set resolution, should be set after the format and encoder was set
        mediaRecorder.setVideoSize(previewWidth, previewHeight);
        // set frame rate, should be set after the format and encoder was set
        if (defaultVideoFrameRate != -1) {
            mediaRecorder.setVideoFrameRate(defaultVideoFrameRate);
        }
        // set the path for video file
        localPath = Environment.getExternalStoragePublicDirectory("") + "/"
                + System.currentTimeMillis() + ".mp4";
        mediaRecorder.setOutputFile(localPath);
        mediaRecorder.setMaxDuration(30000);
        mediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
        try {
            mediaRecorder.prepare();
            mTimer = new Timer();
            mTimer.schedule(new TimerTask() {

                @Override
                public void run() {
                    // TODO Auto-generated method stub
                    mTimeCount++;
                    mProgressBar.setProgress(mTimeCount);// 设置进度条
                    if (mTimeCount == maxtime) {// 达到指定时间，停止拍摄
                        if (!mContinueMove) {
                            handler.sendEmptyMessage(1);
                        }
                    }
                }
            }, 0, 1000);
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;

    }

    public void stopRecording() {
        if (mediaRecorder != null) {
            mediaRecorder.setOnErrorListener(null);
            mediaRecorder.setOnInfoListener(null);
            try {
                mediaRecorder.stop();
            } catch (Exception e) {
                is_failed = true;
            }
        }
        releaseRecorder();
        if (mTimer != null) {
            mTimer.cancel();
        }
        if (mCamera != null) {
            mCamera.stopPreview();
            releaseCamera();
        }
    }

    private void releaseRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    protected void releaseCamera() {
        try {
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }
        } catch (Exception e) {
        }
    }

    @SuppressLint("NewApi")
    public void switchCamera() {

        if (mCamera == null) {
            return;
        }
        if (Camera.getNumberOfCameras() >= 2) {
            btn_switch.setEnabled(false);
            if (mCamera != null) {
                mCamera.setPreviewCallbackWithBuffer(null);
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }

            switch (frontCamera) {
                case 0:
                    mCamera = CameraHelper.getCameraInstance(CameraHelper.getFrontCameraID());
                    setupCamera(CameraHelper.getFrontCameraID());
                    frontCamera = 1;
                    break;
                case 1:
                    mCamera = CameraHelper.getCameraInstance(CameraHelper.getDefaultCameraID());
                    setupCamera(CameraHelper.getDefaultCameraID());
                    frontCamera = 0;
                    break;
            }
            CameraPreview mPreview = new CameraPreview(this, mCamera);
            frameLayout.removeAllViews();
            frameLayout.addView(mPreview);
            // 根据需要输出的视频大小调整预览视图高度
            frameLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    frameLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    ViewGroup.LayoutParams layoutParams = frameLayout.getLayoutParams();
                    layoutParams.height = (int) (frameLayout.getWidth() / RATIO);
                    frameLayout.setLayoutParams(layoutParams);
                }
            });
            mSurfaceHolder = mPreview.getHolder();
            mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
//            try {
//                mCamera.lock();
//                mCamera.setDisplayOrientation(90);
//                mCamera.setPreviewDisplay(mSurfaceHolder);
//                mCamera.startPreview();
//            } catch (IOException e) {
//                mCamera.release();
//                mCamera = null;
//            }
            btn_switch.setEnabled(true);

        }

    }

    MediaScannerConnection msc = null;
    ProgressDialog progressDialog = null;

    public void sendVideo(View view) {
        if (TextUtils.isEmpty(localPath)) {
            return;
        }
        if (msc == null)
            msc = new MediaScannerConnection(this,
                    new MediaScannerConnectionClient() {

                        @Override
                        public void onScanCompleted(String path, Uri uri) {
                            msc.disconnect();
                            progressDialog.dismiss();
                            setResult(RESULT_OK, getIntent().putExtra("uri", uri));
                            finish();
                        }

                        @Override
                        public void onMediaScannerConnected() {
                            msc.scanFile(localPath, "video/*");
                        }
                    });


        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("processing...");
            progressDialog.setCancelable(false);
        }
        progressDialog.show();
        msc.connect();

    }

    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            stopRecording();
            btn_switch.setVisibility(View.VISIBLE);
            chronometer.stop();
            chronometer.stop();
            if (localPath == null) {
                return;
            }
            String st3 = getResources().getString(R.string.Whether_to_send);
            new AlertDialog.Builder(this)
                    .setMessage(st3)
                    .setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface arg0,
                                                    int arg1) {
                                    arg0.dismiss();
                                    sendVideo(null);

                                }
                            }).setNegativeButton(R.string.cancel, null)
                    .setCancelable(false).show();
        }

    }

    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        stopRecording();
        Toast.makeText(this,
                "Recording error has occurred. Stopping the recording",
                Toast.LENGTH_SHORT).show();

    }

    public void saveBitmapFile(Bitmap bitmap) {
        File file = new File(Environment.getExternalStorageDirectory(), "a.jpg");
        try {
            BufferedOutputStream bos = new BufferedOutputStream(
                    new FileOutputStream(file));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseCamera();

        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }

    }

    @Override
    public void onBackPressed() {
        back(null);
    }

    private void showFailDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.prompt)
                .setMessage(R.string.Open_the_equipment_failure)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                finish();

                            }
                        }).setCancelable(false).show();

    }

    private void showNoSDCardDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.prompt)
                .setMessage("No sd card!")
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                finish();

                            }
                        }).setCancelable(false).show();
    }

    private void showShortTimeDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.prompt)
                .setMessage("录制时间太短")
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                movecancel.setVisibility(View.GONE);
                                upcancel.setVisibility(View.GONE);
                                mTimer.cancel();
                                mProgressBar.setProgress(0);
                                mTimeCount = 0;
                                mTimer = null;
                                if (localPath != null) {
                                    File file = new File(localPath);
                                    if (file.exists())
                                        file.delete();
                                }
                                is_failed = false;

                                switch (frontCamera) {
                                    case 1:
                                        mCamera = CameraHelper.getCameraInstance(CameraHelper.getFrontCameraID());
                                        setupCamera(CameraHelper.getFrontCameraID());
                                        frontCamera = 1;
                                        break;
                                    case 0:
                                        mCamera = CameraHelper.getCameraInstance(CameraHelper.getDefaultCameraID());
                                        setupCamera(CameraHelper.getDefaultCameraID());
                                        frontCamera = 0;
                                        break;
                                }
                                CameraPreview mPreview = new CameraPreview(RecorderVideoActivity.this, mCamera);
                                frameLayout.removeAllViews();
                                frameLayout.addView(mPreview);
                                // 根据需要输出的视频大小调整预览视图高度
                                frameLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                                    @Override
                                    public void onGlobalLayout() {
                                        frameLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                                        ViewGroup.LayoutParams layoutParams = frameLayout.getLayoutParams();
                                        layoutParams.height = (int) (frameLayout.getWidth() / RATIO);
                                        frameLayout.setLayoutParams(layoutParams);
                                    }
                                });
                                mSurfaceHolder = mPreview.getHolder();
                                mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                                dialog.dismiss();
                            }
                        }).setCancelable(false).show();
    }
}
