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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.Messages;
import org.gcaldaemon.gui.config.MainConfig;

/**
 * Filter (host, IP-address, user, etc.) property editor.
 * 
 * Created: May 25, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class FilterEditor extends AbstractEditor implements
		ActionListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = 2352522827877955616L;

	// --- GUI ---

	private final JLabel label = new JLabel();
	private final JTextField field = new JTextField();
	private final JButton button = new JButton();

	// --- PROPERTIES ---

	private final String sample;

	// --- CONSTRUCTOR ---

	public FilterEditor(MainConfig config, ConfigEditor editor, String key,
			String sample) throws Exception {
		super(config, editor, key);
		this.sample = sample;
		BorderLayout borderLayout = new BorderLayout();
		borderLayout.setHgap(5);
		setLayout(borderLayout);
		field.setEditable(false);
		field.setText(config.getConfigProperty(key, "*")); //$NON-NLS-1$
		add(label, BorderLayout.WEST);
		add(field, BorderLayout.CENTER);
		add(button, BorderLayout.EAST);
		label.setText(Messages.getString(key) + ':');
		button.addActionListener(this);
		button.setIcon(editor.getIcon("filter"));
		button.setToolTipText(Messages.getString(key));
		label.setPreferredSize(LABEL_SIZE);
	}

	// --- MANAGE FILTERS ---

	public final void actionPerformed(ActionEvent evt) {
		if (evt.getSource() == button) {
			FilterDialog dialog = new FilterDialog(editor, config, key, sample);
			String newFilter = dialog.getFilterList();
			if (newFilter != null) {
				String oldFilter = config.getConfigProperty(key, "*"); //$NON-NLS-1$
				if (!newFilter.equals(oldFilter)) {
					config.setConfigProperty(key, newFilter);
					editor.status(key, newFilter);
					field.setText(newFilter);
				}
			}
		}
	}
}
