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
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JComboBox;
import javax.swing.JLabel;

import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.Messages;
import org.gcaldaemon.gui.config.MainConfig;

/**
 * Combobox-based property editor.
 * 
 * Created: May 25, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class ComboEditor extends AbstractEditor implements ItemListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = -4066800583551480510L;

	// --- GUI ---

	private final JLabel label = new JLabel();
	private final JComboBox combo = new JComboBox();

	// --- CONSTRUCTOR ---

	public ComboEditor(MainConfig config, ConfigEditor editor, String key,
			String[] values, String defaultValue, boolean editable)
			throws Exception {
		super(config, editor, key);
		for (int i = 0; i < values.length; i++) {
			combo.addItem(values[i]);
		}
		combo.setEditable(editable);
		combo.setSelectedItem(config.getConfigProperty(key, defaultValue));
		BorderLayout borderLayout = new BorderLayout();
		borderLayout.setHgap(5);
		setLayout(borderLayout);
		add(label, BorderLayout.WEST);
		add(combo, BorderLayout.CENTER);
		combo.addItemListener(this);
		label.setText(Messages.getString(key) + ':');
		label.setPreferredSize(LABEL_SIZE);
	}

	// --- VALUE CHANGED ---

	public final void itemStateChanged(ItemEvent evt) {
		String value = (String) combo.getSelectedItem();
		value = value.replace('\\', '/');
		if (!value.equals(config.getConfigProperty(key, ""))) { //$NON-NLS-1$
			config.setConfigProperty(key, value);
			if (key.equals(Configurator.NOTIFIER_DATE_FORMAT)) {
				try {
					SimpleDateFormat format = new SimpleDateFormat(value);
					value = value + " >>> " + format.format(new Date()); //$NON-NLS-1$
				} catch (Exception ignored) {
					value = value + " >>> N/A"; //$NON-NLS-1$
				}
			}
			editor.status(key, value);
		}
	}

}
