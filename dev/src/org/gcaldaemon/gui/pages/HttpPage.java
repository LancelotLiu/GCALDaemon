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
package org.gcaldaemon.gui.pages;

import java.awt.BorderLayout;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.HashSet;

import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.core.GCalUtilities;
import org.gcaldaemon.gui.AccountDialog;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.Messages;
import org.gcaldaemon.gui.config.AccountInfo;
import org.gcaldaemon.gui.config.MainConfig;
import org.gcaldaemon.gui.editors.BooleanEditor;
import org.gcaldaemon.gui.editors.FilterEditor;
import org.gcaldaemon.gui.editors.NumberEditor;

/**
 * Configurator page for HTTP-based synchronizer.
 * 
 * Created: May 25, 2007 20:00:00 PM
 * 
 * @author Andras Berkes
 */
public final class HttpPage extends AbstractPage implements ActionListener,
		ItemListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = 2322197164246259265L;

	// --- GUI ---

	private final JPanel panel;
	private final JLabel title;
	private final JPanel toolbar;

	private final JList list;

	private final JButton accountsButton;
	private final JComboBox accountCombo;
	private final JButton copyButton;

	// --- CONSTRUCTOR ---

	public HttpPage(MainConfig config, ConfigEditor editor) throws Exception {
		super(config, editor);

		// # Enable built-in HTTP server/synchronizer
		// http.enabled=true
		BooleanEditor enable = new BooleanEditor(config, editor,
				Configurator.HTTP_ENABLED, true);
		enable.setIcon("plugin"); //$NON-NLS-1$
		enable.markAsServiceEnabler();
		addEditor(enable);

		// # List of allowed hostnames
		// http.allowed.hostnames=*
		addEditor(new FilterEditor(config, editor,
				Configurator.HTTP_ALLOWED_HOSTNAMES,
				"*.mydomain.com, localhost, userpc.domain.*")); //$NON-NLS-1$

		// # List of allowed IP addresses
		// http.allowed.addresses=*
		addEditor(new FilterEditor(config, editor,
				Configurator.HTTP_ALLOWED_ADDRESSES,
				"*.23.45.5, 127.0.0.1, 211.32.*")); //$NON-NLS-1$

		// # Port of the HTTP server (default is '9090')
		// http.port=9090
		addEditor(new NumberEditor(config, editor, Configurator.HTTP_PORT, 9090));
		editorPanel.add(Box.createRigidArea(BIG_SPACE));

		// --- BUILD URL LIST ---

		BorderLayout panelLayout = new BorderLayout();
		panelLayout.setVgap(5);
		panel = new JPanel(panelLayout);
		list = new JList();
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JScrollPane scroll = new JScrollPane(list);
		panel.add(scroll, BorderLayout.CENTER);
		add(panel, BorderLayout.CENTER);
		title = new JLabel();
		title.setText(Messages.getString("google.calendars") + ':'); //$NON-NLS-1$
		title.setIcon(editor.getIcon("gcal")); //$NON-NLS-1$
		panel.add(title, BorderLayout.NORTH);

		BorderLayout toolbarLayout = new BorderLayout(5, 0);
		toolbar = new JPanel(toolbarLayout);
		panel.add(toolbar, BorderLayout.SOUTH);

		// Accounts button
		accountsButton = new JButton();
		accountsButton.setText(Messages.getString("google.account") + ':'); //$NON-NLS-1$
		accountsButton.setIcon(editor.getIcon("account")); //$NON-NLS-1$
		accountsButton.addActionListener(this);
		toolbar.add(accountsButton, BorderLayout.WEST);

		// Combo
		accountCombo = new JComboBox();
		toolbar.add(accountCombo, BorderLayout.CENTER);

		// Copy button
		copyButton = new JButton();
		copyButton.setText(Messages.getString("copy")); //$NON-NLS-1$
		copyButton.setIcon(editor.getIcon("copy")); //$NON-NLS-1$
		copyButton.addActionListener(this);
		toolbar.add(copyButton, BorderLayout.EAST);

		fillAccounts();
	}

	// --- FILL ACCOUNT LIST ---

	private final void fillAccounts() {
		accountCombo.removeItemListener(this);
		AccountInfo[] accounts = config.getAccounts();
		accountCombo.removeAllItems();
		accountCombo.addItem(""); //$NON-NLS-1$
		for (int i = 0; i < accounts.length; i++) {
			accountCombo.addItem(accounts[i].username);
		}
		accountCombo.setSelectedIndex(0);
		list.setModel(new DefaultListModel());
		copyButton.setEnabled(false);
		accountCombo.addItemListener(this);
	}

	// --- ACTION HANDLER ---

	public final void actionPerformed(ActionEvent evt) {
		Object source = evt.getSource();
		if (source instanceof JButton) {
			JButton button = (JButton) source;
			editor.status(button.getText());
		}
		if (source == accountsButton) {
			manageAccounts();
			return;
		}
		if (source == copyButton) {
			try {
				String content = (String) list.getSelectedValue();
				if (content == null || content.length() == 0) {
					ConfigEditor.TOOLKIT.beep();
					return;
				}
				int i = content.lastIndexOf(' ');
				if (i != -1) {
					content = content.substring(i).trim();
				}
				editor.status(copyButton.getText() + " [" + content + ']'); //$NON-NLS-1$
				Clipboard clipboard = ConfigEditor.TOOLKIT.getSystemClipboard();
				StringSelection selection = new StringSelection(content);
				clipboard.setContents(selection, null);
			} catch (Exception ioException) {
				editor.status(ioException.getMessage());
			}
			return;
		}
	}

	// --- ACTIONS ---

	private final void manageAccounts() {
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

	// --- ACCOUNT LIST CHANGED ---

	public final void updateAccountEditors() {
		fillAccounts();
	}

	// --- ACCOUNT SELECTED ---

	private final HashSet listedAccounts = new HashSet();

	public final void itemStateChanged(ItemEvent evt) {
		if (evt.getStateChange() != ItemEvent.SELECTED) {
			return;
		}
		try {
			String email = (String) accountCombo.getSelectedItem();
			if (email == null || email.length() == 0) {
				list.setModel(new DefaultListModel());
				title.setText(Messages.getString("google.calendars") + ':'); //$NON-NLS-1$
				copyButton.setEnabled(false);
				return;
			}
			AccountInfo[] accounts = config.getAccounts();
			AccountInfo account = null;
			int i;
			for (i = 0; i < accounts.length; i++) {
				AccountInfo test = accounts[i];
				if (email.equals(test.username)) {
					account = test;
					break;
				}
			}
			if (account == null) {
				title.setText(Messages.getString("google.calendars") + ':'); //$NON-NLS-1$
				copyButton.setEnabled(false);
				list.setModel(new DefaultListModel());
				return;
			}
			title
					.setText(Messages.getString("google.calendars") + " [" + email + "]:"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			boolean loadFromGoogle = !listedAccounts.contains(email);
			String[] urls = config.getCalendarURLs(account, loadFromGoogle);
			listedAccounts.add(email);
			String url, name;
			DefaultListModel model = new DefaultListModel();
			long port = config.getConfigProperty(Configurator.HTTP_PORT, 9090);
			File workDir = config.getWorkDirectory();
			for (i = 0; i < urls.length; i++) {
				url = urls[i];
				name = GCalUtilities.getCalendarName(url, workDir);
				url = "http://localhost:" + port + url; //$NON-NLS-1$
				if (name == null) {
					model.addElement(url);
				} else {
					model.addElement(name + " - " + url); //$NON-NLS-1$
				}
			}
			list.setModel(model);
			if (model.getSize() > 0) {
				list.setSelectedIndex(0);
				copyButton.setEnabled(true);
			} else {
				copyButton.setEnabled(false);
			}
		} catch (Exception anyException) {
			editor.status(anyException.getMessage());
		}
	}

}
