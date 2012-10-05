package uk.ac.ox.cs.camerastreamer;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.features2d.KeyPoint;

public class Frame {
	private Mat someData = new Mat();
	private Mat greyMat = new Mat();
	private long timestamp;
	private List<Byte> buffer = new ArrayList<Byte>();
	private List<KeyPoint> keyPoints = new ArrayList<KeyPoint>();
	
public List<KeyPoint> getKeyPoints() {
	return keyPoints;
}
	
private double longitude;
private double latitude;


public void setLongitude(double longitude) {
	this.longitude = longitude;
}

public void setLatitude(double latitude) {
	this.latitude = latitude;
}

public double getLongitude() {
	return longitude;
}
public double getLatitude() {
	return latitude;
}


	private float[] mLinearAcceleration = new float[3];
	
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
	public float[] getLinearAcceleration() {
		return mLinearAcceleration;
	}
	
	public Mat getGreyMat() {
		return greyMat;
	}
	
	public void setGreyMat(Mat greyMat) {
		this.greyMat = greyMat;
	}

	public List<Byte> getBuffer() {
		return this.buffer;

	}

	public Mat getMat() {
		return someData;
	}

	protected int sequenceNumber;

	public int getSequenceNumber() {
		return sequenceNumber;
	}

	public void setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}

	public long getTimestamo() {
		return timestamp;
	}
}
