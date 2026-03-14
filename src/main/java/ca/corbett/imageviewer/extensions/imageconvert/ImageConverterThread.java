package ca.corbett.imageviewer.extensions.imageconvert;

import ca.corbett.extras.image.ImageUtil;
import ca.corbett.extras.logging.Stopwatch;
import ca.corbett.extras.progress.SimpleProgressWorker;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A worker thread for handling batch conversion of entire directories of images
 * at a time. Launched from ImageConverterDialog. Much of this code was copied
 * from ImageResizeThread in the ImageResize extension.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 2023-12-31
 */
public class ImageConverterThread extends SimpleProgressWorker {

    private final static Logger logger = Logger.getLogger(ImageConverterThread.class.getName());

    private final ImageConverterDialog owner;
    private final List<File> fileList;
    private final boolean extraLogging;
    private int convertedCount;
    private int skippedCount;
    private int problemCount;
    private long totalTimeSpent;
    private boolean wasCanceled;

    /**
     * You must supply the ImageConverterDialog that launched this thread, along with a
     * list of files on which to operate.
     *
     * @param owner        The ImageConverterDialog that launched this thread.
     * @param list         A List of files on which to operate.
     * @param extraLogging if enabled, will log a message for each conversion.
     */
    public ImageConverterThread(ImageConverterDialog owner, List<File> list, boolean extraLogging) {
        this.owner = owner;
        this.fileList = list;
        this.extraLogging = extraLogging;
    }

    public int getProcessedCount() {
        return fileList.size();
    }

    public int getConvertedCount() {
        return convertedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public int getProblemCount() {
        return problemCount;
    }

    public boolean wasCanceled() {
        return wasCanceled;
    }

    public long getTotalTimeSpent() {
        return totalTimeSpent;
    }

    @Override
    public void run() {
        convertedCount = 0;
        skippedCount = 0;
        problemCount = 0;
        totalTimeSpent = 0;
        wasCanceled = false;
        try {
            fireProgressBegins(fileList.size());
            int i = 0;
            for (File file : fileList) {
                if (!fireProgressUpdate(i, file.getName())) {
                    wasCanceled = true;
                    break;
                }
                try {
                    BufferedImage image = ImageUtil.loadImage(file);
                    Stopwatch.start("imageConvert");
                    ImageConverterDialog.OperationOutcome outcome = owner.convertImage(file, image, false);
                    Stopwatch.stop("imageConvert");
                    totalTimeSpent += Stopwatch.report("imageConvert");
                    switch (outcome) {
                        case SkippedBecauseExists:
                            if (extraLogging) {
                                logger.log(Level.INFO, "Skipped {0} (already exists)",
                                           new Object[]{file.getName()});
                            }
                            skippedCount++;
                            break;
                        case InternalError:
                            if (extraLogging) {
                                logger.log(Level.SEVERE, "Problem converting {0}",
                                           new Object[]{file.getName()});
                            }
                            problemCount++;
                            break;
                        case Success:
                            if (extraLogging) {
                                logger.log(Level.INFO, "Converted {0} in {1}",
                                           new Object[]{file.getName(), Stopwatch.reportFormatted("imageConvert")});
                            }
                            convertedCount++;
                            break;
                    }

                    image.flush();
                }
                catch (IOException ioe) {
                    problemCount++;
                    logger.log(Level.SEVERE,
                               "convertImage: Caught exception while converting " + file.getAbsolutePath() + ": " + ioe.getMessage(),
                               ioe);
                }

                // Next file:
                i++;
            }
        }
        finally {
            // Make sure we always send the completion event, otherwise
            // the progress dialog will never close:
            if (wasCanceled) {
                fireProgressCanceled();
            }
            else {
                fireProgressComplete();
            }
        }
    }
}
