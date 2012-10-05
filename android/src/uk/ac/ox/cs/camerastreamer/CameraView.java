//this source is based on opencv sample
package uk.ac.ox.cs.camerastreamer;


import java.text.DecimalFormat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;

import android.util.Log;

import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CameraView extends SurfaceView implements SurfaceHolder.Callback,
		Runnable {

	private static final String TAG = "CameraView";

	private static CameraView instance = null;

	private SurfaceHolder surfaceHolder;

	private Location lastLocation;

	public CameraView(Context context) {
		super(context);

		Log.i(TAG, "Camera View Initialising");

		CameraView.instance = this;

		surfaceHolder = getHolder();
		surfaceHolder.addCallback(this);

		Log.i(TAG, "Camera View Initialised");

		// (new Thread(this)).start();
	}

	public static CameraView getInstance() { // singleton
		return CameraView.instance;
	}

	@Override
	public void surfaceChanged(SurfaceHolder _holder, int format, int width,
			int height) {

		Log.i(TAG, "surfaceChanged");
	}

	public void surfaceCreated(SurfaceHolder holder) {

		Log.i(TAG, "surfaceCreated");
		// mDisplayPreview = true;

		// if (grabber == null) {
		// grabber = new Grabber();
		// }
	}

	public void surfaceDestroyed(SurfaceHolder holder) {

		// mDisplayPreview = false;
		Log.i(TAG, "surfaceDestroyed");

	}

	public void endProcessing() {
		Log.e(TAG, "Koncim procesisng ");

		// cameraCapture.release();
		// sender stop
		// procesosr stop
	}

	@Override
	public void run() {

		Log.i(TAG, "Starting processing thread");

		// connect();
		// int counter = 0;

		while (true) {

			Log.d(TAG, "Drawing");

			if (true) {
				Canvas canvas = surfaceHolder.lockCanvas();
				if (canvas != null) {
					// if (lastFrame != null)
					// canvas.drawBitmap(lastFrame, 0, 0, null);

					// fpsMeter.draw(canvas, 0, 0); // draw FPS

					Paint green = new Paint();
					green.setTextSize(32);
					green.setColor(Color.GREEN);

					DecimalFormat twoPlaces = new DecimalFormat("0.00");

					// LOCATION
					if (lastLocation != null) {
						String message = String.format(
								"Longitude: %1$s Latitude: %2$s",
								twoPlaces.format(lastLocation.getLongitude()),
								twoPlaces.format(lastLocation.getLatitude()));// TODO
																				// toto
																				// zmenit
																				// nech
																				// nie
																				// percenta

						canvas.drawText(message, 20, 90, green);
					}
					// END LOCATION

					/*
					 * String message2 = "LinAc" +
					 * twoPlaces.format(mLinearAcceleration[0]) + " " +
					 * twoPlaces.format(mLinearAcceleration[1]) + " " +
					 * twoPlaces.format(mLinearAcceleration[2]);
					 * canvas.drawText(message2, 20, 120, green);
					 */
					surfaceHolder.unlockCanvasAndPost(canvas);
				}
			}
		}

	}

}
