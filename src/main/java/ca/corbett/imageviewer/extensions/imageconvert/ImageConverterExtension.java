package ca.corbett.imageviewer.extensions.imageconvert;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.EnhancedAction;
import ca.corbett.extras.io.KeyStrokeManager;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.KeyStrokeProperty;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;
import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.imageviewer.ui.ReservedKeyStrokeWorkaround;

import java.util.ArrayList;
import java.util.List;

/**
 * An ImageViewer extension that allows you to convert either a single image or a directory
 * of images from PNG to Jpeg or vice versa.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since 2023-12-31 - Happy New Year!
 */
public class ImageConverterExtension extends ImageViewerExtension {

    private static final String keyStrokeProp = AppConfig.KEYSTROKE_PREFIX + "Image Converter.convertKeyStroke";
    private final AppExtensionInfo extInfo;

    public ImageConverterExtension() {
        extInfo = AppExtensionInfo.fromExtensionJar(getClass(),
                                                    "/ca/corbett/imageviewer/extensions/imageconvert/extInfo.json");
        if (extInfo == null) {
            throw new RuntimeException("ImageConverterExtension: can't parse extInfo.json!");
        }
    }

    @Override
    public void loadJarResources() {
        // Nothing to load here.
    }

    @Override
    public AppExtensionInfo getInfo() {
        return extInfo;
    }

    @Override
    protected List<AbstractProperty> createConfigProperties() {
        List<AbstractProperty> props = new ArrayList<>();

        props.add(new KeyStrokeProperty(keyStrokeProp, "Image converter:",
                                        KeyStrokeManager.parseKeyStroke("Ctrl+J"),
                                        ImageConverterAction.getInstance())
                      .setAllowBlank(true)
                      .addFormFieldGenerationListener(new ReservedKeyStrokeWorkaround()));

        return props;
    }

    @Override
    public List<EnhancedAction> getMenuActions(String topLevelMenu, MainWindow.BrowseMode browseMode) {
        // We don't support image set mode, because image sets can
        // contain images from many different directories, which would break our dialog.
        // We COULD consider this as a future feature, but the UI might be very confusing.
        if (browseMode == MainWindow.BrowseMode.IMAGE_SET) {
            return null;
        }

        if ("Edit".equals(topLevelMenu)) {
            return List.of(ImageConverterAction.getInstance());
        }

        return null;
    }

    @Override
    public List<EnhancedAction> getPopupMenuActions(MainWindow.BrowseMode browseMode) {
        // We don't support image set mode, because image sets can
        // contain images from many different directories, which would break our dialog.
        // We COULD consider this as a future feature, but the UI might be very confusing.
        if (browseMode == MainWindow.BrowseMode.IMAGE_SET) {
            return null;
        }

        return List.of(ImageConverterAction.getInstance());
    }
}
