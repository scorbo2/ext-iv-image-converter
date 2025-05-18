package ca.corbett.imageviewer.extensions.imageconvert;

import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.extras.image.ImageUtil;
import ca.corbett.extras.logging.Stopwatch;

import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A worker thread for handling batch conversion of entire directories of images
 * at a time. Launched from ImageConverterDialog. Much of this code was copied
 * from ImageResizeThread.
 *
 * @author scorbett
 * @since 2023-12-31
 */
public class ImageConverterThread implements Runnable {

    private final static Logger logger = Logger.getLogger(ImageConverterThread.class.getName());

    private final ImageConverterDialog owner;
    private final List<File> fileList;
    private final boolean extraLogging;
    private ProgressMonitor monitor;
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
        initialize();
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

    /**
     * Invoked internally to initialize the worker thread.
     */
    private void initialize() {
        int min = 0;
        int max = 100;
        if (fileList != null && !fileList.isEmpty()) {
            max = fileList.size();
        }
        monitor = new ProgressMonitor(MainWindow.getInstance(), "Converting...", "Please wait", min, max);
        monitor.setMillisToDecideToPopup(200);
        monitor.setMillisToPopup(200);
    }

    @Override
    public void run() {
        convertedCount = 0;
        skippedCount = 0;
        problemCount = 0;
        totalTimeSpent = 0;
        wasCanceled = false;
        int i = 1;
        for (File file : fileList) {
            if (monitor.isCanceled()) {
                wasCanceled = true;
                break;
            }
            try {
                monitor.setNote("Converting " + file.getName());
                BufferedImage image = ImageUtil.loadImage(file);
                monitor.setProgress(i++);
                Stopwatch.start("imageConvert");
                ImageConverterDialog.OperationOutcome outcome = owner.convertImage(file, image);
                Stopwatch.stop("imageConvert");
                totalTimeSpent += Stopwatch.report("imageConvert");
                switch (outcome) {
                    case SkippedBecauseExists:
                        skippedCount++;
                        break;
                    case InternalError:
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
                           "convertImage: Caught exception while resizing " + file.getAbsolutePath() + ": " + ioe.getMessage(),
                           ioe);
            }
        }
        final ImageConverterThread thisThread = this;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                thisThread.conversionCompleteHandler();
            }

        });

        monitor.close();
    }

    private void conversionCompleteHandler() {
        MainWindow.getInstance().enableDirTree();
        MainWindow.getInstance().reloadCurrentDirectory();

        if (wasCanceled()) {
            MainWindow.getInstance().showMessageDialog("Conversion canceled",
                                                       "The conversion operation was canceled while in progress.\n"
                                                           + getConvertedCount() + " images were converted before the cancellation.");
            return;
        }

        String msg = "The conversion operation evaluated " + getProcessedCount() + " images.\n"
            + getConvertedCount() + " were converted and " + getSkippedCount() + " were skipped.\n"
            + "Total time spent converting images: " + Stopwatch.formatTimeValue(totalTimeSpent) + "\n";
        if (getProblemCount() > 0) {
            msg += getProblemCount() + " problems were encountered (see log file).";
        }

        MainWindow.getInstance().showMessageDialog("Conversion complete", msg);

    }

}
