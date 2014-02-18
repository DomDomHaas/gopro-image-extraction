package org.streamball.main;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import org.gopro.core.GoProHelper;
import org.gopro.main.GoProApi;
import org.json.simple.JSONObject;
import org.streamball.core.DownloadItemsThread;
import org.streamball.core.ImageExtractionThread;
import org.streamball.core.RecordTimingThread;

/**
 * @author Dominik Haas
 * 
 *         This Program starts a thread which downloads images or movies for the
 *         GoPro via Wifi. It also setups an TrayIcon "Menu" to send commands to
 *         the camera.
 * 
 *         There are some configs in the configFile to setup up this app.
 *         Intervalls are in Millisecond ex. 1000 = 1 second
 * 
 */
public class GoProImagesExtraction {

	private static String applicationDir = System.getProperty("user.dir");
	private static String logFilePath = null;
	private static String imageCollectionPath = null;

	private static String configFileName = "ImageExtraction_config.txt";
	private static JSONConfigLoader jsonConfig = null;

	public static GoProApi api;
	private static String goProWifiPassword;
	public static GoProHelper helper;

	public static DownloadItemsThread downloadItemsThread = null;
	public static RecordTimingThread recordTimingThread = null;

	private static boolean downloadImages = false;
	private static boolean downloaderEnabled = false;
	private static boolean startExtractionAfterDownload = false;
	private static boolean useRecordingCycle = false;

	private static int checkNewFilesIntervall;
	private static int recordingTime;
	private static int sleepDuringRecording;
	private static double imageExtractIntervall;
	private static String startUpMode;

	public static TrayIcon trayIcon = null;

	public static void main(String[] args) {

		loadConfigVariables();
		setLogFile(logFilePath);

		api = new GoProApi(goProWifiPassword);
		helper = api.getHelper();

		trayIcon = setupTrayIcon();

		// Testing the ImageExtraction Thread!
		// String destination = imageCollectionPath + File.separator +
		// "GOPR0640.MP4";
		// testingImageCapture(destination, imageExtractIntervall);

		// starting with Timelapse
		if (startCam()) {

			// give it 4s to start up
			try {
				Thread.sleep(4000);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}

			loadTheStartUpMode();

			if (downloaderEnabled) {
				// start thread for checking new files on camera
				try {

					downloadItemsThread =
							new DownloadItemsThread(trayIcon, imageCollectionPath,
									checkNewFilesIntervall, imageExtractIntervall,
									startExtractionAfterDownload, downloadImages);

					downloadItemsThread.start();
					trayIcon.displayMessage("StreamBall App", "Started Downloading!",
							TrayIcon.MessageType.WARNING);

				} catch (Exception e) {
					System.out.println("Error in ImageEffect:  " + e.getMessage());
					trayIcon.displayMessage("ImageEffect Error",
							"ImageEffect had an Error: " + e.getMessage(),
							TrayIcon.MessageType.ERROR);
				}
			}

			if (useRecordingCycle) {

				try {
					recordTimingThread =
							new RecordTimingThread(recordingTime, sleepDuringRecording);
					recordTimingThread.start();

					trayIcon.displayMessage("StreamBall App", "Started recordTimingThread!",
							TrayIcon.MessageType.WARNING);

				} catch (Exception e) {
					System.out.println("Error in recordTimingThread:  " + e.getMessage());
					trayIcon.displayMessage("recordTimingThread Error",
							"starting recordTimingThread had an Error: " + e.getMessage(),
							TrayIcon.MessageType.ERROR);
				}
			}

		} else {
			System.out.println("Couldn't start the cam ");
			trayIcon.displayMessage("ImageEffect Error", "Couldn't start the cam",
					TrayIcon.MessageType.ERROR);
		}

	}

	private static void loadConfigVariables() {

		jsonConfig = new JSONConfigLoader(configFileName);
		JSONObject config = jsonConfig.loadConfig();

		checkNewFilesIntervall = Integer.valueOf((String) config.get("checkIntervall"));
		recordingTime = Integer.valueOf((String) config.get("recordingTime"));
		sleepDuringRecording = Integer.valueOf((String) config.get("sleepDuringRecording"));
		imageExtractIntervall = Double.parseDouble((String) config.get("imageExtractIntervall"));

		imageCollectionPath = (String) config.get("imageCollectionFolder");

		String downloadImagesStr = (String) config.get("downloadImages");

		if (downloadImagesStr.toLowerCase().equals("true")) {
			downloadImages = true;
		}

		String downloaderEnabledStr = (String) config.get("downloaderEnabled");
		if (downloaderEnabledStr.toLowerCase().equals("true")) {
			downloaderEnabled = true;
		}

		String startExtractionAfterDownloadStr =
				(String) config.get("startExtractionAfterDownload");
		if (startExtractionAfterDownloadStr.toLowerCase().equals("true")) {
			startExtractionAfterDownload = true;
		}

		String useRecordingCycleStr = (String) config.get("useRecordingCycle");
		if (useRecordingCycleStr.toLowerCase().equals("true")) {
			useRecordingCycle = true;
		}

		goProWifiPassword = (String) config.get("goProWifiPassword");

		startUpMode = (String) config.get("startUpMode");

		// setup logFile
		logFilePath = applicationDir + File.separator + config.get("logFileName");

	}

	private static void testingImageCapture(String destination, double imageExtractIntervall) {

		try { // start decoding frames per xuggler
			ImageExtractionThread extractImages =
					new ImageExtractionThread(destination, imageExtractIntervall);
			// extractImages.run();
			extractImages.start();

			// testing without thread... didn't help :[
			// ImageExtraction extractImages = new ImageExtraction(destination,
			// imageExtractIntervall);

			// direct testing the demo
			// DecodeAndCaptureFramesSRC demoTry = new
			// DecodeAndCaptureFramesSRC();
			// demoTry.main(new String[] { destination });

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void loadTheStartUpMode() {

		if (startUpMode.equals("timelapseMode")) {
			doTimelapseMode();
		} else if (startUpMode.equals("recordMode")) {
			doRecordMode();
		} else if (startUpMode.equals("burstMode")) {
			doBurstMode();
		}

		// make a short sleep to let the cam change the mode!
		try {
			Thread.sleep(500);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}

	@Override
	protected void finalize() throws Throwable {

		this.downloadItemsThread.stop();
		super.finalize();
		api.powerOff();
	}

	public static void startRecording(String costumMsg) {
		String msg = "Triggered Recording";

		if (!costumMsg.equals("")) {
			msg = costumMsg;
		}

		try {
			api.startRecord();
			trayIcon.displayMessage("StreamBall App", msg, TrayIcon.MessageType.WARNING);
		} catch (Exception e) {
			System.out.println("Error Burst:  " + e.getMessage());
			trayIcon.displayMessage("Error", "Start Recording Cam Error: " + e.getMessage(),
					TrayIcon.MessageType.ERROR);
		}
	}

	public static void stopRecording(String costumMsg) {
		String msg = "Stopped Recording";

		if (!costumMsg.equals("")) {
			msg = costumMsg;
		}
		try {
			api.stopRecord();
			trayIcon.displayMessage("StreamBall App", msg, TrayIcon.MessageType.WARNING);
		} catch (Exception e) {
			System.out.println("Error Burst:  " + e.getMessage());
			trayIcon.displayMessage("Error", "Stopped Recording Cam Error: " + e.getMessage(),
					TrayIcon.MessageType.ERROR);
		}

	}

	private static boolean startCam() {
		boolean ok = false;
		try {
			// ok = api.powerAndWaitUntilIsReady();
			api.powerOn();
			ok = true;
			trayIcon.displayMessage("StreamBall App", "GoPro started", TrayIcon.MessageType.WARNING);

		} catch (Exception e) {
			System.out.println("Error start:  " + e.getMessage());
			trayIcon.displayMessage("Error", "Start Cam Error: " + e.getMessage(),
					TrayIcon.MessageType.ERROR);
		}

		return ok;
	}

	private static void stopCam() {
		try {
			api.powerOff();
			trayIcon.displayMessage("StreamBall App", "Gopro power off",
					TrayIcon.MessageType.WARNING);

		} catch (Exception e) {
			System.out.println("Error stop:  " + e.getMessage());
			trayIcon.displayMessage("Error", "Stop Cam Error: " + e.getMessage(),
					TrayIcon.MessageType.ERROR);
		}

	}

	private static void doBurstMode() {
		try {
			helper.modeBurst();
			trayIcon.displayMessage("StreamBall App", "Set To Burst Mode",
					TrayIcon.MessageType.INFO);

		} catch (Exception e) {
			System.out.println("Error burst mode:  " + e.getMessage());
			trayIcon.displayMessage("Error", "Set Burst Mode: " + e.getMessage(),
					TrayIcon.MessageType.ERROR);
		}
	}

	private static void doRecordMode() {
		try {
			helper.modeCamera();
			trayIcon.displayMessage("StreamBall App", "Set To Recoring Mode",
					TrayIcon.MessageType.INFO);

		} catch (Exception e) {
			System.out.println("Error Recording mode:  " + e.getMessage());
			trayIcon.displayMessage("Error", "Set Recoring Mode: " + e.getMessage(),
					TrayIcon.MessageType.ERROR);
		}
	}

	private static void doTI_HalfSec() {
		try {
			helper.setCamTimeLapseTI("%00");
			trayIcon.displayMessage("StreamBall App", "Set Timelapse 0.5s",
					TrayIcon.MessageType.INFO);

		} catch (Exception e) {
			System.out.println("Error burst mode:  " + e.getMessage());
			trayIcon.displayMessage("Error", "Set Timelapse 0.5s: " + e.getMessage(),
					TrayIcon.MessageType.ERROR);
		}
	}

	private static void doTI_OneSec() {
		try {
			helper.setCamTimeLapseTI("%01");
			trayIcon.displayMessage("StreamBall App", "Set Timelapse 1s", TrayIcon.MessageType.INFO);

		} catch (Exception e) {
			System.out.println("Error burst mode:  " + e.getMessage());
			trayIcon.displayMessage("Error", "Set Timelapse 1s: " + e.getMessage(),
					TrayIcon.MessageType.ERROR);
		}
	}

	private static void doTI_TwoSec() {
		try {
			helper.setCamTimeLapseTI("%02");
			trayIcon.displayMessage("StreamBall App", "Set Timelapse 2s", TrayIcon.MessageType.INFO);

		} catch (Exception e) {
			System.out.println("Error burst mode:  " + e.getMessage());
			trayIcon.displayMessage("Error", "Set Timelapse 2s: " + e.getMessage(),
					TrayIcon.MessageType.ERROR);
		}
	}

	private static void doTI_FiveSec() {
		try {
			helper.setCamTimeLapseTI("%03");
			trayIcon.displayMessage("StreamBall App", "Set Timelapse 5s", TrayIcon.MessageType.INFO);

		} catch (Exception e) {
			System.out.println("Error burst mode:  " + e.getMessage());
			trayIcon.displayMessage("Error", "Set Timelapse 5s: " + e.getMessage(),
					TrayIcon.MessageType.ERROR);
		}
	}

	private static boolean doTimelapseMode() {
		boolean ok = false;

		try {
			ok = helper.timelapse1();
			trayIcon.displayMessage("StreamBall App", "Set To Timelapse Mode",
					TrayIcon.MessageType.INFO);

		} catch (Exception e) {
			System.out.println("Error burst mode:  " + e.getMessage());
			trayIcon.displayMessage("Error", "Set Timelapse Mode: " + e.getMessage(),
					TrayIcon.MessageType.ERROR);
		}
		return ok;
	}

	private static void disablePreview() {
		try {
			helper.setCamLivePreview(false);
			trayIcon.displayMessage("StreamBall App", "Disabled Preview!",
					TrayIcon.MessageType.INFO);

		} catch (Exception e) {
			System.out.println("Error disable preview:  " + e.getMessage());
			trayIcon.displayMessage("Error", "Set disable preview: " + e.getMessage(),
					TrayIcon.MessageType.ERROR);
		}
	}

	private static void setLogFile(String logFileName) {
		try {
			java.io.FileOutputStream outstream = new java.io.FileOutputStream(logFileName);
			System.setErr(new java.io.PrintStream(outstream));
			System.setOut(new java.io.PrintStream(outstream));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static TrayIcon setupTrayIcon() {

		Image icon =
				Toolkit.getDefaultToolkit().getImage(
						GoProImagesExtraction.class.getResource("/images/webcam-32.png"));

		// setup the systemTrayIcon
		TrayIcon trayIcon = new TrayIcon(icon);
		trayIcon.setImageAutoSize(true);

		trayIcon.setPopupMenu(getIconMenu());
		SystemTray sysTray = SystemTray.getSystemTray();
		try {
			sysTray.add(trayIcon);
			trayIcon.displayMessage("StreamBall", "App gestarted", TrayIcon.MessageType.INFO);

		} catch (AWTException e) {
			System.err.println(e);
		}

		return trayIcon;
	}

	private static PopupMenu getIconMenu() {

		PopupMenu fileCheckMenu = new PopupMenu("");

		// create a action listener for the MenuItems
		ActionListener recordingThreadListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				if (recordTimingThread != null) {
					recordTimingThread.doRun = false;
				}
			}
		};

		ActionListener startListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				startCam();
			}
		};

		ActionListener previewListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				disablePreview();
			}
		};

		ActionListener stopistener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				stopCam();
			}
		};

		ActionListener startRecordListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				startRecording("");
			}
		};

		ActionListener stopRecordListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				stopRecording("");
			}
		};

		ActionListener burstModeListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				doBurstMode();
			}
		};

		ActionListener TI_HalfSecListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				doTI_HalfSec();
			}
		};

		ActionListener TI_OneSecListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				doTI_OneSec();
			}
		};

		ActionListener TI_TwoSecListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				doTI_TwoSec();
			}
		};

		ActionListener TI_FiveSecListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				doTI_FiveSec();
			}
		};

		ActionListener timelapseModeListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				doTimelapseMode();
			}
		};

		ActionListener recordModeListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				doRecordMode();
			}
		};

		ActionListener exitListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				System.exit(0);
			}
		};

		ActionListener logListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				try {
					Desktop.getDesktop().open(new File(logFilePath));
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		};

		ActionListener configListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				try {
					Desktop.getDesktop().open(new File(configFileName));
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		};

		// create menu item for the default action
		MenuItem recordingThreadItem = new MenuItem("Stop RecordingThread");
		recordingThreadItem.addActionListener(recordingThreadListener);
		fileCheckMenu.add(recordingThreadItem);

		MenuItem startItem = new MenuItem("Start Cam");
		startItem.addActionListener(startListener);
		fileCheckMenu.add(startItem);

		MenuItem previewItem = new MenuItem("Disable Preview");
		previewItem.addActionListener(previewListener);
		fileCheckMenu.add(previewItem);

		MenuItem burstModeItem = new MenuItem("Set Burst Mode");
		burstModeItem.addActionListener(burstModeListener);
		fileCheckMenu.add(burstModeItem);

		MenuItem recordModeItem = new MenuItem("Set Recording Mode");
		recordModeItem.addActionListener(recordModeListener);
		fileCheckMenu.add(recordModeItem);

		MenuItem timelapseModeItem = new MenuItem("Set Timelapse Mode");
		timelapseModeItem.addActionListener(timelapseModeListener);
		fileCheckMenu.add(timelapseModeItem);

		MenuItem timelapseHalfSecItem = new MenuItem("Set Timelapse 0.5s");
		timelapseHalfSecItem.addActionListener(TI_HalfSecListener);
		fileCheckMenu.add(timelapseHalfSecItem);

		MenuItem timelapseOneSecItem = new MenuItem("Set Timelapse 1s");
		timelapseOneSecItem.addActionListener(TI_OneSecListener);
		fileCheckMenu.add(timelapseOneSecItem);

		MenuItem timelapseTwoSecItem = new MenuItem("Set Timelapse 2s");
		timelapseTwoSecItem.addActionListener(TI_TwoSecListener);
		fileCheckMenu.add(timelapseTwoSecItem);

		MenuItem timelapseFiveSecItem = new MenuItem("Set Timelapse 5s");
		timelapseFiveSecItem.addActionListener(TI_FiveSecListener);
		fileCheckMenu.add(timelapseFiveSecItem);

		MenuItem recordItem = new MenuItem("Start Record");
		recordItem.addActionListener(startRecordListener);
		fileCheckMenu.add(recordItem);

		MenuItem stopRecordItem = new MenuItem("Stop Record");
		stopRecordItem.addActionListener(stopRecordListener);
		fileCheckMenu.add(stopRecordItem);

		MenuItem stopItem = new MenuItem("Stop Cam");
		stopItem.addActionListener(stopistener);
		fileCheckMenu.add(stopItem);

		MenuItem logItem = new MenuItem("Open Log");
		logItem.addActionListener(logListener);
		fileCheckMenu.add(logItem);

		MenuItem configItem = new MenuItem("Open Config");
		configItem.addActionListener(configListener);
		fileCheckMenu.add(configItem);

		MenuItem exitItem = new MenuItem("Exit Streamball App");
		exitItem.addActionListener(exitListener);
		fileCheckMenu.add(exitItem);

		return fileCheckMenu;
	}
}
