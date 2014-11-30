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
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;

import org.gcaldaemon.gui.AccountDialog;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.Messages;
import org.gcaldaemon.gui.config.AccountInfo;
import org.gcaldaemon.gui.config.MainConfig;

public final class AccountEditor extends AbstractEditor implements
		ActionListener, ItemListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = -5077586034287594442L;

	// --- GUI ---

	private final JLabel label = new JLabel();
	private final JComboBox combo = new JComboBox();
	private final JButton button = new JButton();

	// --- PROPERTIES ---

	private final String passwordKey;

	// --- CONSTRUCTOR ---

	public AccountEditor(MainConfig config, ConfigEditor editor,
			String usernamekey, String passwordKey) throws Exception {
		super(config, editor, usernamekey);
		this.passwordKey = passwordKey;
		BorderLayout borderLayout = new BorderLayout();
		borderLayout.setHgap(5);
		setLayout(borderLayout);
		add(label, BorderLayout.WEST);
		add(combo, BorderLayout.CENTER);
		add(button, BorderLayout.EAST);
		updateAccount();
		label.setText(Messages.getString("google.account") + ':'); //$NON-NLS-1$
		button.addActionListener(this);
		button.setIcon(editor.getIcon("account")); //$NON-NLS-1$
		button.setToolTipText(Messages.getString("google.accounts")); //$NON-NLS-1$
		label.setPreferredSize(LABEL_SIZE);
	}

	public final void updateAccount() {
		combo.removeItemListener(this);
		try {
			combo.removeAllItems();
			AccountInfo[] accounts = config.getAccounts();
			for (int i = 0; i < accounts.length; i++) {
				combo.addItem(accounts[i].username);
			}
		} catch (Exception ignored) {
			log.error(ignored);
		}
		combo.setSelectedItem(config.getConfigProperty(key, "")); //$NON-NLS-1$
		combo.addItemListener(this);
	}

	public final void itemStateChanged(ItemEvent evt) {
		Object item = combo.getSelectedItem();
		Object old = evt.getItem();
		if (item != null && old != item) {
			String username = (String) item;
			AccountInfo[] accounts = config.getAccounts();
			AccountInfo account;
			for (int i = 0; i < accounts.length; i++) {
				account = accounts[i];
				if (username.equals(account.username)) {
					config.setConfigProperty(key, account.username);
					config.setPasswordProperty(passwordKey, account.password);
					editor.status(key, account.username + "+password"); //$NON-NLS-1$
					return;
				}
			}
		}
	}

	// --- MANAGE ACCOUNTS ---

	public final void actionPerformed(ActionEvent evt) {
		if (evt.getSource() == button) {
			AccountDialog dialog = new AccountDialog(editor, config, key);
			AccountInfo account = dialog.getSelectedAccount();
			if (account != null) {
				config.setConfigProperty(key, account.username);
				config.setPasswordProperty(passwordKey, account.password);
				editor.status(key, account.username + "+password"); //$NON-NLS-1$
			}
			editor.updateAccountEditors();
		}
	}

}
