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
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.Messages;
import org.gcaldaemon.gui.config.MainConfig;
import org.gcaldaemon.logger.QuickWriter;

/**
 * Remote alarm type(s) property editor.
 * 
 * Created: Sep 13, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class AlarmEditor extends AbstractEditor implements ItemListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = -2107625567776290453L;

	// --- GUI ---

	private final JPanel panel = new JPanel();
	private final JLabel label = new JLabel();
	private final JCheckBox email = new JCheckBox();
	private final JCheckBox sms = new JCheckBox();
	private final JCheckBox popup = new JCheckBox();

	// --- CONSTRUCTOR ---

	public AlarmEditor(MainConfig config, ConfigEditor editor) throws Exception {
		super(config, editor, Configurator.REMOTE_ALARM_TYPES);

		BorderLayout borderLayout = new BorderLayout(0, 0);
		setLayout(borderLayout);

		FlowLayout layout = new FlowLayout(FlowLayout.LEFT);
		layout.setVgap(5);
		layout.setHgap(0);
		panel.setLayout(layout);

		email.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		sms.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		popup.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		email.setText(Messages.getString("email.message")); //$NON-NLS-1$
		sms.setText(Messages.getString("sms.notification")); //$NON-NLS-1$
		popup.setText(Messages.getString("popup.window")); //$NON-NLS-1$

		email.setIcon(editor.getIcon("mail-off")); //$NON-NLS-1$
		email.setSelectedIcon(editor.getIcon("mail-on")); //$NON-NLS-1$

		sms.setIcon(editor.getIcon("mobile-off")); //$NON-NLS-1$
		sms.setSelectedIcon(editor.getIcon("mobile-on")); //$NON-NLS-1$

		popup.setIcon(editor.getIcon("popup-off")); //$NON-NLS-1$
		popup.setSelectedIcon(editor.getIcon("popup-on")); //$NON-NLS-1$

		panel.add(email, BorderLayout.WEST);
		panel.add(sms, BorderLayout.CENTER);
		panel.add(popup, BorderLayout.EAST);

		add(label, BorderLayout.WEST);
		add(panel, BorderLayout.CENTER);

		label.setText(Messages.getString(key) + ':');
		label.setPreferredSize(LABEL_SIZE);

		String alarms = config.getConfigProperty(key, "email,sms,popup");
		alarms = alarms.toLowerCase();
		email.setSelected(alarms.indexOf("mail") != -1);
		sms.setSelected(alarms.indexOf("sms") != -1);
		popup.setSelected(alarms.indexOf("pop") != -1);
		if (!email.isSelected() && !sms.isSelected() && !popup.isSelected()) {
			email.setSelected(true);
			sms.setSelected(true);
			popup.setSelected(true);
		}
		if (email.isSelected()) {
			email.setForeground(Color.BLACK);
		} else {
			email.setForeground(Color.GRAY);
		}
		if (sms.isSelected()) {
			sms.setForeground(Color.BLACK);
		} else {
			sms.setForeground(Color.GRAY);
		}
		if (popup.isSelected()) {
			popup.setForeground(Color.BLACK);
		} else {
			popup.setForeground(Color.GRAY);
		}
		email.addItemListener(this);
		sms.addItemListener(this);
		popup.addItemListener(this);
	}

	// --- VALUE CHANGED ---

	public final void itemStateChanged(ItemEvent evt) {
		boolean enableEmail = email.isSelected();
		boolean enableSms = sms.isSelected();
		boolean enablePopup = popup.isSelected();
		if (!enableEmail && !enableSms && !enablePopup) {
			if (evt.getSource() == email) {
				sms.setSelected(true);
			} else {
				email.setSelected(true);
			}
			return;
		}
		QuickWriter alarm = new QuickWriter(20);
		if (enableEmail) {
			alarm.write("email,");
			email.setForeground(Color.BLACK);
		} else {
			email.setForeground(Color.GRAY);
		}
		if (enableSms) {
			alarm.write("sms,");
			sms.setForeground(Color.BLACK);
		} else {
			sms.setForeground(Color.GRAY);
		}
		if (enablePopup) {
			alarm.write("popup,");
			popup.setForeground(Color.BLACK);
		} else {
			popup.setForeground(Color.GRAY);
		}
		alarm.setLength(alarm.length() - 1);
		String value = alarm.toString();
		config.setConfigProperty(key, value);
		editor.status(key, value);
	}

}
