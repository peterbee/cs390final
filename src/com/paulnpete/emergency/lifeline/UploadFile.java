package com.paulnpete.emergency.lifeline;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class UploadFile extends Service {
	String filepath;
	String postUrl = "http://sites.limetreecreative.com/lifeline/emergencyPost.php";
	String postQuery = "";
	Location geolocation = null;
	LocationManager locationManager = null;
	LocationListener locationListener = null;
	
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		startLocationListener();
		filepath = intent.getStringExtra("filepath");
		Bundle extras = intent.getExtras();
		Log.v("UploadFile",extras.toString());
		new UploadFileTask().execute(filepath);
		return START_REDELIVER_INTENT;
	}
	
	/*@Override
	public void onDestroy(){
		stopLocationListener();
	}*/
	
	protected void startLocationListener(){
		LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		geolocation = lastKnownLocation;
		LocationListener locationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				geolocation = location;
				stopLocationListener();
			}
			public void onStatusChanged(String provider, int status, Bundle extras) {}
			public void onProviderEnabled(String provider) {}
			public void onProviderDisabled(String provider) {}
		};
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
	}
	
	protected void stopLocationListener(){
		locationManager.removeUpdates(locationListener);
	}
	
	private class UploadFileTask extends AsyncTask<String, Void, Void> {

		protected Void doInBackground(String...paths) {
			if(paths.length < 1) return null;
			
			HttpURLConnection connection = null;
			DataOutputStream outputStream = null;

			String pathToOurFile = paths[0];
			String urlServer = postUrl;
			String lineEnd = "\n";
			String twoHyphens = "--";
			String boundary = "*****";

			int bytesRead, bytesAvailable, bufferSize;
			byte[] buffer;
			int maxBufferSize = 1*1024*1024;
			
			try {
				String filepath = paths[0];
				Log.i("UploadFile","File path: " + filepath);
				if(filepath == null) return null;
				
				// Add geolocation to post
				if(geolocation != null){
					postQuery += "&latitude="+geolocation.getLatitude();
					postQuery += "&longitude="+geolocation.getLongitude();
				}
				
				// Upload the file
				FileInputStream fileInputStream = new FileInputStream(new File(pathToOurFile) );
				URL url = new URL(urlServer + "?" + postQuery);
				connection = (HttpURLConnection) url.openConnection();
				Log.i("UploadFile",urlServer + "?" + postQuery);
				Log.i("UploadFile",url.toString());
				
				// Allow Inputs & Outputs
				connection.setDoInput(true);
				connection.setDoOutput(true);
				connection.setUseCaches(false);

				// Enable POST method
				connection.setRequestMethod("POST");
				connection.setRequestProperty("Connection", "Keep-Alive");
				connection.setRequestProperty("Content-Type", "multipart/form-data;boundary="+boundary);
				outputStream = new DataOutputStream( connection.getOutputStream() );

				// add file to transmission
				outputStream.writeBytes(twoHyphens + boundary + lineEnd);
				outputStream.writeBytes("Content-Disposition: form-data; name=\"uploadedfile\";filename=\"" + pathToOurFile +"\"" + lineEnd);
				outputStream.writeBytes(lineEnd);
				bytesAvailable = fileInputStream.available();
				bufferSize = Math.min(bytesAvailable, maxBufferSize);
				buffer = new byte[bufferSize];

				// Read file
				bytesRead = fileInputStream.read(buffer, 0, bufferSize);
				while (bytesRead > 0)
				{
					outputStream.write(buffer, 0, bufferSize);
					bytesAvailable = fileInputStream.available();
					bufferSize = Math.min(bytesAvailable, maxBufferSize);
					bytesRead = fileInputStream.read(buffer, 0, bufferSize);
				}
				outputStream.writeBytes(lineEnd);
				outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

				// Responses from the server (code and message)
				int serverResponseCode = connection.getResponseCode();
				String serverResponseMessage = connection.getResponseMessage();
				/*InputStream is = connection.getInputStream();
				int responseBytes = -1;
				byte[] responseBuffer = new byte[1024];
				while ((responseBytes = is.read(responseBuffer)) >= 0) {
					Log.v("UploadFile",""+responseBuffer);
				}*/

				fileInputStream.close();
				outputStream.flush();
				outputStream.close();

				Log.v("UploadFile","Response Code: "+serverResponseCode);
				Log.v("UploadFile","Response Message: "+serverResponseMessage);

				stopSelf();
			} catch (Exception e) {
				Log.e("UploadFile","Upload error: "+e.toString());
			}
			return null;
		}

		// This is called each time you call publishProgress()
		protected void onProgressUpdate(Integer... progress) {
			//setProgressPercent(progress[0]);
		}

		// This is called when doInBackground() is finished
		protected void onPostExecute(Long result) {
			Log.i("UploadFile","Upload complete: " + filepath);
		}
	}
	

}
