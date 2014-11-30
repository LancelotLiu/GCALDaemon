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
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.Messages;
import org.gcaldaemon.gui.config.MainConfig;

/**
 * File or directory property editor.
 * 
 * Created: May 25, 2007 20:00:00 PM
 * 
 * @author Andras Berkes
 */
public final class FileEditor extends AbstractEditor implements ActionListener,
		KeyListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = 7628039635487698507L;

	// --- GUI ---

	private final JLabel label = new JLabel();
	private final JTextField field = new JTextField();
	private final JButton button = new JButton();

	// --- PROPERTIES ---

	private final boolean fileSelectionMode;

	// --- CONSTRUCTOR ---

	public FileEditor(MainConfig config, ConfigEditor editor, String key,
			String defaultPath, boolean fileSelectionMode) throws Exception {
		super(config, editor, key);
		this.fileSelectionMode = fileSelectionMode;
		BorderLayout borderLayout = new BorderLayout();
		borderLayout.setHgap(5);
		setLayout(borderLayout);
		add(label, BorderLayout.WEST);
		add(field, BorderLayout.CENTER);
		add(button, BorderLayout.EAST);
		label.setText(Messages.getString(key) + ':');
		field.setText(config.getConfigProperty(key, defaultPath));
		button.addActionListener(this);
		button.setIcon(editor.getIcon("open")); //$NON-NLS-1$
		button.setToolTipText(Messages.getString("browse")); //$NON-NLS-1$
		field.addKeyListener(this);
		label.setPreferredSize(LABEL_SIZE);
		verifyPath();
	}

	// --- BROWSE ---

	public final void actionPerformed(ActionEvent evt) {
		String path;
		String oldValue = field.getText();
		if (fileSelectionMode) {
			path = editor.selectFile(Messages.getString(key), oldValue);
		} else {
			path = editor.selectDir(Messages.getString(key), oldValue);
		}
		if (path != null) {
			path = path.replace('\\', '/');
			if (!path.equals(oldValue)) {
				field.setText(path);
				verifyPath();
			}
		}
	}

	// --- VALUE CHANGED ---

	public final void keyReleased(KeyEvent evt) {
		String path = field.getText();
		path = path.replace('\\', '/');
		if (!path.equals(config.getConfigProperty(key, ""))) { //$NON-NLS-1$
			verifyPath();
			config.setConfigProperty(key, path);
			editor.status(key, path);
		}
	}

	public final void keyPressed(KeyEvent evt) {
	}

	public final void keyTyped(KeyEvent evt) {
	}

	// --- VERIFY PATH ---

	private final void verifyPath() {
		String path = field.getText();
		if (path.indexOf('/') == -1 && key.equals(Configurator.LOG_CONFIG)) {
			field.setForeground(Color.BLACK);
			return;
		}
		try {
			File file = new File(path);
			if (file.exists()) {
				field.setForeground(Color.BLACK);
				return;
			}
		} catch (Exception ignored) {
		}
		field.setForeground(Color.RED);
	}

}
