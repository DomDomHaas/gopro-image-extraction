package org.streamball.core;

import java.io.File;

import kanzi.InputBitStream;

public class ImageEffectThread extends Thread {

	private File image;

	public ImageEffectThread(String image) {

		this.image = new File(image);
	}

	@Override
	public void run() {

		applyImageEffect();
	}

	private void applyImageEffect() {

		InputBitStream inStream;// = new Inputbi

		// SobelFilter sobel = new SobelFilter();
		// setup kanzi

		// apply filter?!
	}
}
