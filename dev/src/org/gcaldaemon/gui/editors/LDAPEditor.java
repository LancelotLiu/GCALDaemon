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
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.gui.AccountDialog;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.Messages;
import org.gcaldaemon.gui.config.AccountInfo;
import org.gcaldaemon.gui.config.MainConfig;

/**
 * LDAP Account list property editory.
 * 
 * Created: Feb 3, 2008 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class LDAPEditor extends AbstractEditor implements ItemListener,
		ActionListener, ListSelectionListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = 8856275045631540247L;

	// --- CONSTANTS ---

	private static final Dimension SMALL_SPACE = new Dimension(5, 5);

	// --- GUI ---

	private final JLabel label = new JLabel();
	private final JComboBox combo = new JComboBox();

	private final JButton accountButton = new JButton();
	private final JButton addButton = new JButton();
	private final JButton removeButton = new JButton();

	private final JList list = new JList();

	// --- CONSTRUCTOR ---

	public LDAPEditor(MainConfig config, ConfigEditor editor) throws Exception {
		super(config, editor, Configurator.LDAP_GOOGLE_USERNAME);
		BorderLayout borderLayout1 = new BorderLayout();
		borderLayout1.setHgap(0);
		borderLayout1.setVgap(5);
		setLayout(borderLayout1);

		JPanel header = new JPanel();
		add(header, BorderLayout.NORTH);
		BorderLayout borderLayout2 = new BorderLayout();
		borderLayout2.setHgap(5);
		borderLayout2.setVgap(0);
		header.setLayout(borderLayout2);
		header.add(label, BorderLayout.WEST);
		header.add(combo, BorderLayout.CENTER);

		JPanel buttons = new JPanel();
		FlowLayout flowLayout = new FlowLayout(FlowLayout.RIGHT);
		flowLayout.setHgap(0);
		flowLayout.setVgap(0);
		buttons.setLayout(flowLayout);
		header.add(buttons, BorderLayout.EAST);
		buttons.add(addButton);
		buttons.add(Box.createRigidArea(SMALL_SPACE));
		buttons.add(removeButton);
		buttons.add(Box.createRigidArea(SMALL_SPACE));
		buttons.add(accountButton);

		label.setText(Messages.getString("google.accounts") + ':'); //$NON-NLS-1$
		label.setPreferredSize(LABEL_SIZE);

		addButton.setIcon(editor.getIcon("add")); //$NON-NLS-1$
		removeButton.setIcon(editor.getIcon("remove")); //$NON-NLS-1$
		accountButton.setIcon(editor.getIcon("account")); //$NON-NLS-1$		

		addButton.setToolTipText("+ " + Messages.getString("google.account")); //$NON-NLS-1$ //$NON-NLS-2$
		removeButton
				.setToolTipText("- " + Messages.getString("google.account")); //$NON-NLS-1$ //$NON-NLS-2$
		accountButton.setToolTipText(Messages.getString("google.accounts")); //$NON-NLS-1$

		JPanel listPanel = new JPanel();
		BorderLayout borderLayout3 = new BorderLayout();
		borderLayout3.setHgap(5);
		borderLayout3.setVgap(0);
		listPanel.setLayout(borderLayout3);
		add(listPanel, BorderLayout.CENTER);

		JScrollPane scroll = new JScrollPane(list);
		JLabel dummy = new JLabel();
		dummy.setPreferredSize(LABEL_SIZE);
		listPanel.add(dummy, BorderLayout.WEST);
		listPanel.add(scroll, BorderLayout.CENTER);
		scroll.setPreferredSize(new Dimension(600, 100));

		list.setModel(new DefaultListModel());
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		updateAccount();

		list.addListSelectionListener(this);
		addButton.addActionListener(this);
		removeButton.addActionListener(this);
		accountButton.addActionListener(this);
	}

	public final void updateAccount() {
		combo.removeItemListener(this);
		try {
			combo.removeAllItems();
			AccountInfo[] accounts = config.getAccounts();
			for (int i = 0; i < accounts.length; i++) {
				combo.addItem(accounts[i].username);
			}

			DefaultListModel listModel = (DefaultListModel) list.getModel();
			listModel.removeAllElements();
			AccountInfo[] array = config.getLDAPAccounts();
			for (int i = 0; i < array.length; i++) {
				listModel.addElement(array[i].username);
			}
		} catch (Exception ignored) {
			log.error(ignored);
		}
		if (combo.getItemCount() > 0) {
			combo.setSelectedIndex(0);
		}
		setButtonAccess();
		selectLists();
		combo.addItemListener(this);
	}

	// --- ACTION HANDLERS ---

	private final void setButtonAccess() {
		String selected = (String) combo.getSelectedItem();
		if (selected == null) {
			addButton.setEnabled(false);
			removeButton.setEnabled(false);
			return;
		}

		DefaultListModel listModel = (DefaultListModel) list.getModel();
		int len = listModel.getSize();
		boolean found = false;
		for (int i = 0; i < len; i++) {
			String test = (String) listModel.getElementAt(i);
			if (test != null && test.equals(selected)) {
				found = true;
				break;
			}
		}
		if (found) {
			addButton.setEnabled(false);
			if (len > 1) {
				removeButton.setEnabled(true);
			} else {
				removeButton.setEnabled(false);
			}
		} else {
			addButton.setEnabled(true);
			removeButton.setEnabled(false);
		}
	}

	private final boolean selectLists() {
		String selected = (String) combo.getSelectedItem();
		if (selected == null) {
			list.getSelectionModel().clearSelection();
			return false;
		}
		DefaultListModel listModel = (DefaultListModel) list.getModel();
		boolean found = false;
		int index = list.getSelectedIndex();
		int len = listModel.getSize();
		for (int i = 0; i < len; i++) {
			String test = (String) listModel.getElementAt(i);
			if (test != null && test.equals(selected)) {
				found = true;
				if (i != index) {
					list.setSelectedIndex(i);
				}
				break;
			}
		}
		if (!found) {
			list.getSelectionModel().clearSelection();
		}
		return found;
	}

	private final void selectCombo() {
		String selected = (String) list.getSelectedValue();
		if (selected == null) {
			return;
		}
		if (combo.getItemCount() == 0) {
			return;
		}
		String test = (String) combo.getSelectedItem();
		if (test == null || !selected.equals(test)) {
			combo.setSelectedItem(selected);
		}
	}

	public final void itemStateChanged(ItemEvent e) {

		// Combo changed
		setButtonAccess();
		selectLists();
	}

	public final void valueChanged(ListSelectionEvent e) {

		// List changed
		setButtonAccess();
		selectCombo();
	}

	public final void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		DefaultListModel listModel = (DefaultListModel) list.getModel();
		String selected = (String) combo.getSelectedItem();
		if (source == addButton) {

			// Add item
			if (selected == null) {
				return;
			}
			listModel.addElement(selected);
			AccountInfo info = getAccountInfo(selected);
			if (info != null) {
				config.setLDAPAccount(info);
				config.markAsChanged();
				int idx = listModel.getSize();
				editor.status(key, info.username + "+password #" + idx); //$NON-NLS-1$
			}
			setButtonAccess();
			selectLists();
			return;
		}
		if (source == removeButton) {

			// Remove item
			if (selected == null) {
				return;
			}
			int idx = listModel.indexOf(selected) + 1;
			if (listModel.removeElement(selected)) {
				AccountInfo info = getAccountInfo(selected);
				if (info != null) {
					config.removeLDAPAccount(info);
					config.markAsChanged();
					editor.status(key, Messages.getString("remove") + " #" //$NON-NLS-1$ //$NON-NLS-2$
							+ idx);
				}
			}
			setButtonAccess();
			if (listModel.getSize() > 0) {
				list.setSelectedIndex(0);
			}
			return;
		}

		// Edit accounts
		AccountDialog dialog = new AccountDialog(editor, config, null);
		AccountInfo info = dialog.getSelectedAccount();
		if (info != null) {
			AccountInfo[] infos = config.getAccounts();
			boolean found = false;
			for (int i = 0; i < infos.length; i++) {
				if (info.username.equals(infos[i].username)) {
					found = true;
					break;
				}
			}
			if (!found) {
				config.setAccount(info, info.username);
			}
		}
		editor.updateAccountEditors();
	}

	private final AccountInfo getAccountInfo(String username) {
		if (username == null) {
			return null;
		}
		AccountInfo[] accounts = config.getAccounts();
		AccountInfo account;
		for (int i = 0; i < accounts.length; i++) {
			account = accounts[i];
			if (username.equals(account.username)) {
				AccountInfo copy = new AccountInfo();
				copy.username = username;
				copy.password = account.password;
				return copy;
			}
		}
		return null;
	}

}
