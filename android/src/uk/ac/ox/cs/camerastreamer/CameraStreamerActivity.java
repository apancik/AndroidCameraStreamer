// ----------------------------------------------------------------------------
// Camera Streamer Activity
// Copyright (C) 2011-2012 Andrej Pancik
// ----------------------------------------------------------------------------
package uk.ac.ox.cs.camerastreamer;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.KeyPoint;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;
import org.opencv.imgproc.Imgproc;

import uk.ac.ox.cs.camerastreamer.packet.DataPacket;
import uk.ac.ox.cs.camerastreamer.packet.DataPacket.PointData;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;

public class CameraStreamerActivity extends Activity {
	private static final String TAG = "CameraStreamerActivity";

	private static final int SETTINGS_ID = Menu.FIRST;
	private static final int HELP_ID = Menu.FIRST + 1;
	private static final int CONNECT_ID = Menu.FIRST + 2;
	private static final int EXIT_APP_ID = Menu.FIRST + 3;
	private static final int CANNY_ID = Menu.FIRST + 4;

	private String mHostIp;
	private int mHostPort;
	private volatile int mCompressionLevel;
	private int mBufferSize;
	private int mWidth;
	private int mHeight;
	private int mCameraId;
	// TODO preference option to send RGB or BW
	// TODO preference option to display preview

	private Object mLastLocationLock = new Object();
	private Location mLastLocation;

	private float[] mGravity = new float[3];
	private float[] mLinearAcceleration = new float[3];

	private void updatePreferences() {
		SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(getBaseContext());

		mHostIp = preferences.getString("host", "192.168.1.80");
		mHostPort = Integer.parseInt(preferences
				.getString("portnumber", "1313"));
		mCompressionLevel = Integer.parseInt(preferences
				.getString("jpeg", "90"));
		mBufferSize = Integer.parseInt(preferences.getString("buffer", "10"));
		mWidth = Integer.parseInt(preferences.getString("width", "640"));
		mHeight = Integer.parseInt(preferences.getString("height", "480"));
		mCameraId = Integer.parseInt(preferences.getString("camid", "0"));
	}

	@Override
	protected void onStart() {
		super.onStart();

		updatePreferences();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		Log.d(TAG, "Destroying activity");

		if (mGrabber != null)
			mGrabber.stopGrabbing();

		if (mSender != null)
			mSender.disconnect();

		mGrabber = null;
		mSender = null;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		updatePreferences();

		Log.d(TAG, "Activity starting");

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		// setContentView(R.layout.main);

		Log.d(TAG, "Starting processor");
		mProcessor = new Processor();
		mProcessor.start();

		Log.d(TAG, "Starting sender");
		mSender = new Sender();
		mSender.start();

		Log.d(TAG, "Starting grabber");
		mGrabber = new Grabber();
		mGrabber.start();

		SensorManager sensorManager = (SensorManager) this
				.getSystemService(Context.SENSOR_SERVICE);

		Sensor accelerometer = sensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		sensorManager.registerListener(new SensorEventListener() {

			@Override
			public void onSensorChanged(SensorEvent event) {
				// alpha is calculated as t / (t + dT)
				// with t, the low-pass filter's time-constant
				// and dT, the event delivery rate

				final float alpha = 0.8f; // TODO Estimate

				mGravity[0] = alpha * mGravity[0] + (1 - alpha)
						* event.values[0];
				mGravity[1] = alpha * mGravity[1] + (1 - alpha)
						* event.values[1];
				mGravity[2] = alpha * mGravity[2] + (1 - alpha)
						* event.values[2];

				mLinearAcceleration[0] = event.values[0] - mGravity[0];
				mLinearAcceleration[1] = event.values[1] - mGravity[1];
				mLinearAcceleration[2] = event.values[2] - mGravity[2];
			}

			@Override
			public void onAccuracyChanged(Sensor sensor, int accuracy) {
				Log.d("SensorEventListenerAccelerometer", "onAccuracyChanged");

			}
		}, accelerometer, SensorManager.SENSOR_DELAY_NORMAL); // TODO sensor
																// delay? aky?

		LocationManager locationManager = (LocationManager) this
				.getSystemService(Context.LOCATION_SERVICE);
		mLastLocation = locationManager
				.getLastKnownLocation(LocationManager.GPS_PROVIDER);

		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
				1000, 1, new LocationListener() {
					public void onLocationChanged(Location location) {
						Log.i("LocationListener", "Updating location");
						synchronized (mLastLocationLock) {
							mLastLocation = location;
						}
					}

					public void onStatusChanged(String s, int i, Bundle b) {
						Log.d("LocationListener", "onStatusChanged");
					}

					public void onProviderDisabled(String s) {
						Log.d("LocationListener", "onProviderDisabled");
					}

					public void onProviderEnabled(String s) {
						Log.d("LocationListener", "onProviderEnabled");
					}
				});

		for (int i = 0; i < mBufferSize; i++) {
			mUnusedFrames.add(new Frame());
		}

		setContentView(new CameraView(this));
	}

	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();

		menu.add(0, SETTINGS_ID, 0, "Settings").setIcon(
				android.R.drawable.ic_menu_preferences); // TODO aj vsade inde
		// R.string.settings
		menu.add(0, HELP_ID, 0, "Connect").setIcon(
				android.R.drawable.ic_menu_help);
		menu.add(0, CONNECT_ID, 0, "Disconnect").setIcon(
				android.R.drawable.ic_menu_info_details);
		menu.add(0, EXIT_APP_ID, 0, "Exit").setIcon(
				android.R.drawable.ic_menu_close_clear_cancel);
		menu.add(0, CANNY_ID, 0, "Enable Canny").setIcon(
				android.R.drawable.ic_menu_gallery);
		return true;
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case EXIT_APP_ID: {
			if (mGrabber != null)
				mGrabber.stopGrabbing();

			if (mSender != null)
				mSender.disconnect();

			mGrabber = null;
			mSender = null;

			finish();
			break;
		}
		case SETTINGS_ID: {
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
			intent.setClassName(this, SettingsActivity.class.getName());
			startActivity(intent);
			break;
		}

		case HELP_ID: {
			//if (mGrabber != null)
				//mGrabber.stopGrabbing();

			//if (mSender != null)
				//mSender.disconnect();

			//mSender = new Sender();
			//mSender.start();

			mSender.startSending();
			mSender.connect();
			//mGrabber = new Grabber();
			//mGrabber.start();
			
			
			break;
		}

		case CONNECT_ID: {
			//if (mGrabber != null)
				//mGrabber.stopGrabbing();

			//if (mSender != null)
				//mSender.disconnect();

			//mGrabber = null;
//			mSender = null;
			mSender.stopSending();
			break;
		}
		case CANNY_ID:
			cannyEnabled.set(!cannyEnabled.get());
			break;
		}

		return super.onOptionsItemSelected(item);
	}

	private AtomicBoolean cannyEnabled = new AtomicBoolean(false);

	private LinkedBlockingQueue<Frame> mUnusedFrames = new LinkedBlockingQueue<Frame>();

	private LinkedBlockingQueue<Frame> mGrabbedFrames = new LinkedBlockingQueue<Frame>();

	private int mNextFrameToDispatch = 0;
	private final Object mProcessedFramesBufferLock = new Object();
	private PriorityQueue<Frame> mProcessedFramesBuffer = new PriorityQueue<Frame>(
			1, new FrameComparator()); // TODO konstantu zmenit na pocet

	private LinkedBlockingQueue<Frame> mProcessedFramesToDispatch = new LinkedBlockingQueue<Frame>();

	private Grabber mGrabber;
	private Sender mSender;
	private Processor mProcessor;

	private class Processor extends Thread {
		private static final String TAG = "Processor";
		private List<Integer> mParams;

		public Processor() {
			mParams = new LinkedList<Integer>();
			mParams.add(Highgui.CV_IMWRITE_JPEG_QUALITY);
			mParams.add(mCompressionLevel);
		}

		@Override
		public void run() {
			Log.d(TAG, "Main processor thread running");

			while (true) {
				try {
					Log.d(TAG, "Encoding frame");
					Frame frame = mGrabbedFrames.take();
					Imgproc.cvtColor(frame.getMat(), frame.getGreyMat(),
							Imgproc.COLOR_RGB2GRAY, 1);

					if (cannyEnabled.get()) {
						Mat mIntermediateMat = new Mat();
						Imgproc.Canny(frame.getMat(), mIntermediateMat, 80, 100);
						frame.setGreyMat(mIntermediateMat);
					}
					
					if(true){
						FeatureDetector fd = FeatureDetector.create(FeatureDetector.DYNAMIC_FAST);
						frame.getKeyPoints().clear();
						fd.detect(frame.getGreyMat(), frame.getKeyPoints());
						
			            //for (KeyPoint keyPoint : frame.getKeyPoints())
			              //  Core.circle(frame.getGreyMat(), keyPoint.pt, 10, new Scalar(100, 0,0));
					}
					
					frame.getBuffer().clear();
					Highgui.imencode(".jpg", frame.getGreyMat(),
							frame.getBuffer(), mParams);

					Log.d(TAG, "Frame of size + " + frame.getBuffer().size());

					synchronized (mProcessedFramesBufferLock) {
						mProcessedFramesBuffer.add(frame);
						while (true) {
							Frame nextFrame = mProcessedFramesBuffer.peek();
							if ((nextFrame != null)
									&& (nextFrame.getSequenceNumber() == mNextFrameToDispatch)) {
								mProcessedFramesBuffer.remove(nextFrame);
								mProcessedFramesToDispatch.put(nextFrame);
								mNextFrameToDispatch++;
							} else {
								break;
							}
						}
					}
				} catch (InterruptedException e) {
					Log.e(TAG, "Interrupted processing computation", e);
				}
			}
		}
	}

	private class Sender extends Thread {
		private static final String TAG = "Sender";

		private AtomicBoolean mIsSending = new AtomicBoolean(true);
		private Socket mSocket;
		private DataOutputStream mSocketOutputStream;
		private AtomicBoolean mIsConnected = new AtomicBoolean(false);

		public Sender() {
			Log.d(TAG, "Initialising sender thread");
		}

		public void startSending() {
			mIsSending.set(true);
		}

		public void stopSending() {
			mIsSending.set(false);
		}

		public void connect() {
			if (!mIsConnected.get())
				try {
					Log.d(TAG, "Connecting...");
					InetAddress serverAddr = InetAddress.getByName(mHostIp);

					mSocket = new Socket(serverAddr, mHostPort);

					mSocketOutputStream = new DataOutputStream(
							mSocket.getOutputStream());

					mIsConnected.set(true);
					Log.d(TAG, "Connected.");
				} catch (Exception e) {
					Log.e(TAG, "Connection failed", e);
					mIsConnected.set(false);
				}
		}

		public void disconnect() {

			if (mIsConnected.get()) {
				Log.e(TAG, "Disconnecting");
				mIsConnected.set(false);
				try {
					mSocket.close();
					Log.e(TAG, "Socket closed.");
				} catch (IOException e) {
					Log.e(TAG, "Could not close the socket.", e);
				}
			} else
				Log.d(TAG, "Was not connected.");
		}

		@Override
		public void run() {
			connect(); // TODO spravit ze user musi kliknut aby sa spojil
			
			Log.d(TAG, "Main body of sender thread started");

			while (mIsConnected.get()) {
				try {
					Log.d(TAG, "Waiting for frame");
					Frame frame = mProcessedFramesToDispatch.take();

					Log.d(TAG, "Sending image of size "
							+ frame.getBuffer().size());

					byte[] bytes = new byte[frame.getBuffer().size()]; // TODO
																		// Oh,
																		// this
																		// is
																		// such
																		// a
																		// waste!
					for (int i = 0; i < frame.getBuffer().size(); i++)
						bytes[i] = frame.getBuffer().get(i);

					if (mIsSending.get()) {
						try {
							synchronized (mSocket) {
								DataPacket.Header.Builder headerBuilder = DataPacket.Header
										.newBuilder();

								headerBuilder
										.setTimestamp(frame.getTimestamo());

								headerBuilder.setAcc0(frame
										.getLinearAcceleration()[0]);
								headerBuilder.setAcc1(frame
										.getLinearAcceleration()[1]);
								headerBuilder.setAcc2(frame
										.getLinearAcceleration()[2]);

								headerBuilder
										.setLongitude(frame.getLongitude());
								headerBuilder.setLatitude(frame.getLatitude());
								
								if(!frame.getKeyPoints().isEmpty())
									for(KeyPoint keyPoint : frame.getKeyPoints()) {
										headerBuilder.addFeature(PointData.newBuilder().setX(keyPoint.pt.x).setY(keyPoint.pt.y));
									}
										

								byte[] header = headerBuilder.build()
										.toByteArray();

								// Send metadata
								mSocketOutputStream.writeInt(header.length);
								mSocketOutputStream.write(header);

								// Send image
								Log.d(TAG, "Frame of size + " + bytes.length);
								mSocketOutputStream.writeInt(bytes.length);
								mSocketOutputStream.write(bytes);
							}
							Log.d(TAG, "Sent successfully");
						} catch (Exception e) {
							disconnect();
							Log.e(TAG, "Sending failed", e);
						}
					}

					mUnusedFrames.put(frame);
				} catch (InterruptedException e) {
					Log.e(TAG, "Interrupted Exception", e);
				}
			}

			mIsConnected.set(false);
		}
	}

	private class Grabber extends Thread {
		private static final String TAG = "GrabberThread";

		private FpsMeter mFpsMeter;
		private VideoCapture mCameraCapture;
		private AtomicBoolean mGrabbing = new AtomicBoolean(false);

		public Grabber() {
			Log.d(TAG, "Initialising Video Grabber");
			mCameraCapture = new VideoCapture(Highgui.CV_CAP_ANDROID
					+ mCameraId);
			mFpsMeter = new FpsMeter();

			if (mCameraCapture.isOpened()) {
				mCameraCapture.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, mWidth);
				mCameraCapture.set(Highgui.CV_CAP_PROP_FRAME_HEIGHT, mHeight);
				Log.e(TAG, "Camera initialisation finished successfully");
				mGrabbing.set(true);
			} else {
				mCameraCapture.release();
				mCameraCapture = null;
				stopGrabbing();
				Log.e(TAG,
						"There was an error with native camera initialisation");
			}
		}

		public void stopGrabbing() {
			if (mGrabbing.get()) {
				Log.d(TAG, "Stopping grabber");
				mGrabbing.set(false);
			} else
				Log.d(TAG, "Wasnt grabbing");
		}

		@Override
		public void run() {
			Log.d(TAG, "Video loop thread started");

			int frameCounter = 0;
			while (mGrabbing.get()) {
				mFpsMeter.tick();

				Log.d(TAG, "Grabbing frame");
				synchronized (this) {
					try {
						Frame frame = mUnusedFrames.take();
						frame.setTimestamp(System.currentTimeMillis());
						frame.setSequenceNumber(frameCounter++);

						synchronized (mLinearAcceleration) {
							frame.getLinearAcceleration()[0] = mLinearAcceleration[0];
							frame.getLinearAcceleration()[1] = mLinearAcceleration[1];
							frame.getLinearAcceleration()[2] = mLinearAcceleration[2];
						}

						if (mLastLocation != null)
							synchronized (mLastLocationLock) {
								frame.setLatitude(mLastLocation.getLatitude());
								frame.setLongitude(mLastLocation.getLongitude());
							}

						if (!mCameraCapture.grab()) {
							Log.e(TAG, "Failed to start camera capturing");
							break;
						}

						mCameraCapture.retrieve(frame.getMat(),
								Highgui.CV_CAP_ANDROID_COLOR_FRAME_RGB);

						mGrabbedFrames.put(frame);
					} catch (InterruptedException e) {
						Log.e(TAG,
								"Manipulation with working queues was unsuccessfull",
								e);
					}
				}
			}

			
				if (mCameraCapture != null) {
					mCameraCapture.release();
					mCameraCapture = null;
				}
			
			Log.e(TAG, "Grabber quitting + mGrabbing=" + mGrabbing);
			mGrabbing.set(false);
		}

	}

}
