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
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JLabel;
import javax.swing.JPasswordField;

import org.gcaldaemon.core.PasswordEncoder;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.Messages;
import org.gcaldaemon.gui.config.MainConfig;

/**
 * Password property editor.
 * 
 * Created: May 25, 2007 20:00:00 PM
 * 
 * @author Andras Berkes
 */
public final class PasswordEditor extends AbstractEditor implements KeyListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = 542716571632242103L;

	// --- GUI ---

	private final JLabel label = new JLabel();
	private final JPasswordField password = new JPasswordField();

	// --- CONSTRUCTOR ---

	public PasswordEditor(MainConfig config, ConfigEditor editor, String key)
			throws Exception {
		super(config, editor, key);
		BorderLayout borderLayout = new BorderLayout();
		borderLayout.setHgap(5);
		setLayout(borderLayout);
		add(label, BorderLayout.WEST);
		add(password, BorderLayout.CENTER);
		label.setText(Messages.getString(key) + ':');
		try {
			password.setText(config.getPasswordProperty(key));
		} catch (Exception ignored) {
			password.setText("XXXXX");
			password.setForeground(Color.RED);
		}
		password.addKeyListener(this);
		label.setPreferredSize(LABEL_SIZE);
	}

	// --- VALUE CHANGED ---

	public final void keyReleased(KeyEvent evt) {
		String pass = new String(password.getPassword());
		try {
			if (pass.length() > 0) {
				pass = PasswordEncoder.encodePassword(pass);
			}
			config.setConfigProperty(key, pass);
			editor.status(key, pass);
			password.setForeground(Color.BLACK);
		} catch (Exception ignored) {
			password.setForeground(Color.RED);
			return;
		}
	}

	public final void keyPressed(KeyEvent evt) {
	}

	public final void keyTyped(KeyEvent evt) {
	}

}
