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
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

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
public final class NumberEditor extends AbstractEditor implements
		ChangeListener, KeyListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = -6785176521531009115L;

	// --- GUI ---

	private final JLabel label = new JLabel();
	private final JSpinner spinner = new JSpinner();

	// --- CONSTRUCTOR ---

	public NumberEditor(MainConfig config, ConfigEditor editor, String key,
			int defaultValue) throws Exception {
		super(config, editor, key);
		BorderLayout borderLayout = new BorderLayout();
		borderLayout.setHgap(5);
		setLayout(borderLayout);
		add(label, BorderLayout.WEST);
		add(spinner, BorderLayout.CENTER);
		label.setText(Messages.getString(key) + ':');
		Integer min = new Integer(1);
		Integer value = new Integer((int) config.getConfigProperty(key,
				defaultValue));
		Integer max = new Integer(16000);
		Integer step = new Integer(1);
		SpinnerNumberModel model = new SpinnerNumberModel(value, min, max, step);
		spinner.setModel(model);
		spinner.addChangeListener(this);
		spinner.addKeyListener(this);
		label.setPreferredSize(LABEL_SIZE);
	}

	// --- VALUE CHANGED ---

	public final void stateChanged(ChangeEvent evt) {
		changed();
	}

	public final void keyTyped(KeyEvent evt) {
	}

	public final void keyPressed(KeyEvent evt) {
	}

	public final void keyReleased(KeyEvent evt) {
		changed();
	}

	private final void changed() {
		Integer number = (Integer) spinner.getValue();
		config.setConfigProperty(key, number.toString());
		editor.status(key, number.toString());
	}

}
