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
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.HashMap;

import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.core.GCalUtilities;
import org.gcaldaemon.gui.AccountDialog;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.Messages;
import org.gcaldaemon.gui.config.AccountInfo;
import org.gcaldaemon.gui.config.FileSync;
import org.gcaldaemon.gui.config.MainConfig;
import org.gcaldaemon.gui.editors.BooleanEditor;
import org.gcaldaemon.gui.editors.TimeEditor;

/**
 * Configurator page for file-based synchronizer.
 * 
 * Created: May 25, 2007 20:00:00 PM
 * 
 * @author Andras Berkes
 */
public final class FilePage extends AbstractPage implements ActionListener,
		ChangeListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = -7039222022431388961L;

	// --- GUI ---

	private final JPanel panel;
	private final JLabel title;
	private final JTabbedPane folder;
	private final JPanel toolbar;

	private final JButton newButton;
	private final JButton removeButton;
	private final JButton editButton;
	private final JButton accountsButton;

	private final TimeEditor filePolling;
	private final JLabel warning = new JLabel();

	// --- CONSTRUCTOR ---

	public FilePage(MainConfig config, ConfigEditor editor) throws Exception {
		super(config, editor);

		// # Enable iCalendar file listener/synchronizer
		// file.enabled=false
		BooleanEditor enable = new BooleanEditor(config, editor,
				Configurator.FILE_ENABLED, false);
		enable.setIcon("plugin"); //$NON-NLS-1$
		enable.markAsServiceEnabler();
		addEditor(enable);

		// # Turn it on when you use dial-up connection (default is 'false')
		// file.offline.enabled=false
		BooleanEditor offline = new BooleanEditor(this, config, editor,
				Configurator.FILE_OFFLINE_ENABLED, false);
		offline.setIcon("mobile"); //$NON-NLS-1$
		addEditor(offline);
		editorPanel.add(Box.createRigidArea(BIG_SPACE));

		// # Local iCalendar file polling interval (recommended is '10 sec')
		// file.polling.file=10 sec
		filePolling = new TimeEditor(config, editor,
				Configurator.FILE_POLLING_FILE, "1 sec", "10 sec", "15 sec", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				TimeEditor.SEC, "sec"); //$NON-NLS-1$
		filePolling.setEnabled(!offlineMode);
		addEditor(filePolling);

		// # Google Calendar polling interval (recommended is '10 min')
		// file.polling.google=10 min
		addEditor(new TimeEditor(config, editor,
				Configurator.FILE_POLLING_GOOGLE, "10 min", "10 min", "2 hour", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				TimeEditor.MIN, "min")); //$NON-NLS-1$

		// --- BUILD TABLE ---

		BorderLayout panelLayout = new BorderLayout();
		panelLayout.setVgap(5);
		panel = new JPanel(panelLayout);
		folder = new JTabbedPane();
		folder.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		folder.setTabPlacement(JTabbedPane.BOTTOM);
		panel.add(folder, BorderLayout.CENTER);
		buildTable();
		add(panel, BorderLayout.CENTER);
		title = new JLabel();
		title.setText(Messages.getString("sync.these.calendars") + ':'); //$NON-NLS-1$
		panel.add(title, BorderLayout.NORTH);

		toolbar = new JPanel();
		BorderLayout toolbarLayout = new BorderLayout();
		toolbar.setLayout(toolbarLayout);
		panel.add(toolbar, BorderLayout.SOUTH);

		FlowLayout buttonLayout = new FlowLayout(FlowLayout.RIGHT);
		buttonLayout.setHgap(5);
		JPanel buttons = new JPanel(buttonLayout);
		warning.setForeground(Color.GRAY);
		toolbar.add(warning, BorderLayout.CENTER);
		toolbar.add(buttons, BorderLayout.EAST);

		// Add task button
		newButton = new JButton();
		newButton.setText(Messages.getString("new")); //$NON-NLS-1$
		newButton.setIcon(editor.getIcon("add")); //$NON-NLS-1$
		newButton.addActionListener(this);
		buttons.add(newButton);

		// Remove task button
		removeButton = new JButton();
		removeButton.setText(Messages.getString("remove")); //$NON-NLS-1$
		removeButton.setIcon(editor.getIcon("remove")); //$NON-NLS-1$
		removeButton.addActionListener(this);
		buttons.add(removeButton);

		// Edit task button
		editButton = new JButton();
		editButton.setText(Messages.getString("edit")); //$NON-NLS-1$
		editButton.setIcon(editor.getIcon("edit")); //$NON-NLS-1$
		editButton.addActionListener(this);
		buttons.add(editButton);

		// Accounts button
		accountsButton = new JButton();
		accountsButton.setText(Messages.getString("google.accounts")); //$NON-NLS-1$
		accountsButton.setIcon(editor.getIcon("account")); //$NON-NLS-1$
		accountsButton.addActionListener(this);
		buttons.add(accountsButton);

		setButtonAccess();
	}

	// --- PAGE SELECTED ---

	private String lastPage;

	public final void stateChanged(ChangeEvent evt) {
		if (evt.getSource() == folder) {
			int index = folder.getSelectedIndex();
			if (index != -1) {
				lastPage = folder.getTitleAt(index);
			} else {
				lastPage = null;
			}
			setButtonAccess();
		}
	}

	private final void setButtonAccess() {
		FileSync fileSync = getSelectedFileSync();
		editButton.setEnabled(fileSync != null);
		removeButton.setEnabled(fileSync != null);
	}

	// --- TABLE BUILDER ---

	private static final String NEWS_AND_BLOGS_KEY = "news.and.blogs"; //$NON-NLS-1$
	private static final String UNDEFINED = "???"; //$NON-NLS-1$
	private static final String ADD_MENU = "add"; //$NON-NLS-1$
	private static final String REMOVE_MENU = "remove"; //$NON-NLS-1$
	private static final String EDIT_MENU = "edit"; //$NON-NLS-1$
	private static final String ACCOUNTS_MENU = "accounts"; //$NON-NLS-1$

	private final void buildTable() {

		// Create tabs
		folder.removeChangeListener(this);
		folder.removeAll();
		HashMap models = new HashMap();
		AccountInfo[] infos = config.getAccounts();
		String username;
		FileHeaderContent[] headers = new FileHeaderContent[2];
		headers[0] = new FileHeaderContent(Messages
				.getString("google.calendar"), editor //$NON-NLS-1$
				.getIcon("gcal")); //$NON-NLS-1$
		headers[1] = new FileHeaderContent(
				Messages.getString("ical.calendar"), editor //$NON-NLS-1$
						.getIcon("ical")); //$NON-NLS-1$
		Dimension headerSize = new Dimension(200, 25);
		tables = new JTable[infos.length + 1];
		FileTableModel model;
		for (int i = 0; i < tables.length; i++) {
			if (i < infos.length) {
				username = infos[i].username;
			} else {
				username = Messages.getString("news.and.blogs"); //$NON-NLS-1$
				headers[0] = new FileHeaderContent(Messages
						.getString("rss.atom.feed"), editor //$NON-NLS-1$
						.getIcon("rss")); //$NON-NLS-1$
			}
			JTable table = new JTable();
			table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			tables[i] = table;
			table.setRowHeight(25);
			JScrollPane scroll = new JScrollPane(table);
			folder.addTab(username, scroll);
			model = new FileTableModel(headers);
			table.setModel(model);
			if (i < infos.length) {
				models.put(username, model);
			} else {
				models.put(NEWS_AND_BLOGS_KEY, model);
			}
			JTableHeader tableHeader = table.getTableHeader();
			tableHeader.setPreferredSize(headerSize);
			TableColumnModel columnModel = tableHeader.getColumnModel();
			TableColumn col0 = columnModel.getColumn(0);
			TableColumn col1 = columnModel.getColumn(1);
			col0.setHeaderRenderer(new FileHeaderRenderer());
			col1.setHeaderRenderer(new FileHeaderRenderer());
			col0.setHeaderValue(headers[0]);
			col1.setHeaderValue(headers[1]);

			// Add context menu
			JPopupMenu popup = new JPopupMenu();
			popup.setName(username);

			JMenuItem addMenu = new JMenuItem(Messages.getString("new")); //$NON-NLS-1$
			addMenu.setName(ADD_MENU);
			addMenu.setIcon(editor.getIcon("add")); //$NON-NLS-1$
			addMenu.addActionListener(this);
			popup.add(addMenu);

			JMenuItem removeMenu = new JMenuItem(Messages.getString("remove")); //$NON-NLS-1$
			removeMenu.setName(REMOVE_MENU);
			removeMenu.setIcon(editor.getIcon("remove")); //$NON-NLS-1$
			removeMenu.addActionListener(this);
			popup.add(removeMenu);

			JMenuItem editMenu = new JMenuItem(Messages.getString("edit")); //$NON-NLS-1$
			editMenu.setName(EDIT_MENU);
			editMenu.setIcon(editor.getIcon("edit")); //$NON-NLS-1$
			editMenu.addActionListener(this);
			popup.add(editMenu);

			popup.addSeparator();
			JMenuItem accountsMenu = new JMenuItem(Messages
					.getString("google.accounts")); //$NON-NLS-1$
			accountsMenu.setName(ACCOUNTS_MENU);
			accountsMenu.setIcon(editor.getIcon("account")); //$NON-NLS-1$
			accountsMenu.addActionListener(this);
			popup.add(accountsMenu);

			table.addMouseListener(new PopupListener(table, popup));
		}

		// Fill tables
		FileSync[] configs = config.getFileSyncConfigs();
		File workDir = config.getWorkDirectory();
		FileSync fileSync;
		String[] row;
		String name;
		for (int i = 0; i < configs.length; i++) {
			fileSync = configs[i];
			username = fileSync.username;
			if (username != null) {
				model = (FileTableModel) models.get(username);
				if (model != null) {
					row = new String[2];
					row[0] = fileSync.privateIcalUrl;
					if (row[0] == null) {
						row[0] = UNDEFINED;
					} else {
						name = GCalUtilities.getCalendarName(row[0], workDir);
						if (name != null) {
							row[0] = name + " - " + row[0]; //$NON-NLS-1$
						}
					}
					if (row[0].endsWith(".ics")) { //$NON-NLS-1$
						row[1] = fileSync.icalPath;
						if (row[1] == null) {
							row[1] = UNDEFINED;
						} else {
							name = config.getCalendarName(row[1]);
							if (name != null) {
								row[1] = name + " - " + row[1]; //$NON-NLS-1$
							}
						}
						model.addRow(row);
						continue;
					}
				}
			}
			model = (FileTableModel) models.get(NEWS_AND_BLOGS_KEY);
			row = new String[2];
			row[0] = fileSync.privateIcalUrl;
			if (row[0] == null) {
				row[0] = UNDEFINED;
			}
			row[1] = fileSync.icalPath;
			if (row[1] == null) {
				row[1] = UNDEFINED;
			} else {
				name = config.getCalendarName(row[1]);
				if (name != null) {
					row[1] = name + " - " + row[1]; //$NON-NLS-1$
				}
			}
			model.addRow(row);
		}

		// Select first rows
		for (int i = 0; i < tables.length; i++) {
			if (tables[i].getRowCount() > 0) {
				tables[i].getSelectionModel().setSelectionInterval(0, 0);
			}
		}

		// Select page
		int index = -1;
		if (lastPage == null) {
			for (int i = 0; i < tables.length; i++) {
				if (tables[i].getRowCount() > 0) {
					index = i;
					break;
				}
			}
		} else {
			index = folder.indexOfTab(lastPage);
		}
		if (index != -1) {
			folder.setSelectedIndex(index);
		}
		folder.addChangeListener(this);
	}

	// --- TABLE UTILS ---

	private final class PopupListener implements MouseListener {

		private final JTable table;
		private final JPopupMenu popup;

		private PopupListener(JTable table, JPopupMenu popup) {
			this.table = table;
			this.popup = popup;
		}

		public final void mouseExited(MouseEvent evt) {
		}

		public final void mouseEntered(MouseEvent evt) {
		}

		public final void mouseClicked(MouseEvent evt) {
		}

		public final void mouseReleased(MouseEvent evt) {
			showPopup(evt);
		}

		public final void mousePressed(MouseEvent evt) {
			showPopup(evt);
		}

		private final void showPopup(MouseEvent evt) {
			if (evt.isPopupTrigger()) {
				popup.show(table, evt.getX(), evt.getY());
			}
		}

	}

	private final class FileTableModel extends DefaultTableModel {

		// --- SERIALVERSION ---

		private static final long serialVersionUID = 2760650454048799161L;

		private FileTableModel(FileHeaderContent[] headers) {
			super(new String[0][2], headers);
		}

		public final boolean isCellEditable(int y, int x) {
			return false;
		}

	}

	private final class FileHeaderContent {

		private final String text;
		private final Icon icon;

		private FileHeaderContent(String text, Icon icon) {
			this.text = text;
			this.icon = icon;
		}

	}

	private final class FileHeaderRenderer extends DefaultTableCellRenderer {

		// --- SERIALVERSION ---

		private static final long serialVersionUID = 9030100691931763973L;

		public Component getTableCellRendererComponent(JTable table,
				Object value, boolean isSelected, boolean hasFocus, int row,
				int column) {
			if (table != null) {
				JTableHeader header = table.getTableHeader();
				if (header != null) {
					setForeground(header.getForeground());
					setBackground(header.getBackground());
					setFont(header.getFont());
				}
			}
			if (value instanceof FileHeaderContent) {
				setIcon(((FileHeaderContent) value).icon);
				setText(((FileHeaderContent) value).text);
			} else {
				setText((value == null) ? "" : value.toString()); //$NON-NLS-1$
				setIcon(null);
			}
			setBorder(UIManager.getBorder("TableHeader.cellBorder")); //$NON-NLS-1$
			setHorizontalAlignment(JLabel.CENTER);
			return this;
		}
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
		if (source == newButton) {
			addFileSync();
			return;
		}
		if (source == removeButton) {
			removeFileSync();
			return;
		}
		if (source == editButton) {
			editFileSync();
			return;
		}
		if (source instanceof JMenuItem) {
			JMenuItem menu = (JMenuItem) source;
			String command = menu.getName();
			if (ADD_MENU.equals(command)) {
				addFileSync();
				return;
			}
			if (REMOVE_MENU.equals(command)) {
				removeFileSync();
				return;
			}
			if (EDIT_MENU.equals(command)) {
				editFileSync();
				return;
			}
			if (ACCOUNTS_MENU.equals(command)) {
				manageAccounts();
				return;
			}
		}
	}

	// --- OFFLINE MODE CHANGED ---

	private boolean offlineMode;

	public final void setChecked(boolean offlineMode) {
		this.offlineMode = offlineMode;
		if (filePolling != null) {
			filePolling.setEnabled(!offlineMode);
		}
		if (!offlineMode) {
			String message = Messages.getString("offline.recommended"); //$NON-NLS-1$
			warning.setText(message);
			warning.setToolTipText(message);
			warning.setIcon(editor.getIcon("warn")); //$NON-NLS-1$
		} else {
			warning.setText(""); //$NON-NLS-1$
			warning.setIcon(null);
			warning.setToolTipText(null);
		}
	}

	// --- GET SELECTED TASK ---

	private JTable[] tables;

	private final FileSync getSelectedFileSync() {
		if (tables == null || tables.length == 0) {
			return null;
		}
		int n, index = folder.getSelectedIndex();
		if (index == -1) {
			return null;
		}
		JTable table = tables[index];
		index = table.getSelectedRow();
		if (index == -1) {
			return null;
		}
		FileTableModel model = (FileTableModel) table.getModel();
		String url = (String) model.getValueAt(index, 0);
		if (url == null) {
			url = ""; //$NON-NLS-1$
		} else {
			n = url.lastIndexOf(' ');
			if (n != -1) {
				url = url.substring(n + 1);
			}
		}
		String path = (String) model.getValueAt(index, 1);
		if (path == null) {
			path = ""; //$NON-NLS-1$
		} else {
			n = path.indexOf(" - "); //$NON-NLS-1$
			if (n != -1) {
				path = path.substring(n + 3);
			}
		}
		FileSync[] configs = config.getFileSyncConfigs();
		FileSync fileSync;
		for (int i = 0; i < configs.length; i++) {
			fileSync = configs[i];
			if (url.equals(fileSync.privateIcalUrl)) {
				return fileSync;
			}
			if (path.equals(fileSync.icalPath)) {
				return fileSync;
			}
		}
		return null;
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

	private final void addFileSync() {
		int index = folder.getSelectedIndex();
		if (index == -1) {
			return;
		}
		boolean feedMode = (index == folder.getTabCount() - 1);
		if (feedMode
				&& !config.getConfigProperty(Configurator.FEED_ENABLED, true)) {
			editor.error(Messages.getString("error"), //$NON-NLS-1$
					Messages.getString("feed.disabled"), null); //$NON-NLS-1$
			return;
		}
		FileSync fileSync = null;
		if (feedMode) {
			FeedDialog dialog = new FeedDialog(editor, config, null);
			fileSync = dialog.getFileSync();
		} else {
			String user = folder.getTitleAt(index);
			FileSync[] infos = config.getFileSyncConfigs();
			for (int i = 0; i < infos.length; i++) {
				if (user.equals(infos[i].username)) {
					fileSync = new FileSync();
					fileSync.username = infos[i].username;
					fileSync.password = infos[i].password;
					break;
				}
			}
			if (fileSync == null) {
				fileSync = new FileSync();
				fileSync.username = user;
			}
			GCalDialog dialog = new GCalDialog(editor, config, fileSync);
			fileSync = dialog.getFileSync();
		}
		if (fileSync == null || fileSync.privateIcalUrl == null
				|| fileSync.icalPath == null) {
			return;
		}
		config.setFileSyncConfig(fileSync);
		config.markAsChanged();
		if (!feedMode) {
			editor.updateAccountEditors();
		}
		buildTable();
		setButtonAccess();
	}

	private final void removeFileSync() {
		FileSync fileSync = getSelectedFileSync();
		if (fileSync == null) {
			return;
		}
		if (!editor
				.question(
						Messages.getString("delete"), Messages.getString("are.you.sure"))) { //$NON-NLS-1$ //$NON-NLS-2$
			return;
		}
		config.removeFileSyncConfig(fileSync);
		config.markAsChanged();
		buildTable();
		setButtonAccess();
	}

	private final void editFileSync() {
		int index = folder.getSelectedIndex();
		if (index == -1) {
			return;
		}
		boolean feedMode = (index == folder.getTabCount() - 1);
		if (feedMode
				&& !config.getConfigProperty(Configurator.FEED_ENABLED, true)) {
			editor.error(Messages.getString("error"), //$NON-NLS-1$
					Messages.getString("feed.disabled"), null); //$NON-NLS-1$
			return;
		}
		FileSync fileSync = getSelectedFileSync();
		if (fileSync == null) {
			return;
		}
		if (feedMode) {
			FeedDialog dialog = new FeedDialog(editor, config, fileSync);
			fileSync = dialog.getFileSync();
		} else {
			GCalDialog dialog = new GCalDialog(editor, config, fileSync);
			fileSync = dialog.getFileSync();
		}
		if (fileSync == null || fileSync.privateIcalUrl == null
				|| fileSync.privateIcalUrl == null) {
			return;
		}
		config.setFileSyncConfig(fileSync);
		config.markAsChanged();
		buildTable();
		setButtonAccess();
	}

	// --- ACCOUNT LIST CHANGED ---

	public final void updateAccountEditors() {
		buildTable();
		setButtonAccess();
	}

}
