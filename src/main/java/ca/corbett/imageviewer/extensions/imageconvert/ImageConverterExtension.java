package ca.corbett.imageviewer.extensions.imageconvert;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;

import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * An ImageViewer extension that allows you to convert either a single image or a directory
 * of images from PNG to Jpeg or vice versa.
 *
 * @author scorbo2
 * @since 2023-12-31 - Happy New Year!
 */
public class ImageConverterExtension extends ImageViewerExtension {

    private final AppExtensionInfo extInfo;

    public ImageConverterExtension() {
        extInfo = AppExtensionInfo.fromExtensionJar(getClass(),
                                                    "/ca/corbett/imageviewer/extensions/imageconvert/extInfo.json");
        if (extInfo == null) {
            throw new RuntimeException("ImageConverterExtension: can't parse extInfo.json!");
        }
    }

    @Override
    public AppExtensionInfo getInfo() {
        return extInfo;
    }

    @Override
    public List<AbstractProperty> getConfigProperties() {
        return null;
    }

    @Override
    public List<JMenuItem> getMenuItems(String topLevelMenu) {
        if ("Edit".equals(topLevelMenu)) {
            List<JMenuItem> list = new ArrayList<>();
            list.add(buildMenuItem());
            return list;
        }
        return null;
    }

    @Override
    public List<JMenuItem> getPopupMenuItems() {
        List<JMenuItem> list = new ArrayList<>();
        list.add(buildMenuItem());
        return list;
    }

    private JMenuItem buildMenuItem() {
        JMenuItem item = new JMenuItem(new ImageConverterAction());
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK));
        return item;
    }

}
