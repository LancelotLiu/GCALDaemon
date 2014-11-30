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
package org.gcaldaemon.gui;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.gcaldaemon.gui.config.AccountInfo;
import org.gcaldaemon.gui.config.MainConfig;

public final class AccountDialog extends JDialog implements WindowListener,
		ActionListener, ListSelectionListener, MouseListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = 2561544685604071540L;

	// --- VARIABLES ---

	private final ConfigEditor editor;
	private final String key;

	// --- COMPONENTS ---

	private final JPanel root = new JPanel(null);
	private final JList list = new JList();
	private final JPanel accountPanel = new JPanel();
	private final JLabel userLabel = new JLabel();
	private final JLabel passLabel1 = new JLabel();
	private final JLabel passLabel2 = new JLabel();
	private final JTextField userField = new JTextField();
	private final JPasswordField passField1 = new JPasswordField();
	private final JPasswordField passField2 = new JPasswordField();
	private final JButton newButton = new JButton();
	private final JButton editButton = new JButton();
	private final JButton deleteButton = new JButton();
	private final JButton verifyButton = new JButton();
	private final JButton okButton = new JButton();

	// --- CONFIG CONTAINER ---

	private final MainConfig config;

	// --- CONSTRUCTOR ---

	public AccountDialog(ConfigEditor editor, MainConfig config, String key) {
		super(editor, Messages.getString("google.accounts"), true); //$NON-NLS-1$
		this.editor = editor;
		this.config = config;
		this.key = key;
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setSize(500, 360);
		Container content = getContentPane();
		content.setLayout(new BorderLayout(0, 0));
		content.add(root, BorderLayout.CENTER);
		root.setSize(495, 330);
		root.setPreferredSize(new Dimension(495, 330));

		// Build GUI
		JScrollPane scroll = new JScrollPane(list);
		scroll.setBounds(10, 10, 300, 150);
		root.add(scroll);
		accountPanel.setLayout(null);
		accountPanel.setBorder(BorderFactory.createTitledBorder(Messages
				.getString("account.properties"))); //$NON-NLS-1$
		accountPanel.setBounds(10, 170, 300, 150);
		root.add(accountPanel);

		newButton.setText(Messages.getString("new.account")); //$NON-NLS-1$
		newButton.setBounds(320, 10, 165, 25);
		root.add(newButton);

		editButton.setText(Messages.getString("edit")); //$NON-NLS-1$
		editButton.setBounds(320, 45, 165, 25);
		root.add(editButton);

		deleteButton.setText(Messages.getString("delete")); //$NON-NLS-1$
		deleteButton.setBounds(320, 80, 165, 25);
		root.add(deleteButton);

		verifyButton.setEnabled(false);
		verifyButton.setText(Messages.getString("verify")); //$NON-NLS-1$
		verifyButton.setBounds(320, 259, 165, 25);
		root.add(verifyButton);

		okButton.setText(Messages.getString("ok")); //$NON-NLS-1$
		okButton.setBounds(320, 294, 165, 25);
		root.add(okButton);

		userLabel.setText(Messages.getString("gmail.address") + ':'); //$NON-NLS-1$
		userLabel.setBounds(20, 30, 110, 25);
		accountPanel.add(userLabel);

		userField.setEnabled(false);
		userField.setBounds(130, 30, 150, 25);
		accountPanel.add(userField);

		passLabel1.setText(Messages.getString("password") + "[1]:"); //$NON-NLS-1$ //$NON-NLS-2$
		passLabel1.setBounds(20, 65, 110, 25);
		accountPanel.add(passLabel1);

		passField1.setEnabled(false);
		passField1.setBounds(130, 65, 150, 25);
		accountPanel.add(passField1);

		passLabel2.setText(Messages.getString("password") + "[2]:"); //$NON-NLS-1$ //$NON-NLS-2$
		passLabel2.setBounds(20, 100, 110, 25);
		accountPanel.add(passLabel2);

		passField2.setEnabled(false);
		passField2.setBounds(130, 100, 150, 25);
		accountPanel.add(passField2);

		// Action listeners
		addWindowListener(this);
		newButton.addActionListener(this);
		editButton.addActionListener(this);
		deleteButton.addActionListener(this);
		verifyButton.addActionListener(this);
		okButton.addActionListener(this);
		list.addListSelectionListener(this);
		list.addMouseListener(this);

		// Exit on escape
		KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
		root.registerKeyboardAction(this, stroke,
				JComponent.WHEN_IN_FOCUSED_WINDOW);

		// Fill list
		fillAccountList();

		// Show dialog
		setResizable(false);
		pack();
		Dimension size = ConfigEditor.TOOLKIT.getScreenSize();
		Dimension win = getSize();
		setLocation((size.width - win.width) / 2,
				(size.height - win.height) / 2);
		setVisible(true);
	}

	// --- DUMMY ACTION HANDLERS ---

	public final void windowOpened(WindowEvent evt) {
	}

	public final void windowIconified(WindowEvent evt) {
	}

	public final void windowDeiconified(WindowEvent evt) {
	}

	public final void windowActivated(WindowEvent evt) {
	}

	public final void windowDeactivated(WindowEvent evt) {
	}

	public final void windowClosed(WindowEvent evt) {
	}

	// --- CLOSE WINDOW ---

	public final void windowClosing(WindowEvent evt) {
		accounts = null;
		dispose();
	}

	// --- ACTION HANDLER ---

	public final void actionPerformed(ActionEvent evt) {
		Object source = evt.getSource();
		if (newButton == source) {
			oldUsername = null;
			userField.setEnabled(true);
			passField1.setEnabled(true);
			passField2.setEnabled(true);
			newButton.setEnabled(false);
			editButton.setEnabled(false);
			deleteButton.setEnabled(true);
			verifyButton.setEnabled(true);
			okButton.setEnabled(true);
			userField.setText("user@gmail.com"); //$NON-NLS-1$
			passField1.setText(""); //$NON-NLS-1$
			passField2.setText(""); //$NON-NLS-1$
			userField.select(0, 4);
			userField.requestFocus();
			return;
		}
		if (editButton == source) {
			editAccount();
			return;
		}
		if (deleteButton == source) {
			oldUsername = null;
			String username = userField.getText();
			if (username == null || username.length() == 0) {
				int index = list.getSelectedIndex();
				if (index == -1 || accounts.length < index - 1
						|| accounts.length < 2) {
					return;
				}
				if (!editor
						.question(
								Messages.getString("delete"), Messages.getString("are.you.sure"))) { //$NON-NLS-1$ //$NON-NLS-2$
					return;
				}
				AccountInfo account = accounts[index];
				removeAccount(account);
				list.setSelectedIndex(0);
				return;
			}
			if (!username.startsWith("user@") //$NON-NLS-1$
					&& !username.startsWith("example@") //$NON-NLS-1$
					&& !editor
							.question(
									Messages.getString("delete") + " [" + username + ']', Messages.getString("are.you.sure"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				return;
			}
			AccountInfo account = new AccountInfo();
			account.username = username;
			account.password = new String(passField1.getPassword());
			removeAccount(account);
			return;
		}
		if (okButton == source) {
			if (!validateFields()) {
				return;
			}
			String username = userField.getText();
			if (username.endsWith("@googlemail.com")) { //$NON-NLS-1$
				username = username.substring(0, username.indexOf('@'))
						+ "@gmail.com"; //$NON-NLS-1$
			}
			AccountInfo account = new AccountInfo();
			account.username = username;
			account.password = new String(passField1.getPassword());
			setAccount(account);
			return;
		}
		if (verifyButton == source) {
			if (!validateFields()) {
				return;
			}
			verifyAccount();
			return;
		}
		if (source == root) {
			accounts = null;
			dispose();
		}
	}

	private final boolean validateFields() {
		String username = userField.getText();
		if (username.length() == 0) {
			dispose();
			return false;
		}
		if (username.length() == 0 || username.indexOf('@') == -1) {
			ConfigEditor.TOOLKIT.beep();
			userField.requestFocus();
			return false;
		}
		String password1 = new String(passField1.getPassword());
		if (password1.length() == 0) {
			ConfigEditor.TOOLKIT.beep();
			passField1.requestFocus();
			return false;
		}
		String password2 = new String(passField2.getPassword());
		if (password2.length() == 0 || !password1.equals(password2)) {
			ConfigEditor.TOOLKIT.beep();
			passField2.selectAll();
			passField2.requestFocus();
			return false;
		}
		return true;
	}

	// --- VERIFY USERNAME AND PASSWORD ---

	private final void verifyAccount() {
		try {

			// Get user's calendars
			AccountInfo account = new AccountInfo();
			account.username = userField.getText();
			account.password = new String(passField1.getPassword());
			config.getCalendarURLs(account, true);
			editor.info(Messages.getString("verify"), //$NON-NLS-1$
					Messages.getString("account.verified")); //$NON-NLS-1$
		} catch (Exception accountException) {
			editor.error(Messages.getString("verify"), //$NON-NLS-1$
					Messages.getString("account.unverified") + " (" //$NON-NLS-1$ //$NON-NLS-2$
							+ accountException.getMessage() + ")!", //$NON-NLS-1$
					accountException);
		}
	}

	// --- ACCOUNT SELECTED ---

	public final void valueChanged(ListSelectionEvent evt) {
		oldUsername = null;
		userField.setEnabled(false);
		passField1.setEnabled(false);
		passField2.setEnabled(false);
		userField.setText(""); //$NON-NLS-1$
		passField1.setText(""); //$NON-NLS-1$
		passField2.setText(""); //$NON-NLS-1$
		verifyButton.setEnabled(false);
		newButton.setEnabled(true);
		editButton.setEnabled(true);
	}

	// --- GET SELECTED ACCOUNT ---

	public AccountInfo getSelectedAccount() {
		int index = list.getSelectedIndex();
		if (accounts == null || index == -1 || accounts.length < index - 1) {
			return null;
		}
		return accounts[index];
	}

	// --- REMOVE ACCOUNT ---

	private final void removeAccount(AccountInfo account) {
		oldUsername = null;
		userField.setEnabled(false);
		passField1.setEnabled(false);
		passField2.setEnabled(false);
		userField.setText(""); //$NON-NLS-1$
		passField1.setText(""); //$NON-NLS-1$
		passField2.setText(""); //$NON-NLS-1$
		verifyButton.setEnabled(false);
		config.removeAccount(account);
		fillAccountList();
	}

	// --- ADD / MODIFY ACCOUNT ---

	private String oldUsername;

	private final void setAccount(AccountInfo account) {
		if (oldUsername == null) {
			AccountInfo test;
			for (int i = 0; i < accounts.length; i++) {
				test = accounts[i];
				if (account.username.equals(test.username)) {
					test.password = account.password;
					list.setSelectedIndex(i);
					return;
				}
			}
			AccountInfo[] swap = new AccountInfo[accounts.length + 1];
			System.arraycopy(accounts, 0, swap, 0, accounts.length);
			swap[swap.length - 1] = account;
			accounts = swap;
			DefaultListModel model = (DefaultListModel) list.getModel();
			model.addElement(account.username);
			list.setSelectedIndex(accounts.length - 1);
		} else {
			config.setAccount(account, oldUsername);
		}
		dispose();
	}

	// --- ACCOUNT LIST ---

	private AccountInfo[] accounts = new AccountInfo[0];

	private final void fillAccountList() {
		accounts = config.getAccounts();
		DefaultListModel model = new DefaultListModel();
		int i;
		for (i = 0; i < accounts.length; i++) {
			model.addElement(accounts[i].username);
		}
		list.setModel(model);
		deleteButton.setEnabled(accounts.length > 1);
		editButton.setEnabled(accounts.length > 0);
		if (accounts.length > 0 && key != null) {
			String username = config.getConfigProperty(key,
					accounts[0].username);
			for (i = 0; i < accounts.length; i++) {
				if (username.equals(accounts[i].username)) {
					list.setSelectedIndex(i);
					return;
				}
			}
		}
	}

	// --- EDIT ACCOUNT ---

	private final void editAccount() {
		int index = list.getSelectedIndex();
		if (index == -1 || accounts.length < index - 1) {
			return;
		}
		AccountInfo account = accounts[index];
		userField.setText(account.username);
		oldUsername = account.username;
		String pass = ""; //$NON-NLS-1$
		if (account.password != null) {
			pass = account.password;
		}
		passField1.setText(pass);
		passField2.setText(pass);
		userField.setEnabled(true);
		passField1.setEnabled(true);
		passField2.setEnabled(true);
		newButton.setEnabled(true);
		editButton.setEnabled(true);
		deleteButton.setEnabled(accounts.length > 1);
		verifyButton.setEnabled(true);
		okButton.setEnabled(true);
	}

	// --- LIST ITEM SELECTED ---

	public final void mouseClicked(MouseEvent evt) {
		if (evt.getClickCount() > 1) {
			editAccount();
		}
	}

	public final void mousePressed(MouseEvent evt) {
	}

	public final void mouseReleased(MouseEvent evt) {
	}

	public final void mouseEntered(MouseEvent evt) {
	}

	public final void mouseExited(MouseEvent evt) {
	}

}
