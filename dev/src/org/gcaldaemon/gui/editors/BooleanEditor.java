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

import javax.swing.JCheckBox;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.Messages;
import org.gcaldaemon.gui.config.MainConfig;
import org.gcaldaemon.gui.pages.AbstractPage;

/**
 * Boolean property editor.
 * 
 * Created: May 25, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class BooleanEditor extends AbstractEditor implements
		ChangeListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = -8256147144473140969L;

	// --- GUI ---

	private final JCheckBox check = new JCheckBox();
	private final AbstractPage page;

	// --- CONSTRUCTORS ---

	public BooleanEditor(MainConfig config, ConfigEditor editor, String key,
			boolean defaultValue) throws Exception {
		this(null, config, editor, key, defaultValue);
	}

	public BooleanEditor(AbstractPage page, MainConfig config,
			ConfigEditor editor, String key, boolean defaultValue)
			throws Exception {
		super(config, editor, key);
		this.page = page;
		setLayout(new BorderLayout());
		add(check, BorderLayout.CENTER);
		check.setText(Messages.getString(key));
		check.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		boolean value = config.getConfigProperty(key, defaultValue);
		check.setSelected(value);
		if (value) {
			check.setForeground(Color.BLACK);
		} else {
			check.setForeground(Color.GRAY);
		}
		if (page != null) {
			page.setChecked(value);
		}
		check.addChangeListener(this);
	}

	public final void setIcon(String name) {
		check.setIcon(editor.getIcon(name + "-off")); //$NON-NLS-1$
		check.setSelectedIcon(editor.getIcon(name + "-on")); //$NON-NLS-1$
	}

	// --- SET TAB FOREGROUND ---

	private boolean serviceEnabler;

	public final void markAsServiceEnabler() {
		editor.setServiceEnabled(this, check.isSelected());
		serviceEnabler = true;
	}

	// --- VALUE CHANGED ---

	public final void stateChanged(ChangeEvent evt) {
		boolean value = check.isSelected();
		if (value != config.getConfigProperty(key, true)) {
			config.setConfigProperty(key, value);
			editor.status(key, value);
			if (serviceEnabler) {
				editor.setServiceEnabled(this, value);
			}
			if (value) {
				check.setForeground(Color.BLACK);
			} else {
				check.setForeground(Color.GRAY);
			}
			if (page != null) {
				page.setChecked(check.isSelected());
			}
		}
	}

}
