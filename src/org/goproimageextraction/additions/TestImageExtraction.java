package org.goproimageextraction.additions;

import java.io.File;

import org.goproimageextraction.core.ImageExtractionThread;
import org.goproimageextraction.main.JSONConfigLoader;
import org.json.simple.JSONObject;

public class TestImageExtraction {

	private static String configFileName = "ImageExtraction_config.txt";

	public static void main(String[] args) {

		// load Config
		JSONConfigLoader jsonConfig = new JSONConfigLoader(configFileName);
		JSONObject config = jsonConfig.loadConfig();

		// int checkNewFilesIntervall = Integer.valueOf((String)
		// config.get("checkIntervall"));
		double imageExtractIntervall =
				Double.parseDouble((String) config.get("imageExtractIntervall"));

		// overwrite for testing
		imageExtractIntervall = 0.1;

		String imageCollectionPath = (String) config.get("imageCollectionFolder");

		String testMovie = imageCollectionPath + File.separator + "GOPR0656.MP4";

		ImageExtractionThread imageExt =
				new ImageExtractionThread(testMovie, imageExtractIntervall);
		imageExt.start();
		System.out.println("started extracting: " + testMovie);

		while (imageExt.getState() != Thread.State.TERMINATED) {

			System.out.println("extractFinished: " + imageExt.extractionFinished);
			System.out.println("filecounter: " + imageExt.getFilecounter());

			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		System.out.println(" stopped extracting: " + testMovie);

	}
}
