package com.paulnpete.emergency.lifeline;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceView;

public class PhotoCapture extends Service {
	protected ArrayList<Camera> mCameras = new ArrayList<Camera>();
	Intent uploadService;

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		uploadService = new Intent(this, UploadFile.class);
	}

	@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
		int numCams = openCameras();
		Log.i("PhotoCapture",numCams+" cameras available");
		capturePhotos();
        return START_STICKY;
	}
	
	@Override
	public void onDestroy() {
		releaseCameras();
		super.onDestroy();
	}
	
	private int openCameras() {
		int openedCams = 0;
		releaseCameras();
		int numCams = Camera.getNumberOfCameras();
		for(int i=0;i<numCams;i++) {
			try {
				Camera mCamera = Camera.open(i);
				openedCams++;
				mCameras.add(i,mCamera);
				Log.v(getString(R.string.app_name), "successfully opened Camera "+i);
			} catch (Exception e) {
				Log.e(getString(R.string.app_name), "failed to open Camera "+i);
				Log.e(getString(R.string.app_name), e.getMessage());
			}
		}
		return openedCams;
	}
	
	private void releaseCameras() {
		int numCams = mCameras.size();
		for(int i=0;i<numCams;i++) {
			Camera mCamera = mCameras.get(i);
			if (mCamera != null) {
				mCamera.release();
				mCamera = null;
			}
		}
	}
	
	private void startPhotoTimer(){
		Timer photoTimer = new Timer("photoTimer",true);
		photoTimer.schedule(new PhotoTimer(),15000);
	}

	private class PhotoTimer extends TimerTask {
		public void run(){
			Log.i("PhotoCapture","Initiating new photo");
			capturePhotos();
		}
	}
	
	private void capturePhotos() {
		int numCams = mCameras.size();
		Log.v("PhotoCapture",numCams+" cameras to use");
		for(int i=0;i<numCams;i++){
			try {
				Camera mCamera = mCameras.get(i);
				SurfaceView view = new SurfaceView(this);
	            Log.v("surface view",view.toString());
				mCamera.setPreviewDisplay(view.getHolder());
				mCamera.startPreview();
				mCamera.takePicture(null, null, jpegCallback);
				//startPhotoTimer();
			} catch (Exception e) {
				Log.e("PhotoCapture",e.getMessage());
			}
		}
	}

	PictureCallback jpegCallback = new PictureCallback() {
		public void onPictureTaken(byte[] imageData, Camera c) {
			if (imageData != null) {
				FileOutputStream outStream = null;
				try {
					File imageDir = new File(String.format(
							"%s/EmergencyLifeline/",
							Environment.getExternalStorageDirectory()
						));
					imageDir.mkdirs();
					File imageFile = new File(imageDir,
							String.format("%d.jpg", System.currentTimeMillis())
						);
					outStream = new FileOutputStream(imageFile);
					Bitmap bitmap = BitmapFactory.decodeByteArray(imageData,0,imageData.length);
				    /* Write bitmap to file using JPEG and 80% quality hint for JPEG. */
//					Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 1200, 1200, false); // memory leak? "external allocation too large for this process" -- kills app after three photos
					bitmap.compress(CompressFormat.JPEG, 80, outStream);
				    //outStream.write(imageData); // old write to OutputStream before compression method
					outStream.close();
					Log.d("PhotoCapture", "onPictureTaken - wrote bytes: "
							+ imageData.length);

					c.startPreview();
					Log.i("MediaCapture", String.format("%s written", imageFile));
					uploadService.putExtra("filepath",imageFile.toString());
					startService(uploadService);

				} catch (FileNotFoundException e) {
					stopSelf();
				} catch (IOException e) {
					stopSelf();
				}
			}
		}
	};
	
}
