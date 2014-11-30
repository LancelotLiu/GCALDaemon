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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import org.gcaldaemon.core.GCalUtilities;
import org.gcaldaemon.gui.AccountDialog;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.Messages;
import org.gcaldaemon.gui.config.AccountInfo;
import org.gcaldaemon.gui.config.FileSync;
import org.gcaldaemon.gui.config.MainConfig;

public final class GCalDialog extends JDialog implements WindowListener,
		ActionListener, ItemListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = 67816588333571472L;

	// --- PROPERTIES ---

	private final ConfigEditor editor;
	private final MainConfig config;

	private FileSync fileSync;

	// --- GUI COMPONENTS ---

	private final JPanel root = new JPanel(null);
	private final JLabel accountLabel = new JLabel();
	private final JLabel gcalLabel = new JLabel();
	private final JLabel icalLabel = new JLabel();
	private final JLabel icalUrlLabel = new JLabel();

	private final JComboBox accountCombo = new JComboBox();
	private final JComboBox gcalCombo = new JComboBox();
	private final JComboBox icalCombo = new JComboBox();

	private final JTextField icalUrlField = new JTextField();

	private final JSeparator separator = new JSeparator(JSeparator.HORIZONTAL);

	private final JButton accountButton = new JButton();
	private final JButton refreshButton = new JButton();
	private final JButton browseButton = new JButton();
	private final JButton copyButton = new JButton();
	private final JButton okButton = new JButton();
	private final JButton cancelButton = new JButton();

	// --- CONSTRUCTOR ---

	public GCalDialog(ConfigEditor editor, MainConfig config, FileSync fileSync) {
		super(editor, true);
		this.editor = editor;
		this.config = config;
		this.fileSync = fileSync;
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setSize(575, 260);
		Container content = getContentPane();
		content.setLayout(new BorderLayout(0, 0));
		content.add(root, BorderLayout.CENTER);
		root.setSize(570, 230);
		root.setPreferredSize(new Dimension(570, 230));

		// Build GUI
		accountLabel.setBounds(10, 15, 160, 25);
		accountLabel.setText(Messages.getString("google.account") + ':'); //$NON-NLS-1$
		root.add(accountLabel);

		gcalLabel.setBounds(10, 50, 160, 25);
		gcalLabel.setText(Messages.getString("google.calendar") + ':'); //$NON-NLS-1$
		root.add(gcalLabel);

		icalLabel.setBounds(10, 85, 160, 25);
		icalLabel.setText(Messages.getString("ical.file") + ':'); //$NON-NLS-1$
		root.add(icalLabel);

		icalUrlLabel.setBounds(10, 120, 160, 25);
		icalUrlLabel.setText(Messages.getString("ical.file.url") + ':'); //$NON-NLS-1$
		root.add(icalUrlLabel);

		accountCombo.setBounds(175, 15, 200, 25);
		root.add(accountCombo);
		fillAccounts();

		gcalCombo.setBounds(175, 50, 200, 25);
		gcalCombo.setEditable(true);
		root.add(gcalCombo);
		fillCalendars(false);

		icalCombo.setBounds(175, 85, 200, 25);
		icalCombo.setEditable(true);
		root.add(icalCombo);
		fillPaths();

		icalUrlField.setBounds(175, 120, 200, 25);
		icalUrlField.setEditable(false);
		root.add(icalUrlField);
		setIcalFileURL();

		separator.setBounds(10, 160, 545, 20);
		root.add(separator);

		accountButton.setBounds(385, 15, 170, 25);
		accountButton.setText(Messages.getString("google.accounts")); //$NON-NLS-1$
		root.add(accountButton);

		refreshButton.setBounds(385, 50, 170, 25);
		refreshButton.setText(Messages.getString("refresh")); //$NON-NLS-1$
		root.add(refreshButton);

		browseButton.setBounds(385, 85, 170, 25);
		browseButton.setText(Messages.getString("browse")); //$NON-NLS-1$
		root.add(browseButton);

		copyButton.setBounds(385, 120, 170, 25);
		copyButton.setText(Messages.getString("copy")); //$NON-NLS-1$
		root.add(copyButton);

		okButton.setBounds(355, 190, 95, 25);
		okButton.setText(Messages.getString("ok")); //$NON-NLS-1$
		root.add(okButton);

		cancelButton.setBounds(460, 190, 95, 25);
		cancelButton.setText(Messages.getString("cancel")); //$NON-NLS-1$
		root.add(cancelButton);

		// Action listeners
		addWindowListener(this);
		gcalCombo.addItemListener(this);
		icalCombo.addItemListener(this);
		accountButton.addActionListener(this);
		refreshButton.addActionListener(this);
		browseButton.addActionListener(this);
		copyButton.addActionListener(this);
		okButton.addActionListener(this);
		cancelButton.addActionListener(this);

		// Exit on escape
		KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
		root.registerKeyboardAction(this, stroke,
				JComponent.WHEN_IN_FOCUSED_WINDOW);

		// Show dialog
		setTitle();
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
		fileSync = null;
		dispose();
	}

	public final FileSync getFileSync() {
		return fileSync;
	}

	// --- ACTION HANDLERS ---

	public final void actionPerformed(ActionEvent evt) {
		Object source = evt.getSource();
		if (accountButton == source) {
			manageAccounts();
			return;
		}
		if (refreshButton == source) {
			fillCalendars(true);
			return;
		}
		if (browseButton == source) {
			String path = (String) icalCombo.getSelectedItem();
			if (path == null || path.length() == 0) {
				path = System.getProperty("user.home", "/"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			path = editor.selectFile(Messages.getString("ical.file"), path); //$NON-NLS-1$
			if (path != null) {
				if (path.endsWith(".ics")) { //$NON-NLS-1$
					MainConfig.selectItem(icalCombo, path);
				} else {
					ConfigEditor.TOOLKIT.beep();
				}
			}
			return;
		}
		if (copyButton == source) {
			try {
				String content = icalUrlField.getText().trim();
				Clipboard clipboard = ConfigEditor.TOOLKIT.getSystemClipboard();
				StringSelection selection = new StringSelection(content);
				clipboard.setContents(selection, null);
			} catch (Exception ioException) {
				editor.status(ioException.getMessage());
			}
			return;
		}
		if (okButton == source) {
			String url = (String) gcalCombo.getSelectedItem();
			String path = (String) icalCombo.getSelectedItem();
			if (url.length() == 0 || path.length() == 0) {
				ConfigEditor.TOOLKIT.beep();
				fileSync = null;
				dispose();
				return;
			}
			int i = url.lastIndexOf(' ');
			if (i != -1) {
				url = url.substring(i + 1);
			}
			i = path.indexOf(" - "); //$NON-NLS-1$
			if (i != -1) {
				path = path.substring(i + 3);
			}
			fileSync = new FileSync();
			fileSync.privateIcalUrl = url.trim();
			fileSync.icalPath = path.replace('\\', '/').trim();
			if (fileSync.privateIcalUrl.length() == 0
					|| fileSync.icalPath.length() == 0) {
				ConfigEditor.TOOLKIT.beep();
				fileSync = null;
				dispose();
				return;
			}
			AccountInfo[] accounts = config.getAccounts();
			String email = (String) accountCombo.getSelectedItem();
			if (email == null || email.length() == 0) {
				ConfigEditor.TOOLKIT.beep();
				fileSync = null;
				dispose();
				return;
			}
			AccountInfo account;
			for (i = 0; i < accounts.length; i++) {
				account = accounts[i];
				if (email.equals(account.username)) {
					fileSync.username = account.username;
					fileSync.password = account.password;
					break;
				}
			}
			if (fileSync.username == null) {
				ConfigEditor.TOOLKIT.beep();
				fileSync = null;
			}
			dispose();
			return;
		}
		if (cancelButton == source) {
			fileSync = null;
			dispose();
			return;
		}
		if (source == root) {
			fileSync = null;
			dispose();
		}
	}

	// --- ACCOUNT MANAGER ---

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
			fillAccounts();
			accountCombo.setSelectedItem(info.username);
		}
		editor.updateAccountEditors();
	}

	// --- COMBO ITEM SELECTED ---

	public final void itemStateChanged(ItemEvent evt) {
		Object source = evt.getSource();
		if (accountCombo == source) {
			fillCalendars(false);
			return;
		}
		if (gcalCombo == source) {
			gcalCombo.setToolTipText((String) gcalCombo.getSelectedItem());
			setTitle();
			return;
		}
		if (icalCombo == source) {
			setIcalFileURL();
			setTitle();
			return;
		}
	}

	private final void setIcalFileURL() {
		try {
			String path = (String) icalCombo.getSelectedItem();
			icalCombo.setToolTipText(path);
			int i = path.indexOf(" - "); //$NON-NLS-1$
			if (i != -1) {
				path = path.substring(i + 3);
			}
			i = path.indexOf(':');
			if (i > 1) {
				path = path.substring(i - 1);
			}
			String url = ""; //$NON-NLS-1$
			if (path != null) {
				path = path.trim();
				if (path.length() != 0) {
					while (path.startsWith("/")) { //$NON-NLS-1$
						path = path.substring(1);
					}
					if (path.indexOf("*.ics") == -1) { //$NON-NLS-1$
						url = "file:///" + path.replace('\\', '/'); //$NON-NLS-1$
						url = url.replaceAll(" ", "%20"); //$NON-NLS-1$ //$NON-NLS-2$
					} else {
						url = ""; //$NON-NLS-1$
					}
				}
			}
			copyButton.setEnabled(url.length() != 0);
			icalUrlField.setText(url);
			icalUrlField.select(0, 0);
		} catch (Exception ignored) {
		}
	}

	private final void setTitle() {
		String title = Messages.getString("google.sync"); //$NON-NLS-1$
		String gcal = (String) gcalCombo.getSelectedItem();
		if (gcal != null) {
			int i = gcal.indexOf(" - "); //$NON-NLS-1$
			if (i != -1) {
				String ical = (String) icalCombo.getSelectedItem();
				if (ical != null) {
					int j = ical.indexOf(" - "); //$NON-NLS-1$
					if (j != -1) {
						title = title + " [" + gcal.substring(0, i) + " - " //$NON-NLS-1$ //$NON-NLS-2$
								+ ical.substring(0, j) + ']';
					}
				}
			}
		}
		setTitle(title);
	}

	// --- FILL ACCOUNT LIST ---

	private final void fillAccounts() {
		accountCombo.removeItemListener(this);
		AccountInfo[] accounts = config.getAccounts();
		accountCombo.removeAllItems();
		for (int i = 0; i < accounts.length; i++) {
			accountCombo.addItem(accounts[i].username);
		}
		if (fileSync != null && fileSync.username != null) {
			accountCombo.setSelectedItem(fileSync.username);
		}
		int index = accountCombo.getSelectedIndex();
		if (index == -1 && accountCombo.getItemCount() != 0) {
			accountCombo.setSelectedIndex(0);
		}
		accountCombo.addItemListener(this);
	}

	// --- FILL CALENDAR LIST ---

	private final void fillCalendars(boolean loadFromGoogle) {
		gcalCombo.removeAllItems();
		gcalCombo.addItem(""); //$NON-NLS-1$
		String email = (String) accountCombo.getSelectedItem();
		if (email != null) {
			AccountInfo[] accounts = config.getAccounts();
			email = email.trim();
			AccountInfo account = null;
			int i;
			for (i = 0; i < accounts.length; i++) {
				if (email.equals(accounts[i].username)) {
					account = accounts[i];
					break;
				}
			}
			if (account != null) {
				try {
					String[] urls = config.getCalendarURLs(account,
							loadFromGoogle);
					File workDir = config.getWorkDirectory();
					String url, name;
					for (i = 0; i < urls.length; i++) {
						url = urls[i];
						name = GCalUtilities.getCalendarName(url, workDir);
						if (name == null) {
							gcalCombo.addItem(url);
						} else {
							gcalCombo.addItem(name + " - " + url); //$NON-NLS-1$
						}
					}
				} catch (Exception accountException) {
					if (loadFromGoogle) {
						editor.error(Messages.getString("verify"), //$NON-NLS-1$
								Messages.getString("account.unverified") + " (" //$NON-NLS-1$ //$NON-NLS-2$
										+ accountException.getMessage() + ")!", //$NON-NLS-1$
								accountException);
					}
				}
			}
		}
		if (fileSync != null && fileSync.privateIcalUrl != null) {
			if (gcalCombo.getItemCount() == 0) {
				String name = GCalUtilities.getCalendarName(
						fileSync.privateIcalUrl, config.getWorkDirectory());
				if (name == null) {
					gcalCombo.addItem(fileSync.privateIcalUrl);
				} else {
					gcalCombo.addItem(name + " - " + fileSync.privateIcalUrl); //$NON-NLS-1$
				}
			}
			MainConfig.selectItem(gcalCombo, fileSync.privateIcalUrl);
		}
		int index = gcalCombo.getSelectedIndex();
		if (index == -1 && gcalCombo.getItemCount() != 0) {
			gcalCombo.setSelectedIndex(0);
		}
	}

	// --- FILL ICAL FILE LIST ---

	private final void fillPaths() {
		icalCombo.addItem(""); //$NON-NLS-1$
		String[] paths = config.getCalendarPaths();
		if (paths != null) {
			String path, name;
			for (int i = 0; i < paths.length; i++) {
				path = paths[i];
				name = config.getCalendarName(path);
				if (name == null) {
					icalCombo.addItem(path);
				} else {
					icalCombo.addItem(name + " - " + path); //$NON-NLS-1$
				}
			}
		}
		if (fileSync != null && fileSync.icalPath != null) {
			MainConfig.selectItem(icalCombo, fileSync.icalPath);
		}
	}

}
