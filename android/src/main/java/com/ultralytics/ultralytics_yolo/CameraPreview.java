package com.ultralytics.ultralytics_yolo;

import android.app.Activity;
import android.content.Context;
import android.util.Size;

import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.ultralytics.ultralytics_yolo.predict.Predictor;

import java.util.List;
import java.util.concurrent.ExecutionException;


public class CameraPreview {
    public final static Size CAMERA_PREVIEW_SIZE = new Size(640, 480);
    private final Context context;
    private Predictor predictor;
    private ProcessCameraProvider cameraProvider;
    private CameraControl cameraControl;
    private Activity activity;
    private PreviewView mPreviewView;
    private boolean busy = false;

    public CameraPreview(Context context) {
        this.context = context;
    }

    public void openCamera(int facing, Activity activity, PreviewView mPreviewView) {
        this.activity = activity;
        this.mPreviewView = mPreviewView;

        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreview(facing);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindPreview(int facing) {
        if (!busy) {
            busy = true;

            Preview cameraPreview = new Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build();

             CameraSelector cameraSelector = null;
            if (facing == 1) { // Assume 2 is wide lens
                List<CameraInfo> availableCameraInfos = cameraProvider.getAvailableCameraInfos();
                cameraSelector = availableCameraInfos.get(2).getCameraSelector();
                if (cameraSelector == null) {
                    // Fallback to back camera if wide lens is unavailable
                    cameraSelector = new CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build();
                }
            } else {
                cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(facing)
                        .build();
            }
            // jksong 0705. Operation Toggle Lens to Select Camera Lenz
            // if (facing == 2) {
            //     List<CameraInfo> availableCameraInfos = cameraProvider.getAvailableCameraInfos();
            //     cameraSelector = availableCameraInfos.get(2).getCameraSelector();
            // }
            // else {
            //     cameraSelector = new CameraSelector.Builder()
            //         .requireLensFacing(facing)
            //         .build();
            // }
            

            ImageAnalysis imageAnalysis =
                    new ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                            .build();
            imageAnalysis.setAnalyzer(Runnable::run, imageProxy -> {
                // jksong 0705. Operation Toggle Lens to Select Camera Lens, 
                // No need to consider mirroring
                predictor.predict(imageProxy, facing == CameraSelector.LENS_FACING_FRONT);
                // predictor.predict(imageProxy, false);

                //clear stream for next image
                imageProxy.close();

                // add sleep for 8.x FPS by jksong , 100 msec
                try {
                    //Thread.sleep(100); // 1.x FPS
                    Thread.sleep(75); // 10.x FPS
                }catch(Exception e)
                {
                    System.out.println(e);
                }
            });

            // Unbind use cases before rebinding
            cameraProvider.unbindAll();

            // Bind use cases to camera
            Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) activity, cameraSelector, cameraPreview, imageAnalysis);

            cameraControl = camera.getCameraControl();

            cameraPreview.setSurfaceProvider(mPreviewView.getSurfaceProvider());

            busy = false;
        }
    }

    public void setPredictorFrameProcessor(Predictor predictor) {
        this.predictor = predictor;
    }

    public void setCameraFacing(int facing) { bindPreview(facing); }

    public void setScaleFactor(double factor) {
        cameraControl.setZoomRatio((float)factor);
    }
}
