//
// GCALDaemon is an OS-independent Java program that offers two-way
// synchronization between Google Calendar and various iCalalendar (RFC 2445)
// compatible calendar applications (Sunbird, Rainlendar, iCal, Lightning, etc).
//
// Apache License
// Version 2.0, January 2004
// http://www.apache.org/licenses/
// 
// Project home:
// http://gcaldaemon.sourceforge.net
//
package org.gcaldaemon.gui.editors;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.core.notifier.GmailNotifier;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.Messages;
import org.gcaldaemon.gui.config.MainConfig;

/**
 * Window style property editor for the Gmail notifier.
 * 
 * Created: May 25, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class StyleEditor extends AbstractEditor implements ItemListener,
		ActionListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = 6805766620829052914L;

	// --- GUI ---

	private final JPanel panel = new JPanel();
	private final JLabel label = new JLabel();
	private final JComboBox combo = new JComboBox();
	private final JButton browse = new JButton();
	private final JLabel preview = new JLabel();

	// --- CONSTRUCTOR ---

	public StyleEditor(MainConfig config, ConfigEditor editor) throws Exception {
		super(config, editor, Configurator.NOTIFIER_WINDOW_STYLE);
		combo.addItem("default"); //$NON-NLS-1$
		combo.addItem("mail"); //$NON-NLS-1$
		combo.addItem("blue"); //$NON-NLS-1$
		combo.addItem("gray"); //$NON-NLS-1$
		combo.addItem("green"); //$NON-NLS-1$
		combo.addItem("kde"); //$NON-NLS-1$
		combo.addItem("yellow"); //$NON-NLS-1$
		combo.addItem("metal"); //$NON-NLS-1$
		combo.addItem("white"); //$NON-NLS-1$
		combo.addItem("xbox"); //$NON-NLS-1$
		combo.setEditable(true);
		String style = config.getConfigProperty(key, "default"); //$NON-NLS-1$
		combo.setSelectedItem(style);

		browse.addActionListener(this);
		browse.setIcon(editor.getIcon("open")); //$NON-NLS-1$
		browse.setToolTipText(Messages.getString("browse")); //$NON-NLS-1$

		BorderLayout layout = new BorderLayout();
		layout.setVgap(5);
		setLayout(layout);

		BorderLayout borderLayout = new BorderLayout();
		borderLayout.setHgap(5);
		panel.setLayout(borderLayout);
		panel.add(label, BorderLayout.WEST);
		panel.add(combo, BorderLayout.CENTER);
		panel.add(browse, BorderLayout.EAST);
		combo.addItemListener(this);
		label.setText(Messages.getString(key) + ':');
		label.setPreferredSize(LABEL_SIZE);

		add(panel, BorderLayout.NORTH);

		preview.setBorder(BorderFactory.createTitledBorder(Messages
				.getString("preview"))); //$NON-NLS-1$
		preview.setToolTipText(Messages.getString("preview")); //$NON-NLS-1$
		preview.setPreferredSize(new Dimension(600, 160));
		preview.setHorizontalAlignment(JLabel.CENTER);
		preview.setVerticalAlignment(JLabel.CENTER);
		add(preview, BorderLayout.CENTER);

		setImage(style);
	}

	// --- VALUE CHANGED ---

	public final void itemStateChanged(ItemEvent evt) {
		String value = (String) combo.getSelectedItem();
		value = value.replace('\\', '/');
		if (!value.equals(config.getConfigProperty(key, ""))) { //$NON-NLS-1$
			config.setConfigProperty(key, value);
			editor.status(key, value);
			setImage(value);
		}
	}

	public final void actionPerformed(ActionEvent evt) {
		String oldValue = (String) combo.getSelectedItem();
		String path = editor.selectFile(Messages.getString(key), oldValue);
		if (path != null) {
			path = path.replace('\\', '/');
			String test = path.toLowerCase();
			if (!test.endsWith(".gif") && !test.endsWith(".jpg") //$NON-NLS-1$ //$NON-NLS-2$
					&& !test.endsWith(".png") && !test.endsWith(".jpeg")) { //$NON-NLS-1$ //$NON-NLS-2$
				editor.info(Messages.getString(key), Messages
						.getString("unupported.image.file")); //$NON-NLS-1$
				return;
			}
			if (!path.equals(oldValue)) {
				config.setConfigProperty(key, path);
				editor.status(key, path);
				combo.setSelectedItem(path);
				setImage(path);
			}
		}
	}

	// --- SET IMAGE ---

	private final void setImage(String style) {
		InputStream in;
		try {
			if (style.indexOf('.') == -1) {
				in = GmailNotifier.class.getResourceAsStream(style + ".gif"); //$NON-NLS-1$
			} else {
				in = new FileInputStream(style);
			}
			ImageIO.setUseCache(false);
			BufferedImage image = ImageIO.read(in);
			if (image == null) {
				throw new Exception("Invalid or unsupported image (" + style //$NON-NLS-1$
						+ ")!"); //$NON-NLS-1$
			} else {
				if (image.getWidth() > 300) {
					image = image.getSubimage(0, 0, 300, image.getHeight());
				}
				if (image.getHeight() > 110) {
					image = image.getSubimage(0, 0, image.getWidth(), 110);
				}
			}
			ImageIcon icon = new ImageIcon(image);
			preview.setIcon(icon);
		} catch (Exception loadError) {
			log.error(loadError.getMessage(), loadError);
			try {
				preview.setIcon(null);
			} catch (Exception ignored) {
			}
		}
		preview.repaint(300);
	}

}
