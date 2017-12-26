package com.grt.york.opencvwithqrcode;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.List;

public class OpenCVActivity extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2, View.OnTouchListener{

    //OpenCV parameter
    private static final String TAG = "OCVSample::Activity";

    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat mRgba;
    private Mat mThresholdMat;
    private Mat mIntermediateMat;
    private Mat mYellowMat;
    private Mat mHsvMat;
    private MatOfPoint points;
    private Mat hierarchy;
    private MatOfPoint2f approxCurve;
    private Mat temp_contour;
    private List<MatOfPoint> contours;
    private boolean isParsing = false;
    private double mWidth = 0 , mHeight = 0;
    private Thread mThread = null;
    Bitmap bMap;
    int[] intArray;
    Reader reader;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(OpenCVActivity.this);

                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_open_cv);
        //Remove notification bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //OpenCV initial
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.opencv_activity_java_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        return false;
    }

    //OpenCV sample code
    public void onCameraViewStarted(int width, int height) {
        mWidth = width;
        mHeight = height;
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mIntermediateMat = new Mat(height, width, CvType.CV_8UC4);
        hierarchy = new Mat();
        mThresholdMat = new Mat();
        approxCurve = new MatOfPoint2f();
        mHsvMat = new Mat();
        mYellowMat = new Mat();
        bMap = Bitmap.createBitmap(mRgba.width(), mRgba.height(), Bitmap.Config.ARGB_8888);
        intArray = new int[bMap.getWidth()*bMap.getHeight()];
        reader = new QRCodeMultiReader();
//        mThread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//            while(true) {
//                if (isParsing) {
//                    if (squareShape()) {
//                        Intent rIntent = new Intent();
//                        setResult(Activity.RESULT_OK, rIntent);
//                        finish();
//                    }
//                    isParsing = false;
//                }
//            }
//            }
//        });
//        mThread.start();
    }

    public void onCameraViewStopped() {
        mRgba.release();
        mIntermediateMat.release();
        hierarchy.release();
        mThresholdMat.release();
        approxCurve.release();
        mHsvMat.release();
        mYellowMat.release();
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        mRgba = inputFrame.rgba();
        Imgproc.rectangle(
                mRgba,
                new Point(mWidth/4, mHeight/8*5),
                new Point(mWidth/4*3, mHeight/8*7),
                new Scalar(156, 43, 46),
                3
        );

        Imgproc.rectangle(
                mRgba,
                new Point(mWidth/4, mHeight/8*1),
                new Point(mWidth/4*3, mHeight/8*3),
                new Scalar(156, 43, 46),
                3
        );

        if (!isParsing) {
            isParsing = true;
        }

        return mRgba;

    }

    private boolean squareShape() {

        if (mRgba == null) {
            return false;
        }
        boolean isSquare = false;
        contours = new ArrayList<MatOfPoint>();
        try {
            //尋找顏色黃綠色方塊
            Imgproc.cvtColor(mRgba, mHsvMat, Imgproc.COLOR_BGR2HSV);
            Core.inRange(mHsvMat, new Scalar(60, 43, 46), new Scalar(100, 255, 255), mYellowMat);

            //在尋找四邊形
            Imgproc.GaussianBlur(mYellowMat, mThresholdMat, new Size(3, 3), 0);//高斯模糊3X3的九宮格
            Imgproc.Canny(mThresholdMat, mIntermediateMat, 80, 100);//Canny邊緣檢測器檢測圖像邊緣
            Imgproc.findContours(mIntermediateMat,
                    contours,
                    hierarchy,
                    Imgproc.RETR_EXTERNAL,
                    Imgproc.CHAIN_APPROX_SIMPLE,
                    new Point(0, 0)
            );

            if (contours.size() > 0) {

                for (int i = 0; i < contours.size(); i++) {
                    MatOfPoint2f contour2f = new MatOfPoint2f(contours.get(i).toArray());
                    double approxDistance = Imgproc.arcLength(contour2f, true) * 0.02;
                    Imgproc.approxPolyDP(contour2f, approxCurve, approxDistance, true);


                    if (!contours.get(i).empty()) {
                        temp_contour = contours.get(i);
                        double contourArea = Imgproc.contourArea(temp_contour);
                        MatOfPoint mat = new MatOfPoint();
                        approxCurve.convertTo(mat, CvType.CV_32S);
                        if (contourArea < 1000 || !Imgproc.isContourConvex(mat)) {
                            continue;
                        }
                        points = new MatOfPoint(approxCurve.toArray());
                        if (points.toArray().length == 4 ||
                                points.toArray().length == 5 ) {
                            Imgproc.drawContours(mRgba, contours, i, new Scalar(227, 43, 51), 3);
                            isSquare = true;
                        }
                    }
                }
            }
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }
        return isSquare;
    }

}
