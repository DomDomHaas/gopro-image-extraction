package org.streamball.core;

import java.awt.TrayIcon;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.gopro.main.GoProApi;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class DownloadItemsThread extends Thread {

	private GoProApi api;
	private TrayIcon trayIcon;
	private String imageCollectionPath = "";
	// default 5s
	private int checkIntervall = 5000;
	private double imageExtractIntervall = 0;
	private URL goProURL;
	private boolean directLoadImages = false;

	private ArrayList<String> alreadyDownloadedItemList = new ArrayList<String>();
	private LinkedHashMap<String, Integer> camFiles = new LinkedHashMap<String, Integer>();

	public DownloadItemsThread(GoProApi api, TrayIcon trayIcon, String imageCollectionPath,
			int checkIntervall, double imageExtractIntervall, boolean directLoadImages) {

		this.api = api;
		this.trayIcon = trayIcon;

		this.imageCollectionPath = imageCollectionPath;
		this.checkIntervall = checkIntervall;
		this.imageExtractIntervall = imageExtractIntervall;
		this.directLoadImages = directLoadImages;

		try {
			if (directLoadImages) {
				goProURL = new URL("http://" + api._10_5_5_9 + ":8080/DCIM/100GOPRO");
			} else {
				goProURL = new URL("http://" + api._10_5_5_9 + ":8080/videos/DCIM/100GOPRO");
			}
		} catch (MalformedURLException e) {
			System.out.println("Error in the URL: " + api._10_5_5_9);
			e.printStackTrace();
		}

		// load existing files
		preloadExistingFiles();
	}

	private void preloadExistingFiles() {

		File imageFolder = new File(imageCollectionPath);

		for (final File fileEntry : imageFolder.listFiles()) {

			if (this.directLoadImages) {
				if (isImageFile(fileEntry)) {
					this.alreadyDownloadedItemList.add(fileEntry.getName());
				}
			} else {
				if (isMP4File(fileEntry)) {
					this.alreadyDownloadedItemList.add(fileEntry.getName());
				}
			}

		}
	}

	@Override
	public void run() {

		for (;;) {

			System.out.println("DownloadItemsThread " + super.getName()
					+ " start downloading Items");

			if (directLoadImages == true) {
				downloadLatestFile(false);
			} else {
				downloadLatestFile(true);
			}

			// String overviewHTML = loadHTMLOverview(goProURL);
			//
			// if (overviewHTML != null) {

			// String[] fileNames = null;
			//
			// if (directLoadImages == true) {
			// // download only images
			// fileNames = null;
			// getFileNames(overviewHTML, false);
			// directDownloadItems(fileNames, true, false);
			// } else {
			// // download mp4 and extract images
			// fileNames = null;
			// getFileNames(overviewHTML, true);
			// directDownloadItems(fileNames, true, true);
			// }

			// } else {
			// System.out.println("DownloadItemsThread " + super.getName() +
			// " noting found");
			// }

			try {

				System.out.println("DownloadItemsThread " + super.getName()
						+ " going to sleep for " + this.checkIntervall + "ms");

				sleep(this.checkIntervall);

			} catch (InterruptedException e) {
				System.out.println("Error sleep Intervall:  " + e.getMessage());
				trayIcon.displayMessage("DownloadImageThread Error",
						"Error sleep Intervall: " + e.getMessage(), TrayIcon.MessageType.ERROR);
			}
		}

	}

	private String loadHTMLOverview(URL url) {

		BufferedReader reader = null;
		InputStreamReader urlStream = null;
		String overviewHtml = null;

		try {
			urlStream = new InputStreamReader(url.openStream(), "UTF-8");
			reader = new BufferedReader(urlStream);

			String line;

			while ((line = reader.readLine()) != null) {
				overviewHtml += line;
			}

		} catch (IOException e) {
			System.out.println("Error opening URL: " + url.toString() + " " + e.getMessage());
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					System.out.println("BufferedReader couldn't close... to bad! : "
							+ e.getMessage());
				}
			}

			if (urlStream != null) {
				try {
					urlStream.close();
				} catch (IOException e) {
					System.out.println("urlStream couldn't close... to bad! : " + e.getMessage());
				}
			}
		}

		return overviewHtml;
	}

	private boolean downloadLatestFile(boolean mp4LinksOnly) {

		// initial load
		String overviewHTML = loadHTMLOverview(goProURL);
		if (overviewHTML != null) {
			updateCamFileMap(overviewHTML, mp4LinksOnly);
		}

		String fileToDownload = getLatestFileNameToDownload();

		// milliseconds to w8 for the next check
		int sizeRecheckIntervall = 500;

		int checksBeforeDownload = 2;
		int checkCount = 0;

		if (fileToDownload != null) {

			int intialSizeOfFileToDownload = camFiles.get(fileToDownload);

			while (true) {

				overviewHTML = loadHTMLOverview(goProURL);

				if (overviewHTML != null) {
					updateCamFileMap(overviewHTML, mp4LinksOnly);

					int currentFileSize = camFiles.get(fileToDownload);

					if (intialSizeOfFileToDownload == currentFileSize) {

						if (checkCount > checksBeforeDownload) {
							// file didn't change, it's ready for download!

							try {
								URL source = new URL(goProURL + "/" + fileToDownload);
								String destFilePath =
										imageCollectionPath + File.separator + fileToDownload;

								if (downloadFile(source, destFilePath)) {
									alreadyDownloadedItemList.add(fileToDownload);

									// start the image Extraction or PostEffect
									if (mp4LinksOnly) {
										// start decoding frames per xuggler
										ImageExtractionThread extractImages =
												new ImageExtractionThread(destFilePath,
														imageExtractIntervall);
										extractImages.start();

									} else {
										// start thread for image effect
										ImageEffectThread postEffect =
												new ImageEffectThread(destFilePath);
										postEffect.start();
									}
								}

							} catch (MalformedURLException e) {
								e.printStackTrace();
								System.out.println("URL wrong for download: " + e.getMessage());
								trayIcon.displayMessage("DownloadImageThread Error",
										"Error sleep Intervall: " + e.getMessage(),
										TrayIcon.MessageType.ERROR);

								return false;
							}

							return true;
						}

						checkCount++;
					}
				}

				try {
					sleep(sizeRecheckIntervall);
				} catch (InterruptedException e) {
					e.printStackTrace();
					System.out.println("Sleep in downlowadLatestFile Error: " + e.getMessage());
				}
			}
		}

		return false;
	}

	private boolean isFileReadyForDownload(String fileName, int currentSize) {

		return false;
	}

	private String[] getFileNames(String html, boolean mp4LinksOnly) {

		Document htmlDoc = Jsoup.parse(html);
		Elements links = null;

		if (mp4LinksOnly) {
			links = htmlDoc.select("a[href$=.mp4]");
		} else {
			links = htmlDoc.select("a[href$=.jpg]");
		}

		if (links != null) {

			String[] imageNames = new String[links.size()];

			for (int i = 0; i < links.size(); i++) {
				Element link = links.get(i);
				imageNames[i] = link.text();
			}

			return imageNames;
		}

		return null;
	}

	private int[] getSizeNumber(String html, boolean mp4LinksOnly, String[] fileNames) {

		Document htmlDoc = Jsoup.parse(html);

		Elements links = htmlDoc.select("a.link");
		int exceptedSizeCount = 0;

		if (mp4LinksOnly) {
			exceptedSizeCount = htmlDoc.select("a[href$=.mp4]").size();
		} else {
			exceptedSizeCount = htmlDoc.select("a[href$=.jpg]").size();
		}

		Elements maybeHere = htmlDoc.select("span.size");
		Elements maybeThere = htmlDoc.select("span.unit");

		if (links != null) {

			int[] sizes = new int[exceptedSizeCount];

			for (int i = 0; i < links.size(); i++) {

				Element link = links.get(i);

				if (mp4LinksOnly) {

					if (link.text().endsWith(".MP4")) {
						// now I'm interested!

						int indexOfFileName = getIndexOfEntry(link.text(), fileNames);

						// but gopro is to stupid to be consistent so I have to
						// check class size OR unit
						Element isSizeHere = maybeHere.get(i);
						if (!isSizeHere.text().trim().equals("")) {
							sizes[indexOfFileName] = Integer.parseInt(isSizeHere.text().trim());
						} else {
							// oooohhh this time it's the size is in the unit
							// class... and we put a unit behind it, isn't that
							// great?
							// -_- ... no! idiots!

							Element isSizeThere = maybeThere.get(i);
							if (!isSizeThere.text().trim().equals("")) {
								String sizeWithUnit = isSizeThere.text();
								String fuckingSize =
										isSizeThere.text().substring(0, sizeWithUnit.length() - 1);
								sizes[indexOfFileName] = Integer.parseInt(fuckingSize.trim());
							}
						}

					}
				} else {
					if (link.toString().endsWith(".JPG")) {
						// not now!
					}
				}

				// System.out.println(link.text());
			}

			return sizes;
		}

		return null;
	}

	private int getIndexOfEntry(String name, String[] fileNames) {

		for (int i = 0; i < fileNames.length; i++) {
			if (name.equals(fileNames[i])) {
				return i;
			}
		}

		return -1;
	}

	private void updateCamFileMap(String html, boolean mp4LinksOnly) {

		String[] fileNames = getFileNames(html, mp4LinksOnly);
		int[] fileSizes = getSizeNumber(html, mp4LinksOnly, fileNames);

		// update Files
		if (fileNames.length > camFiles.size()) {

			for (int i = 0; i < fileNames.length; i++) {
				String fileName = fileNames[i];
				int actualSize = fileSizes[i];

				if (!camFiles.containsKey(fileNames.length)) {
					camFiles.put(fileName, actualSize);
				}
			}
		} else {

			// update sizes
			String lastFileName = fileNames[fileNames.length - 1];
			int sizeOfLastFile = fileSizes[fileSizes.length - 1];

			camFiles.put(lastFileName, sizeOfLastFile);
		}

	}

	private String getLatestFileNameToDownload() {

		String fileName = null;

		for (String key : camFiles.keySet()) {

			if (!alreadyDownloadedItemList.contains(key)) {
				return key;
			}

			fileName = key;
		}

		if (!alreadyDownloadedItemList.contains(fileName)) {
			return fileName;
		} else {
			return null;
		}

	}

	private void directDownloadItems(String[] imageNames, boolean getImages, boolean getMp4s) {

		int itemDownloaded = 0;
		Date before = new Date();

		for (int i = 0; i < imageNames.length; i++) {

			String currentName = imageNames[i];
			boolean isValidName = false;

			if (getImages) {
				isValidName = isImageLink(currentName);
			}

			if (getMp4s) {
				isValidName = isMp4Link(currentName);
			}

			if (isValidName && !alreadyDownloadedItemList.contains(currentName)) {
				// only download when image isn't in collection

				try {
					URL source = new URL(goProURL + "/" + currentName);

					// get the last modified and w8 for some secs...
					HttpURLConnection httpCon = (HttpURLConnection) source.openConnection();
					httpCon.connect();
					Date lastModified = new Date(httpCon.getLastModified());

					long modifiedMillis = (lastModified.getTime() - before.getTime());
					long modifedSecs = modifiedMillis / 1000;

					if (modifedSecs > 0.5) {

						String destFilePath = imageCollectionPath + File.separator + currentName;

						if (downloadFile(source, destFilePath)) {
							alreadyDownloadedItemList.add(currentName);
							itemDownloaded++;
						}

						// collect the garbage for freeing the destination?!
						// System.gc();

						if (getImages && getMp4s) {
							// start decoding frames per xuggler
							ImageExtractionThread extractImages =
									new ImageExtractionThread(destFilePath, imageExtractIntervall);
							extractImages.start();

						} else {
							// start thread for image effect
							ImageEffectThread postEffect = new ImageEffectThread(destFilePath);
							postEffect.start();
						}
					}

					httpCon.disconnect();
				} catch (IOException e) {
					System.out.println("Couldn't download " + currentName + " " + e.getMessage());
				}
			}
		}

		Date after = new Date();

		if (itemDownloaded > 0) {
			long millis = (after.getTime() - before.getTime());
			long secs = millis / 1000;

			trayIcon.displayMessage("DownloadItem Finished", "Downloaded " + itemDownloaded
					+ " new Item in " + secs + " secs", TrayIcon.MessageType.INFO);
			System.out.println(before.getTime() + " Downloaded " + itemDownloaded + " new Item in "
					+ millis + " millis");
		}

	}

	private boolean downloadFile(URL source, String destFilePath) {

		File destination = new File(destFilePath);

		try {
			FileUtils.copyURLToFile(source, destination);
			destination = null;
			return true;

		} catch (IOException e) {
			System.out.println("Couldn't download " + source + " : " + e.getMessage());
			trayIcon.displayMessage("DownloadImageThread Error", "Couldn't download " + source
					+ " : " + e.getMessage(), TrayIcon.MessageType.ERROR);
		}

		return false;
	}

	private boolean isImageFile(File fileInQuestion) {

		if (fileInQuestion.isFile()) {
			if (FilenameUtils.wildcardMatch(fileInQuestion.getAbsolutePath(), "*.JPG")
					|| FilenameUtils.wildcardMatch(fileInQuestion.getAbsolutePath(), "*.JPG")
					|| FilenameUtils.wildcardMatch(fileInQuestion.getAbsolutePath(), "*.png")) {
				return true;
			}
		}

		return false;

	}

	private boolean isMP4File(File fileInQuestion) {

		if (fileInQuestion.isFile()) {
			if (FilenameUtils.wildcardMatch(fileInQuestion.getAbsolutePath(), "*.MP4")
					|| FilenameUtils.wildcardMatch(fileInQuestion.getAbsolutePath(), "*.mp4")) {
				return true;
			}
		}

		return false;

	}

	private boolean isImageLink(String linkInQuestion) {

		if (linkInQuestion.endsWith(".JPG") || linkInQuestion.endsWith(".jpg")
				|| linkInQuestion.endsWith(".png")) {
			return true;
		}
		return false;
	}

	private boolean isMp4Link(String linkInQuestion) {

		if (linkInQuestion.endsWith(".MP4") || linkInQuestion.endsWith(".mp4")) {
			return true;
		}
		return false;
	}

	//
	// private void JsoupDownloadImages(String[] imageNames) {
	//
	// FileOutputStream fileOutStream = null;
	//
	// for (int i = 0; i < imageNames.length; i++) {
	//
	// String currentName = imageNames[i];
	//
	// if (isImageLink(currentName) &&
	// !alreadyDownloadedItemList.contains(currentName)) {
	// // only download when image isn't in collection
	//
	// Response resultImageResponse = null;
	// // Open a URL Stream
	// try {
	// resultImageResponse =
	// Jsoup.connect(goProURL + "/" + currentName).ignoreContentType(true)
	// .execute();
	// } catch (IOException e1) {
	// System.out.println("Error while open the image response");
	// e1.printStackTrace();
	// }
	//
	// if (resultImageResponse != null) {
	// try {
	//
	// fileOutStream =
	// new FileOutputStream(new File(imageCollectionPath + File.separator
	// + currentName));
	//
	// fileOutStream.write(resultImageResponse.bodyAsBytes());
	// fileOutStream.close();
	//
	// } catch (IOException e) {
	// System.out.println("Error while downloading the image");
	// e.printStackTrace();
	// }
	// }
	// }
	//
	// }
	//
	// }

}
