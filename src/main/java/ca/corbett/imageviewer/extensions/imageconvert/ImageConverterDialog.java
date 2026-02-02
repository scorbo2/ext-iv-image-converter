package ca.corbett.imageviewer.extensions.imageconvert;

import ca.corbett.extras.MessageUtil;
import ca.corbett.extras.image.ImageUtil;
import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.io.KeyStrokeManager;
import ca.corbett.extras.logging.Stopwatch;
import ca.corbett.extras.progress.MultiProgressDialog;
import ca.corbett.extras.progress.SimpleProgressAdapter;
import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.Margins;
import ca.corbett.forms.fields.CheckBoxField;
import ca.corbett.forms.fields.ComboField;
import ca.corbett.forms.fields.NumberField;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.imageviewer.ui.ThumbCacheManager;
import org.apache.commons.io.FilenameUtils;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Presents a dialog with options for converting either a single images or a directory
 * of images from jpeg to png format, or vice versa.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 2023-12-29
 */
public class ImageConverterDialog extends JDialog {

    public enum OperationOutcome {
        SkippedBecauseExists, InternalError, Success
    }

    private static final Logger logger = Logger.getLogger(ImageConverterDialog.class.getName());

    private final ImageInstance selectedImage;
    private final KeyStrokeManager keyStrokeManager;

    private MessageUtil messageUtil;

    private ComboField<String> conversionQuantityChooser;
    private ComboField<String> conversionTypeChooser;
    private CheckBoxField deleteOriginalCheckbox;
    private CheckBoxField overwriteIfExistsCheckbox;
    private NumberField jpegQualityField;
    private CheckBoxField preserveDateCheckbox;
    private CheckBoxField extraLoggingCheckbox;

    public ImageConverterDialog(ImageInstance image) {
        super(MainWindow.getInstance(), "Convert image");
        this.selectedImage = image;
        this.keyStrokeManager = new KeyStrokeManager(this);
        configureKeyStrokes();
        setSize(new Dimension(480, 300));
        setMinimumSize(new Dimension(480, 300));
        setResizable(false);
        setLocationRelativeTo(MainWindow.getInstance());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setModal(true);
        initComponents();
        loadImageDetails();
    }

    private void configureKeyStrokes() {
        keyStrokeManager.clear();
        keyStrokeManager.registerHandler("esc", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        keyStrokeManager.registerHandler("enter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                okHandler();
            }
        });
    }

    /**
     * Converts the given image in the given source file using the current conversion parameters
     * in this dialog.
     *
     * @param srcFile The file containing the image to be converted.
     * @param image   The image data.
     * @param disposeOnSuccess if true, the dialog will be disposed if the conversion is successful.
     * @return An OperationOutcome that described what happened.
     */
    public OperationOutcome convertImage(File srcFile, BufferedImage image, boolean disposeOnSuccess) {
        final String targetExtension = conversionTypeChooser.getSelectedIndex() == 0 ? ".png" : ".jpg";
        File targetFile = new File(srcFile.getParentFile(),
                                   FilenameUtils.getBaseName(srcFile.getName()) + targetExtension);

        // Check if the output file already exists:
        if (targetFile.exists() && !overwriteIfExistsCheckbox.isChecked()) {
            logger.log(Level.INFO,
                       "Skipping conversion of \"{0}\" because the output file exists and \"overwrite if exists\" is not selected.",
                       srcFile.getAbsolutePath());
            return OperationOutcome.SkippedBecauseExists;
        }

        try {

            BasicFileAttributes view = Files.getFileAttributeView(srcFile.toPath(), BasicFileAttributeView.class)
                                            .readAttributes();
            FileTime srcFileCreationTime = view.creationTime();

            if (".png".equals(targetExtension)) {
                Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("png");
                ImageWriter imageWriter = null;
                if (iter.hasNext()) {
                    imageWriter = iter.next();
                }
                if (imageWriter == null) {
                    throw new IOException("No PNG ImageWriter exists on this system; unable to convert.");
                }

                ImageUtil.saveImage(image, targetFile, imageWriter, null);
            }

            else {
                ImageUtil.saveImage(image, targetFile, jpegQualityField.getCurrentValue().floatValue() / 100f);
            }

            // Delete the source file if successful and if so directed:
            if (deleteOriginalCheckbox.isChecked()) {
                srcFile.delete();

                // Let the thumbnail manager know the source file is gone:
                // (we MIGHT want to go through ImageOperationHandler to report this move,
                //  as other extensions might need to know that the image file has effectively moved...
                //  This code will break if https://github.com/scorbo2/imageviewer/issues/40 is ever addressed.
                //  Right now it will work, because companion files only consider the base filename,
                //  not the extension. Still, this feels sloppy.)
                ThumbCacheManager.remove(srcFile);

                // Notify the ImageSetManager that this image has moved:
                // (note: if deleteOriginal is not selected, we'll skip this and just
                //  leave the original image in the image set. User can sort it out as needed).
                MainWindow.getInstance().getImageSetManager().imageMoved(srcFile, targetFile);
            }

            // Modify the target file to have the same creation time as the source file:
            if (preserveDateCheckbox.isChecked()) {
                Files.setLastModifiedTime(targetFile.toPath(), srcFileCreationTime);
            }
        }
        catch (IOException ioe) {
            logger.log(Level.SEVERE, "Image conversion error: " + ioe.getMessage(), ioe);
            return OperationOutcome.InternalError;
        }
        if (disposeOnSuccess) {
            dispose();
        }
        return OperationOutcome.Success;
    }

    private void convertImage() {
        Stopwatch.start("imageConvert");
        OperationOutcome outcome = convertImage(selectedImage.getImageFile(), selectedImage.getRegularImage(), true);
        Stopwatch.stop("imageConvert");
        switch (outcome) {
            case InternalError:
                if (extraLoggingCheckbox.isChecked()) {
                    logger.log(Level.SEVERE, "Problem converting {0}",
                               new Object[]{selectedImage.getImageFileName()});
                }
                getMessageUtil().error("Conversion error", "An internal error occurred. Check the log for details.");
                break;
            case SkippedBecauseExists:
                if (extraLoggingCheckbox.isChecked()) {
                    logger.log(Level.INFO, "Skipped {0} (already exists)",
                               new Object[]{selectedImage.getImageFileName()});
                }
                getMessageUtil().info("Conversion skipped", "Conversion was skipped because the output file exists.");
                break;
            case Success:
                if (extraLoggingCheckbox.isChecked()) {
                    logger.log(Level.INFO, "Converted {0} in {1}",
                               new Object[]{selectedImage.getImageFileName(),
                                   Stopwatch.reportFormatted("imageConvert")});
                }
                MainWindow.getInstance().reload();
                getMessageUtil().info("Conversion complete", "The file was successfully converted.");
                break;
        }
    }

    private void convertBulk(boolean recursive) {
        boolean toPng = conversionTypeChooser.getSelectedIndex() == 0;
        List<String> extensions = new ArrayList<>();
        if (toPng) {
            extensions.add("jpg");
            extensions.add("jpeg");
        }
        else {
            extensions.add("png");
        }
        List<File> fileList = FileSystemUtil.findFiles(selectedImage.getImageFile().getParentFile(), recursive,
                                                       extensions);
        String extraPrompt = recursive ? " recursively" : "";
        String warning = deleteOriginalCheckbox.isChecked() ? "Original images will be deleted upon completion." : "Original images will not be deleted.";

        if (JOptionPane.showConfirmDialog(this,
                                          "Perform bulk conversion on all " + fileList.size() + " images in this directory" + extraPrompt + "?\n" + warning,
                                          "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }

        ImageConverterThread worker = new ImageConverterThread(this, fileList, extraLoggingCheckbox.isChecked());
        worker.addProgressListener(new ThreadProgressListener(this, worker));
        MultiProgressDialog progressDialog = new MultiProgressDialog(this, "Converting images...");
        progressDialog.setInitialShowDelayMS(250); // Don't show for very fast conversions
        progressDialog.runWorker(worker, true);
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        add(buildControlPanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);
    }

    private FormPanel buildControlPanel() {
        FormPanel formPanel = new FormPanel(Alignment.TOP_CENTER);

        List<String> options = new ArrayList<>();
        options.add("Selected image");
        options.add("All images in this directory");
        options.add("All images recursively");
        conversionQuantityChooser = new ComboField<>("Convert:", options, 0, false);
        conversionQuantityChooser.setMargins(new Margins(16, 4, 4, 4, 4));
        formPanel.add(conversionQuantityChooser);

        options = new ArrayList<>();
        options.add("Jpeg -> PNG");
        options.add("PNG -> Jpeg");
        conversionTypeChooser = new ComboField<>("Format:", options, 0, false);
        conversionTypeChooser.addValueChangedListener(
            field -> jpegQualityField.setEnabled(conversionTypeChooser.getSelectedIndex() == 1));
        formPanel.add(conversionTypeChooser);

        deleteOriginalCheckbox = new CheckBoxField("Remove source file(s) after conversion", false);
        formPanel.add(deleteOriginalCheckbox);

        overwriteIfExistsCheckbox = new CheckBoxField("Overwrite target file(s) if they exist", false);
        formPanel.add(overwriteIfExistsCheckbox);

        jpegQualityField = new NumberField("Jpeg quality:", 95, 60, 99, 1);
        jpegQualityField.setEnabled(false);
        formPanel.add(jpegQualityField);

        preserveDateCheckbox = new CheckBoxField("Preserve file date/time when converting", true);
        formPanel.add(preserveDateCheckbox);

        extraLoggingCheckbox = new CheckBoxField("Log each conversion result", false);
        formPanel.add(extraLoggingCheckbox);

        return formPanel;
    }

    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
        panel.setLayout(new FlowLayout(FlowLayout.RIGHT));

        JButton button = new JButton("OK");
        button.setPreferredSize(new Dimension(90, 23));
        button.addActionListener(e -> okHandler());
        panel.add(button);

        button = new JButton("Cancel");
        button.setPreferredSize(new Dimension(90, 23));
        button.addActionListener(e -> dispose());
        panel.add(button);

        return panel;
    }

    private void okHandler() {
        if (conversionQuantityChooser.getSelectedIndex() == 0) {
            convertImage();
        }
        else {
            convertBulk(conversionQuantityChooser.getSelectedIndex() == 2);
        }
    }

    private void loadImageDetails() {
        conversionQuantityChooser.setSelectedIndex(0);
        boolean isPng = selectedImage.getImageFileName().toLowerCase().endsWith(".png");
        conversionTypeChooser.setSelectedIndex(isPng ? 1 : 0);
    }

    MessageUtil getMessageUtil() {
        if (messageUtil == null) {
            messageUtil = new MessageUtil(this, logger);
        }
        return messageUtil;
    }

    /**
     * Listens to our ImageConverterThread and shows appropriate messages when done or canceled.
     * Note that these callbacks fire on the worker thread, not on the EDT!
     * We need to take care to switch to the EDT when showing dialogs.
     */
    private static class ThreadProgressListener extends SimpleProgressAdapter {

        private final ImageConverterDialog ownerDialog;
        private final ImageConverterThread thread;

        public ThreadProgressListener(ImageConverterDialog owner, ImageConverterThread thread) {
            this.ownerDialog = owner;
            this.thread = thread;
        }

        @Override
        public void progressCanceled() {
            SwingUtilities.invokeLater(() -> {
                if (thread.getConvertedCount() > 0) {
                    MainWindow.getInstance().reload();
                }
                MainWindow.getInstance().showMessageDialog("Conversion canceled",
                                                           "The conversion operation was canceled while in progress.\n"
                                                               + thread.getConvertedCount()
                                                               + " images were converted before the cancellation.");
            });
        }

        @Override
        public void progressComplete() {
            String msg = "The conversion operation evaluated "
                + thread.getProcessedCount()
                + " images.\n"
                + thread.getConvertedCount()
                + " were converted and "
                + thread.getSkippedCount()
                + " were skipped.\n"
                + "Total time spent converting images: "
                + Stopwatch.formatTimeValue(thread.getTotalTimeSpent()) + "\n";
            if (thread.getProblemCount() > 0) {
                msg += thread.getProblemCount() + " problems were encountered (see log file).";
            }

            final String message = msg;
            MainWindow mw = MainWindow.getInstance();
            SwingUtilities.invokeLater(() -> {
                MainWindow.getInstance().reload();
                ownerDialog.dispose();
                mw.showMessageDialog("Conversion complete", message);
            });
        }
    }
}
