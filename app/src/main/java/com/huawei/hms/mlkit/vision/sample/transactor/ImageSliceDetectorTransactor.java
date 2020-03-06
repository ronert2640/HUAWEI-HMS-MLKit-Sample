/**
 * Copyright 2020. Huawei Technologies Co., Ltd. All rights reserved.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.huawei.hms.mlkit.vision.sample.transactor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.huawei.hmf.tasks.Task;
import com.huawei.hms.mlkit.vision.sample.callback.ImageSegmentationResultCallBack;
import com.huawei.hms.mlkit.vision.sample.camera.CameraConfiguration;
import com.huawei.hms.mlkit.vision.sample.camera.FrameMetadata;
import com.huawei.hms.mlkit.vision.sample.util.EdgeDetection;
import com.huawei.hms.mlkit.vision.sample.views.graphic.CameraImageGraphic;
import com.huawei.hms.mlkit.vision.sample.views.overlay.GraphicOverlay;
import com.huawei.hms.mlsdk.MLAnalyzerFactory;
import com.huawei.hms.mlsdk.common.MLFrame;
import com.huawei.hms.mlsdk.imgseg.MLImageSegmentation;
import com.huawei.hms.mlsdk.imgseg.MLImageSegmentationAnalyzer;
import com.huawei.hms.mlsdk.imgseg.MLImageSegmentationSetting;

import java.io.IOException;

/**
 * A transactor to run object detector.
 */
public class ImageSliceDetectorTransactor extends BaseTransactor<MLImageSegmentation> {

    private static final String TAG = ImageSliceDetectorTransactor.class.getSimpleName();

    private final MLImageSegmentationAnalyzer detector;
    private boolean isBackgroundReplaced;
    private Context context;
    private Bitmap foregroundBitmap, backgroundBitmap;
    private int facing;
    private boolean[] resultArray;
    private String savePath = "";
    private ImageSegmentationResultCallBack imageSegmentationResultCallBack;

    /**
     * Constructor for real-time replacement background.
     *
     * @param context              context.
     * @param options              options.
     * @param isBackgroundReplaced if replace background.
     * @param backgroundBitmap     background image.
     * @param facing
     */
    public ImageSliceDetectorTransactor(Context context, MLImageSegmentationSetting options, boolean isBackgroundReplaced, Bitmap backgroundBitmap, int facing) {
        this.context = context;
        MLAnalyzerFactory.getInstance().getImageSegmentationAnalyzer();
        this.detector = MLAnalyzerFactory.getInstance().getImageSegmentationAnalyzer(options);
        this.isBackgroundReplaced = isBackgroundReplaced;
        this.backgroundBitmap = backgroundBitmap;
        this.facing = facing;
        this.clearPath();
    }

    public void setSavePath(String path) {
        this.savePath = path;
    }

    public void clearPath() {
        this.savePath = "";
    }

    // Return to processed image.
    public void setImageSegmentationResultCallBack(ImageSegmentationResultCallBack imageSegmentationResultCallBack) {
        this.imageSegmentationResultCallBack = imageSegmentationResultCallBack;
    }

    @Override
    public void stop() {
        super.stop();
        try {
            this.detector.stop();
        } catch (IOException e) {
            Log.e(ImageSliceDetectorTransactor.TAG, "Exception thrown while trying to close object detector!", e);
        }
    }

    Long startTime = 0L;

    @Override
    protected Task<MLImageSegmentation> detectInImage(MLFrame frame) {
        this.startTime = System.currentTimeMillis();
        Task<MLImageSegmentation> task = this.detector.asyncAnalyseFrame(frame);
        return task;
    }

    protected SparseArray<MLImageSegmentation> analyseFrame(MLFrame frame) {
        return this.detector.analyseFrame(frame);
    }

    @Override
    protected void onSuccess(
            @Nullable Bitmap originalCameraImage,
            @NonNull MLImageSegmentation results,
            @NonNull FrameMetadata frameMetadata,
            @NonNull GraphicOverlay graphicOverlay) {
        this.startTime = System.currentTimeMillis();
        graphicOverlay.clear();
        if (results == null || results.getMasks() == null) {
            Log.i(TAG, "detection failed");
            return;
        }
        byte[] masks = results.getMasks();
        if(masks == null){
            return;
        }
        // Replace background.
        if (this.isBackgroundReplaced) {
            this.foregroundBitmap = originalCameraImage;
            // Perform front camera mirror conversion.
            if (this.facing == CameraConfiguration.CAMERA_FACING_FRONT) {
                this.foregroundBitmap = this.convert(originalCameraImage);
            }
            Bitmap resultBitmap = this.changeNextBackground(masks);
            if (this.facing == CameraConfiguration.CAMERA_FACING_FRONT) {
                resultBitmap = this.convert(resultBitmap);
            }
            if (this.imageSegmentationResultCallBack != null) {
                this.imageSegmentationResultCallBack.callResultBitmap(resultBitmap);
            }
            CameraImageGraphic imageGraphic = new CameraImageGraphic(graphicOverlay, resultBitmap);
            graphicOverlay.addGraphic(imageGraphic);
        }
        graphicOverlay.postInvalidate();

    }

    @Override
    protected void onFailure(@NonNull Exception e) {
        Log.e(ImageSliceDetectorTransactor.TAG, "Object detection failed!", e);
    }

    /**
     * Replace the images in the assets directory as the background image in order.
     */
    private Bitmap changeNextBackground(byte[] masks) {
        Bitmap result;
        if (this.backgroundBitmap == null) {
            Toast.makeText(this.context, "No Background Image", Toast.LENGTH_SHORT).show();
            throw new NullPointerException("No background image");
        }

        if (!this.equalToForegroundImageSize()) {
            this.backgroundBitmap = this.resizeImageToForegroundImage(this.backgroundBitmap);
        }
        int[] pixels = new int[this.backgroundBitmap.getWidth() * this.backgroundBitmap.getHeight()];
        this.backgroundBitmap.getPixels(pixels, 0, this.backgroundBitmap.getWidth(), 0, 0, this.backgroundBitmap.getWidth(), this.backgroundBitmap.getHeight());
        //todo:masks
        result = this.doMaskOnBackgroundImage(masks);
        return result;
    }

    /**
     * Process the picture according to the mask value of the returned classification.
     */
    private Bitmap doMaskOnBackgroundImage(byte[] maskByte) {
        if (this.backgroundBitmap == null) {
            Toast.makeText(this.context, "No Background Image", Toast.LENGTH_SHORT).show();
            return null;
        }

        //todo;
        int[] masks = this.byteArrToIntArr(maskByte);
        int[][] twoMasks = this.oneToTwoArray(masks);
        boolean[][] detectArray = new boolean[twoMasks.length][twoMasks[0].length];
        EdgeDetection edgeDetection = new EdgeDetection(twoMasks, detectArray);
        edgeDetection.setArray(twoMasks);
        edgeDetection.setLabel(1);
        this.resultArray = edgeDetection.detect();
        Log.i(ImageSliceDetectorTransactor.TAG, "foreground width:" + this.foregroundBitmap.getWidth() + ",foreground height:" + this.foregroundBitmap.getHeight());
        Log.i(ImageSliceDetectorTransactor.TAG, "background width:" + this.backgroundBitmap.getWidth() + ",foreground height:" + this.backgroundBitmap.getHeight());

        int[] foregroundPixels = new int[this.foregroundBitmap.getWidth() * this.foregroundBitmap.getHeight()];
        int[] backgroundPixels = new int[this.backgroundBitmap.getWidth() * this.backgroundBitmap.getHeight()];
        this.foregroundBitmap.getPixels(foregroundPixels, 0, this.foregroundBitmap.getWidth(), 0, 0, this.foregroundBitmap.getWidth(), this.foregroundBitmap.getHeight());
        this.backgroundBitmap.getPixels(backgroundPixels, 0, this.backgroundBitmap.getWidth(), 0, 0, this.backgroundBitmap.getWidth(), this.backgroundBitmap.getHeight());

        for (int i = 0; i < masks.length; i++) {
            if (masks[i] == 0) {
                foregroundPixels[i] = backgroundPixels[i];
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(foregroundPixels, 0, this.foregroundBitmap.getWidth(), this.foregroundBitmap.getWidth(), this.foregroundBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        BitmapDrawable bitmapDrawable = new BitmapDrawable(bitmap);
        bitmapDrawable.setAntiAlias(true);
        return bitmap;
    }

    /**
     * Stretch background image size to foreground image's.
     *
     * @param bitmap
     * @return
     */
    private Bitmap resizeImageToForegroundImage(Bitmap bitmap) {
        float scaleWidth = ((float) this.foregroundBitmap.getWidth() / bitmap.getWidth());
        float scaleHeigth = ((float) this.foregroundBitmap.getHeight() / bitmap.getHeight());
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeigth);

        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        return bitmap;
    }

    private boolean equalToForegroundImageSize() {
        Log.i(ImageSliceDetectorTransactor.TAG, "FOREGREOUND SIZE;" + this.foregroundBitmap.getWidth() + ", height:" + this.foregroundBitmap.getHeight());
        return this.backgroundBitmap.getHeight() == this.foregroundBitmap.getHeight() && this.backgroundBitmap.getWidth() == this.foregroundBitmap.getWidth();
    }

    private int[] byteArrToIntArr(byte[] masks) {
        int[] results = new int[masks.length];
        for (int i = 0; i < masks.length; i++) {
            results[i] = masks[i];
        }
        return results;
    }

    /**
     * One-dimensional array converted into two-dimensional image coordinates.
     */
    private int[][] oneToTwoArray(int[] one) {
        int[][] two = new int[this.foregroundBitmap.getHeight()][this.foregroundBitmap.getWidth()];
        for (int index = 0; index < one.length; index++) {
            int row = index / this.foregroundBitmap.getWidth();
            int col = index % this.foregroundBitmap.getWidth();
            two[row][col] = one[index];
        }
        return two;
    }

    /**
     * Front camera image position changed.
     */
    private Bitmap convert(Bitmap bitmap) {
        Matrix m = new Matrix();
        m.setScale(-1, 1);// horizontal flip.
        Bitmap reverseBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
        return reverseBitmap;
    }

    @Override
    public boolean isImageSegment() {
        return true;
    }
}
