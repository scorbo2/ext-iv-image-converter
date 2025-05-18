package ca.corbett.imageviewer.extensions.imageconvert;

import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.extras.image.ImageUtil;
import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.MessageUtil;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.fields.CheckBoxField;
import ca.corbett.forms.fields.ComboField;
import ca.corbett.forms.fields.NumberField;
import org.apache.commons.io.FilenameUtils;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.BevelBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
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
 * @author scorbo2
 * @since 2023-12-29
 */
public class ImageConverterDialog extends JDialog implements KeyEventDispatcher {

    public enum OperationOutcome {
        SkippedBecauseExists, InternalError, Success
    }

    private static final Logger logger = Logger.getLogger(ImageConverterDialog.class.getName());

    private final ImageInstance selectedImage;

    private MessageUtil messageUtil;

    private ComboField conversionQuantityChooser;
    private ComboField conversionTypeChooser;
    private CheckBoxField deleteOriginalCheckbox;
    private CheckBoxField overwriteIfExistsCheckbox;
    private NumberField jpegQualityField;
    private CheckBoxField preserveDateCheckbox;
    private CheckBoxField extraLoggingCheckbox;

    public ImageConverterDialog(ImageInstance image) {
        super(MainWindow.getInstance(), "Convert image");
        this.selectedImage = image;
        setSize(new Dimension(480, 300));
        setMinimumSize(new Dimension(480, 300));
        setResizable(false);
        setLocationRelativeTo(MainWindow.getInstance());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        initComponents();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            loadImageDetails();
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
        }
    }

    @Override
    public void dispose() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
        super.dispose();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (!isActive()) {
            return false; // don't capture keystrokes if this dialog isn't showing.
        }

        if (e.getID() == KeyEvent.KEY_RELEASED) {
            switch (e.getKeyCode()) {

                case KeyEvent.VK_ESCAPE:
                    dispose();
                    break;

                case KeyEvent.VK_ENTER:
                    okHandler();
                    break;
            }
        }

        return false;
    }

    /**
     * Converts the given image in the given source file using the current conversion parameters
     * in this dialog.
     *
     * @param srcFile The file containing the image to be converted.
     * @param image   The image data.
     * @return An OperationOutcome that described what happened.
     */
    public OperationOutcome convertImage(File srcFile, BufferedImage image) {
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
        dispose();
        return OperationOutcome.Success;
    }

    private void convertImage() {
        OperationOutcome outcome = convertImage(selectedImage.getImageFile(), selectedImage.getRegularImage());
        switch (outcome) {
            case InternalError:
                getMessageUtil().error("Conversion error", "An internal error occurred. Check the log for details.");
                break;
            case SkippedBecauseExists:
                getMessageUtil().info("Conversion skipped", "Conversion was skipped because the output file exists.");
                break;
            case Success:
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
        MainWindow.getInstance().disableDirTree();
        new Thread(worker).start();
        dispose();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        add(buildControlPanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);
    }

    private FormPanel buildControlPanel() {
        FormPanel formPanel = new FormPanel(FormPanel.Alignment.TOP_CENTER);

        List<String> options = new ArrayList<>();
        options.add("Selected image");
        options.add("All images in this directory");
        options.add("All images recursively");
        conversionQuantityChooser = new ComboField("Convert:", options, 0, false);
        conversionQuantityChooser.setMargins(16, 4, 4, 4, 4);
        formPanel.addFormField(conversionQuantityChooser);

        options = new ArrayList<>();
        options.add("Jpeg -> PNG");
        options.add("PNG -> Jpeg");
        conversionTypeChooser = new ComboField("Format:", options, 0, false);
        conversionTypeChooser.addValueChangedAction(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                jpegQualityField.setEnabled(conversionTypeChooser.getSelectedIndex() == 1);
            }

        });
        formPanel.addFormField(conversionTypeChooser);

        deleteOriginalCheckbox = new CheckBoxField("Remove source file(s) after conversion", false);
        formPanel.addFormField(deleteOriginalCheckbox);

        overwriteIfExistsCheckbox = new CheckBoxField("Overwrite target file(s) if they exist", false);
        formPanel.addFormField(overwriteIfExistsCheckbox);

        jpegQualityField = new NumberField("Jpeg quality:", 95, 60, 99, 1);
        jpegQualityField.setEnabled(false);
        formPanel.addFormField(jpegQualityField);

        preserveDateCheckbox = new CheckBoxField("Preserve file date/time when converting", true);
        formPanel.addFormField(preserveDateCheckbox);

        extraLoggingCheckbox = new CheckBoxField("Log each conversion result", false);
        formPanel.addFormField(extraLoggingCheckbox);

        formPanel.render();
        return formPanel;
    }

    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
        panel.setLayout(new FlowLayout(FlowLayout.RIGHT));

        JButton button = new JButton("OK");
        button.setPreferredSize(new Dimension(90, 23));
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                okHandler();
            }

        });
        panel.add(button);

        button = new JButton("Cancel");
        button.setPreferredSize(new Dimension(90, 23));
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }

        });
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

}
