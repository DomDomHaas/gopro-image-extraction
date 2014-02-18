package org.streamball.core;

import org.streamball.main.GoProImagesExtraction;

/**
 * @author Dominik Haas
 * 
 *         This Thread does waits for
 */
public class RecordTimingThread extends Thread {

	private int recordingTime;
	private int sleepDuringRecording;

	public volatile boolean doRun = true;

	public RecordTimingThread(int recordingTime, int sleepDuringRecording) {
		// add 5s because of the triggering delay...
		this.recordingTime = recordingTime + 5000;
		// testing showed: recordingTime: 2000 + 5000 results in 10s videos...

		this.sleepDuringRecording = sleepDuringRecording;
	}

	@Override
	public void run() {

		while (doRun) {

			GoProImagesExtraction.startRecording("Trigger Recording (RecordThread)");
			System.out.println(super.getName() + " Trigger Recording");

			// sleep until the next recording is done...
			try {
				sleep(recordingTime);
			} catch (InterruptedException e) {
				System.out.println(super.getName() + " recordingTime interruptedException: "
						+ e.getMessage());
			}

			GoProImagesExtraction.stopRecording("Stopped Recording (RecordThread)");
			System.out.println(super.getName() + " Stopped Recording");

			// sleep until the next recording phase
			try {
				sleep(sleepDuringRecording);
			} catch (InterruptedException e) {
				System.out.println(super.getName() + " sleepDuringRecording interruptedException: "
						+ e.getMessage());
			}

		}

	}
}
