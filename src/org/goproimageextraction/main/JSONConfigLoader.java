package org.goproimageextraction.main;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * @author Dominik Haas
 * 
 *         loads a JSON config definition out of the given file. uses the
 *         json-simple-1.1.1.jar
 */
public class JSONConfigLoader {

	// default: use the folder in which the application is
	private String configDir = System.getProperty("user.dir");

	private String configFilePath;

	/**
	 * The System.getProperty("user.dir") is the default for the path of the
	 * config file
	 * 
	 * @param configFileName
	 *            inculde file extension
	 */

	public JSONConfigLoader(String configFileName) {

		this.configFilePath = configDir + File.separator + configFileName;
	}

	/**
	 * @param configFileName
	 *            inculde file extension
	 * @param configFilePath
	 *            to overwrite the path where the config file is stored
	 */
	public JSONConfigLoader(String configFileName, String configFilePath) {
		this.configFilePath = configFilePath + File.separator + configFileName;
	}

	/**
	 * 
	 * @return {@link JSONObject}
	 */
	public JSONObject loadConfig() {

		String JSONConfig = null;
		try {
			JSONConfig = readFileToString(configFilePath, Charset.defaultCharset());
		} catch (IOException e1) {
			System.out.println("JSON Config reading the whole file: " + configFilePath
					+ " had an error");
			e1.printStackTrace();
		}

		JSONParser parser = new JSONParser();

		JSONObject configs = null;

		if (JSONConfig != null) {
			try {
				configs = (JSONObject) parser.parse(JSONConfig);
			} catch (ParseException e) {
				System.out.println("JSON Config parsing error file: " + configFilePath);
				e.printStackTrace();
			}
		}

		return configs;
	}

	// copy & past from
	// http://stackoverflow.com/questions/326390/how-to-create-a-java-string-from-the-contents-of-a-file
	public static String readFileToString(String path, Charset encoding) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return encoding.decode(ByteBuffer.wrap(encoded)).toString();
	}

	public File getRawConfigFile() {
		return new File(this.configFilePath);
	}
}
