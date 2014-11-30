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
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.Messages;
import org.gcaldaemon.gui.config.FileSync;
import org.gcaldaemon.gui.config.MainConfig;

public final class FeedDialog extends JDialog implements WindowListener,
		ActionListener, ItemListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = 354420299817277671L;
	
	// --- PROPERTIES ---

	private final ConfigEditor editor;
	private final MainConfig config;

	private FileSync fileSync;

	// --- GUI COMPONENTS ---

	private final JPanel root = new JPanel(null);
	private final JLabel feedLabel = new JLabel();
	private final JLabel icalLabel = new JLabel();
	private final JLabel icalUrlLabel = new JLabel();

	private final JLabel userLabel = new JLabel();
	private final JLabel passLabel = new JLabel();

	private final JTextField feedField = new JTextField();
	private final JComboBox icalCombo = new JComboBox();
	private final JTextField icalUrlField = new JTextField();

	private final JTextField userField = new JTextField();
	private final JPasswordField passField = new JPasswordField();

	private final JButton pasteButton = new JButton();
	private final JButton browseButton = new JButton();
	private final JButton copyButton = new JButton();
	private final JButton okButton = new JButton();
	private final JButton cancelButton = new JButton();

	// --- CONSTRUCTOR ---

	public FeedDialog(ConfigEditor editor, MainConfig config, FileSync fileSync) {
		super(editor, Messages.getString("feed.sync"), true); //$NON-NLS-1$
		this.editor = editor;
		this.config = config;
		this.fileSync = fileSync;
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setSize(575, 270);
		Container content = getContentPane();
		content.setLayout(new BorderLayout(0, 0));
		content.add(root, BorderLayout.CENTER);
		root.setSize(570, 240);
		root.setPreferredSize(new Dimension(570, 240));

		// Build GUI
		feedLabel.setBounds(10, 15, 160, 25);
		feedLabel.setText(Messages.getString("feed.url") + ':'); //$NON-NLS-1$
		root.add(feedLabel);

		icalLabel.setBounds(10, 50, 160, 25);
		icalLabel.setText(Messages.getString("ical.file") + ':'); //$NON-NLS-1$
		root.add(icalLabel);

		icalUrlLabel.setBounds(10, 85, 160, 25);
		icalUrlLabel.setText(Messages.getString("ical.file.url") + ':'); //$NON-NLS-1$
		root.add(icalUrlLabel);

		feedField.setBounds(175, 15, 200, 25);
		root.add(feedField);
		if (fileSync != null && fileSync.privateIcalUrl != null) {
			feedField.setText(fileSync.privateIcalUrl);
		}

		icalCombo.setBounds(175, 50, 200, 25);
		icalCombo.setEditable(true);
		root.add(icalCombo);
		fillPaths();

		icalUrlField.setBounds(175, 85, 200, 25);
		icalUrlField.setEditable(false);
		root.add(icalUrlField);
		setIcalFileURL();

		JPanel accountPanel = new JPanel(null);
		accountPanel.setBorder(BorderFactory.createTitledBorder(Messages
				.getString("http.auth"))); //$NON-NLS-1$
		accountPanel.setBounds(10, 120, 365, 105);
		root.add(accountPanel);

		userLabel.setBounds(15, 25, 145, 25);
		userLabel.setText(Messages.getString("user.name") + ':'); //$NON-NLS-1$
		accountPanel.add(userLabel);

		passLabel.setBounds(15, 60, 145, 25);
		passLabel.setText(Messages.getString("password") + ':'); //$NON-NLS-1$
		accountPanel.add(passLabel);

		userField.setBounds(165, 25, 185, 25);
		accountPanel.add(userField);
		if (fileSync != null && fileSync.username != null) {
			userField.setText(fileSync.username);
		}

		passField.setBounds(165, 60, 185, 25);
		accountPanel.add(passField);
		if (fileSync != null && fileSync.password != null) {
			passField.setText(fileSync.password);
		}

		pasteButton.setBounds(385, 15, 170, 25);
		pasteButton.setText(Messages.getString("paste")); //$NON-NLS-1$
		root.add(pasteButton);

		browseButton.setBounds(385, 50, 170, 25);
		browseButton.setText(Messages.getString("browse")); //$NON-NLS-1$
		root.add(browseButton);

		copyButton.setBounds(385, 85, 170, 25);
		copyButton.setText(Messages.getString("copy")); //$NON-NLS-1$
		root.add(copyButton);

		cancelButton.setBounds(385, 165, 170, 25);
		cancelButton.setText(Messages.getString("cancel")); //$NON-NLS-1$
		root.add(cancelButton);

		okButton.setBounds(385, 200, 170, 25);
		okButton.setText(Messages.getString("ok")); //$NON-NLS-1$
		root.add(okButton);

		// Action listeners
		addWindowListener(this);
		icalCombo.addItemListener(this);
		pasteButton.addActionListener(this);
		browseButton.addActionListener(this);
		copyButton.addActionListener(this);
		okButton.addActionListener(this);
		cancelButton.addActionListener(this);

		// Exit on escape
		KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
		root.registerKeyboardAction(this, stroke,
				JComponent.WHEN_IN_FOCUSED_WINDOW);

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
		fileSync = null;
		dispose();
	}

	public FileSync getFileSync() {
		return fileSync;
	}

	// --- ACTION HANDLER ---

	public final void actionPerformed(ActionEvent evt) {
		Object source = evt.getSource();
		if (pasteButton == source) {
			try {
				Clipboard clipboard = ConfigEditor.TOOLKIT.getSystemClipboard();
				Transferable transferable = clipboard.getContents(clipboard);
				String content = (String) transferable
						.getTransferData(DataFlavor.stringFlavor);
				if (content == null) {
					ConfigEditor.TOOLKIT.beep();
					return;
				}
				content = content.replace('\r', ' ');
				content = content.replace('\n', ' ');
				content = content.replace('\t', ' ');
				content = content.trim();
				if (content.length() == 0) {
					ConfigEditor.TOOLKIT.beep();
					return;
				}
				if (!content.startsWith("http")) { //$NON-NLS-1$
					ConfigEditor.TOOLKIT.beep();
					return;
				}
				feedField.setText(content);
			} catch (Exception ioException) {
				editor.status(ioException.getMessage());
			}
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
			String url = feedField.getText();
			String path = (String) icalCombo.getSelectedItem();
			String user = userField.getText();
			String pass = new String(passField.getPassword());
			if (url.length() == 0 || path.length() == 0
					|| !url.startsWith("http")) { //$NON-NLS-1$
				ConfigEditor.TOOLKIT.beep();
				fileSync = null;
				dispose();
				return;
			}
			int i = path.indexOf(" - "); //$NON-NLS-1$
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
			if (user.length() != 0 && pass.length() != 0) {
				fileSync.username = user;
				fileSync.password = pass;
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

	// --- COMBO ITEM SELECTED ---

	public final void itemStateChanged(ItemEvent evt) {
		Object source = evt.getSource();
		if (icalCombo == source) {
			setIcalFileURL();
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
