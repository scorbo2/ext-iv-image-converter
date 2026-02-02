package ca.corbett.imageviewer.extensions.imageconvert;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;

import java.awt.event.ActionEvent;
import java.io.File;

/**
 * Shows the ImageConverterDialog if an image is currently selected in the MainWindow.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class ImageConverterAction extends EnhancedAction {

    private static ImageConverterAction instance;

    private static final String NAME = "Convert image...";

    private ImageConverterAction() {
        super(NAME);
    }

    public static ImageConverterAction getInstance() {
        if (instance == null) {
            instance = new ImageConverterAction();
        }
        return instance;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        ImageInstance currentImage = MainWindow.getInstance().getSelectedImage();
        if (currentImage.isEmpty()) {
            MainWindow.getInstance().showMessageDialog(NAME, "Nothing selected.");
            return;
        }

        // Ensure correct browse mode:
        // (we hide the menu item in image set mode, but the keyboard shortcut could still be used)
        if (MainWindow.getInstance().getBrowseMode() == MainWindow.BrowseMode.IMAGE_SET) {
            MainWindow.getInstance().showMessageDialog(NAME,
                                                       "Image conversion is only supported when browsing the file system.");
            return;
        }

        // Ensure correct file format:
        File file = currentImage.getImageFile();
        if (!file.getName().toLowerCase().endsWith("jpg")
            && !file.getName().toLowerCase().endsWith("jpeg")
            && !file.getName().toLowerCase().endsWith("png")) {
            MainWindow.getInstance().showMessageDialog(NAME,
                                                       "Image conversion can currently only be performed on jpeg or png images.");
            return;
        }

        new ImageConverterDialog(currentImage).setVisible(true);
    }
}
