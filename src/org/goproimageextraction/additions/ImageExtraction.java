package org.goproimageextraction.additions;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import com.xuggle.xuggler.Global;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.IVideoResampler;
import com.xuggle.xuggler.Utils;

public class ImageExtraction {

	/** The number of seconds between frames. */

	public static double SECONDS_BETWEEN_FRAMES = 1;

	/** The number of nano-seconds between frames. */

	public static final long NANO_SECONDS_BETWEEN_FRAMES =
			(long) (Global.DEFAULT_PTS_PER_SECOND * SECONDS_BETWEEN_FRAMES);

	/** Time of last frame write. */

	private static long mLastPtsWrite = Global.NO_PTS;

	private String fileName;
	private String subFolder;
	private long filecounter = 0;

	public ImageExtraction(String filename, double imageExtractIntervall) {

		this.fileName = filename;
		SECONDS_BETWEEN_FRAMES = imageExtractIntervall;

		String noExtension = fileName.substring(0, fileName.indexOf("."));
		this.subFolder = noExtension + "_pics";

		File tempDirFile = new File(this.subFolder);

		if (!tempDirFile.exists()) {
			tempDirFile.mkdir();
		}
		tempDirFile = null;

		decodeVideoFile(fileName);

		// start thread for image effect

		// find the actual image file names
		// create a loop for every file...
		// ImageEffectThread postEffect = new ImageEffectThread(new File());
		// postEffect.run();

	}

	private void processFrame(IVideoPicture picture, BufferedImage image) {
		try {
			// if uninitialized, backdate mLastPtsWrite so we get the very
			// first frame

			if (mLastPtsWrite == Global.NO_PTS)
				mLastPtsWrite = picture.getPts() - NANO_SECONDS_BETWEEN_FRAMES;

			// if it's time to write the next frame

			if (picture.getPts() - mLastPtsWrite >= NANO_SECONDS_BETWEEN_FRAMES) {
				// Make a temorary file name

				// File file = File.createTempFile("frame", ".png");
				File file =
						new File(this.subFolder + File.separator + "frame_"
								+ this.SECONDS_BETWEEN_FRAMES + "_" + filecounter + ".png");

				// write out PNG

				ImageIO.write(image, "png", file);
				filecounter++;
				// indicate file written

				double seconds = ((double) picture.getPts()) / Global.DEFAULT_PTS_PER_SECOND;
				System.out.printf("\n at elapsed time of %6.3f seconds wrote: %s\n", seconds, file);

				// start image post effect

				file = null;

				// update last write time
				mLastPtsWrite += NANO_SECONDS_BETWEEN_FRAMES;
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(this.getClass() + " processFrame Error: " + e.getMessage());
		}
	}

	/**
	 * Takes a media container (file) as the first argument, opens it, reads
	 * through the file and captures video frames periodically as specified by
	 * SECONDS_BETWEEN_FRAMES. The frames are written as PNG files into the
	 * system's temporary directory.
	 * 
	 * @param args
	 *            must contain one string which represents a filename
	 */

	@SuppressWarnings("deprecation")
	private void decodeVideoFile(String filename) {

		if (filename.length() <= 0)
			throw new IllegalArgumentException("must pass in a filename as the first argument");

		// make sure that we can actually convert video pixel formats

		if (!IVideoResampler.isSupported(IVideoResampler.Feature.FEATURE_COLORSPACECONVERSION))
			throw new RuntimeException(
					"you must install the GPL version of Xuggler (with IVideoResampler"
							+ " support) for this demo to work");

		// create a Xuggler container object

		IContainer container = IContainer.make();

		// open up the container

		if (container.open(filename, IContainer.Type.READ, null) < 0)
			throw new IllegalArgumentException("could not open file: " + filename);

		// query how many streams the call to open found

		int numStreams = container.getNumStreams();

		// and iterate through the streams to find the first video stream

		int videoStreamId = -1;
		IStreamCoder videoCoder = null;
		for (int i = 0; i < numStreams; i++) {
			// find the stream object

			IStream stream = container.getStream(i);

			// get the pre-configured decoder that can decode this stream;

			IStreamCoder coder = stream.getStreamCoder();

			if (coder.getCodecType() == ICodec.Type.CODEC_TYPE_VIDEO) {
				videoStreamId = i;
				videoCoder = coder;
				break;
			}
		}

		if (videoStreamId == -1)
			throw new RuntimeException("could not find video stream in container: " + filename);

		// Now we have found the video stream in this file. Let's open up
		// our decoder so it can do work

		if (videoCoder.open() < 0)
			throw new RuntimeException("could not open video decoder for container: " + filename);

		IVideoResampler resampler = null;
		if (videoCoder.getPixelType() != IPixelFormat.Type.BGR24) {
			// if this stream is not in BGR24, we're going to need to
			// convert it. The VideoResampler does that for us.

			resampler =
					IVideoResampler.make(videoCoder.getWidth(), videoCoder.getHeight(),
							IPixelFormat.Type.BGR24, videoCoder.getWidth(), videoCoder.getHeight(),
							videoCoder.getPixelType());
			if (resampler == null)
				throw new RuntimeException("could not create color space resampler for: "
						+ filename);
		}

		// Now, we start walking through the container looking at each packet.

		IPacket packet = IPacket.make();
		while (container.readNextPacket(packet) >= 0) {

			// Now we have a packet, let's see if it belongs to our video strea

			if (packet.getStreamIndex() == videoStreamId) {
				// We allocate a new picture to get the data out of Xuggle

				IVideoPicture picture =
						IVideoPicture.make(videoCoder.getPixelType(), videoCoder.getWidth(),
								videoCoder.getHeight());

				int offset = 0;
				while (offset < packet.getSize()) {
					// Now, we decode the video, checking for any errors.

					int bytesDecoded = videoCoder.decodeVideo(picture, packet, offset);
					if (bytesDecoded < 0)
						throw new RuntimeException("got error decoding video in: " + filename);
					offset += bytesDecoded;

					// Some decoders will consume data in a packet, but will not
					// be able to construct a full video picture yet. Therefore
					// you should always check if you got a complete picture
					// from
					// the decode.

					if (picture.isComplete()) {
						IVideoPicture newPic = picture;

						// If the resampler is not null, it means we didn't get
						// the
						// video in BGR24 format and need to convert it into
						// BGR24
						// format.

						if (resampler != null) {
							// we must resample
							newPic =
									IVideoPicture.make(resampler.getOutputPixelFormat(),
											picture.getWidth(), picture.getHeight());
							if (resampler.resample(newPic, picture) < 0)
								throw new RuntimeException("could not resample video from: "
										+ filename);
						}

						if (newPic.getPixelType() != IPixelFormat.Type.BGR24)
							throw new RuntimeException(
									"could not decode video as BGR 24 bit data in: " + filename);

						// convert the BGR24 to an Java buffered image

						BufferedImage javaImage = Utils.videoPictureToImage(newPic);

						// process the video frame

						processFrame(newPic, javaImage);
						// javaImage.flush();
						javaImage = null;
					}
				}
			} else {
				// This packet isn't part of our video stream, so we just
				// silently drop it.
				do {
				} while (false);
			}
		}

		// Technically since we're exiting anyway, these will be cleaned up
		// by the garbage collector... but because we're nice people and
		// want to be invited places for Christmas, we're going to show how
		// to clean up.

		if (videoCoder != null) {
			videoCoder.close();
			videoCoder = null;
		}
		if (container != null) {
			container.close();
			container = null;
		}
	}

}
