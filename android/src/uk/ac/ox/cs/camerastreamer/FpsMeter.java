package uk.ac.ox.cs.camerastreamer;

import java.text.DecimalFormat;

import org.opencv.core.Core;

import android.util.Log;

public class FpsMeter {
	private static final String TAG = "FPS Meter";

	private int step;

	private int framesCounter;

	private long previousFrameTime;

	private double frequency;

	DecimalFormat twoPlaces = new DecimalFormat("0.00");

	public FpsMeter() {
		step = 20;
		framesCounter = 0;
		frequency = Core.getTickFrequency();
		previousFrameTime = Core.getTickCount();
	}

	public void tick() {
		if (framesCounter++ % step == 0) {
			long time = Core.getTickCount();
			double fps = step * frequency / (time - previousFrameTime);
			previousFrameTime = time;

			String strfps = twoPlaces.format(fps) + " FPS";
			Log.i(TAG, strfps);
		}
	}

}