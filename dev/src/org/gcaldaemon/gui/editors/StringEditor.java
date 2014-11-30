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
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JLabel;
import javax.swing.JTextField;

import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.Messages;
import org.gcaldaemon.gui.config.MainConfig;

/**
 * String property editor.
 * 
 * Created: May 25, 2007 20:00:00 PM
 * 
 * @author Andras Berkes
 */
public final class StringEditor extends AbstractEditor implements KeyListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = -6372164282525166544L;

	// --- GUI ---

	private final JLabel label = new JLabel();
	private final JTextField field = new JTextField();

	// --- CONSTRUCTOR ---

	public StringEditor(MainConfig config, ConfigEditor editor, String key,
			String defaultValue) throws Exception {
		super(config, editor, key);
		BorderLayout borderLayout = new BorderLayout();
		borderLayout.setHgap(5);
		setLayout(borderLayout);
		add(label, BorderLayout.WEST);
		add(field, BorderLayout.CENTER);
		label.setText(Messages.getString(key) + ':');
		field.setText(config.getConfigProperty(key, defaultValue));
		field.addKeyListener(this);
		label.setPreferredSize(LABEL_SIZE);
	}

	// --- VALUE CHANGED ---

	public final void keyReleased(KeyEvent evt) {
		String path = field.getText();
		path = path.replace('\\', '/');
		if (!path.equals(config.getConfigProperty(key, ""))) {
			config.setConfigProperty(key, path);
			editor.status(key, path);
		}
	}

	public final void keyPressed(KeyEvent evt) {
	}

	public final void keyTyped(KeyEvent evt) {
	}

}
