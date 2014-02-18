package org.goproimageextraction.core;

import java.io.File;

import kanzi.InputBitStream;

/**
 * @author Dominik Haas
 * 
 *         This Thread is supposed to add an image effect to every image in a
 *         given Folder
 * 
 */
public class ImageEffectThread extends Thread {

	private File imageFolder;

	public ImageEffectThread(String imageFolder) {

		this.imageFolder = new File(imageFolder);
	}

	@Override
	public void run() {

		applyImageEffect();
	}

	private void applyImageEffect() {

		// load each file

		File[] files = this.imageFolder.listFiles();

		for (int i = 0; i < files.length; i++) {

			InputBitStream inStream;// = new Inputbi

			// SobelFilter sobel = new SobelFilter();
			// setup kanzi

			// apply filter?!

		}

	}
}
