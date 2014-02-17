package org.streamball.core;

import java.util.ArrayList;
import java.util.Iterator;

public class ImageExtractionManager extends Thread {

	private volatile ArrayList<Thread> extractionThreadList = new ArrayList<Thread>();

	public ImageExtractionManager() {

	}

	@Override
	public void run() {

		while (true) {

			try {
				sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			startNextThread();

			try {
				sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			removeUnusedThreads();

		}
	}

	/**
	 * make sure only one thread is running at the same time
	 */
	private void startNextThread() {

		Iterator<Thread> i = extractionThreadList.iterator();
		while (i.hasNext()) {
			Thread thread = i.next();

			// TODO check use of join to w8 for thread to finish? Could probably
			// delay the downloadThrad since it tries to add to the list?
			// thread.join();

			boolean extractionFinished = ((ImageExtractionThread) thread).extractionFinished;

			if (!thread.isAlive() && !extractionFinished) {
				thread.start();
				break;
			}
		}
	}

	private void removeUnusedThreads() {

		// use Iterator to safely remove
		Iterator<Thread> i = extractionThreadList.iterator();

		while (i.hasNext()) {
			Thread thread = i.next();
			if (!((ImageExtractionThread) thread).isExtracting) {
				i.remove();
			}
		}
	}

	public void addThread(Thread t) {

		extractionThreadList.add(t);
	}
}
