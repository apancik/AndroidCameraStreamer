package uk.ac.ox.cs.camerastreamer;

import java.util.Comparator;

public class FrameComparator implements Comparator<Frame> {
	public int compare(Frame a, Frame b) {
		if (a.getSequenceNumber() > b.getSequenceNumber()) {
			return 1;
		} else if (a.getSequenceNumber() < b.getSequenceNumber()) {
			return -1;
		} else {
			return 0;
		}
	}
}