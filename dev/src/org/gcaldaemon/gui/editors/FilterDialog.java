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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.HashSet;
import java.util.Iterator;
import java.util.StringTokenizer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.Messages;
import org.gcaldaemon.gui.config.AccountInfo;
import org.gcaldaemon.gui.config.MainConfig;
import org.gcaldaemon.logger.QuickWriter;

/**
 * Dialog for filter editor.
 * 
 * Created: May 25, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
final class FilterDialog extends JDialog implements WindowListener,
		ActionListener, MouseListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = 3229497438822543120L;

	// --- GUI ---

	private final JPanel root = new JPanel(null);
	private final JList list = new JList();
	private final JPanel filterPanel = new JPanel();
	private final JLabel filterLabel = new JLabel();
	private final JTextField filterField = new JTextField();
	private final JLabel sampleLabel = new JLabel();
	private final JButton newButton = new JButton();
	private final JButton editButton = new JButton();
	private final JButton deleteButton = new JButton();
	private final JButton deleteAllButton = new JButton();
	private final JButton saveButton = new JButton();
	private final JButton okButton = new JButton();

	// --- FILTER ---

	private String filterList;

	private final String key;
	private final MainConfig config;

	// --- CONSTRUCTOR ---

	FilterDialog(ConfigEditor editor, MainConfig config, String key,
			String sample) {
		super(editor, Messages.getString(key), true);
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
		filterPanel.setLayout(null);
		filterPanel.setBorder(BorderFactory.createTitledBorder(Messages
				.getString("filter.properties"))); //$NON-NLS-1$
		filterPanel.setBounds(10, 170, 300, 150);
		root.add(filterPanel);

		newButton.setText(Messages.getString("new.filter")); //$NON-NLS-1$
		newButton.setBounds(320, 10, 165, 25);
		root.add(newButton);

		editButton.setText(Messages.getString("edit")); //$NON-NLS-1$
		editButton.setBounds(320, 45, 165, 25);
		root.add(editButton);

		deleteButton.setText(Messages.getString("delete")); //$NON-NLS-1$
		deleteButton.setBounds(320, 80, 165, 25);
		root.add(deleteButton);

		deleteAllButton.setText(Messages.getString("delete.all")); //$NON-NLS-1$
		deleteAllButton.setBounds(320, 115, 165, 25);
		root.add(deleteAllButton);

		saveButton.setEnabled(false);
		saveButton.setText(Messages.getString("save")); //$NON-NLS-1$
		saveButton.setBounds(320, 200, 165, 25);
		root.add(saveButton);

		okButton.setText(Messages.getString("ok")); //$NON-NLS-1$
		okButton.setBounds(320, 294, 165, 25);
		root.add(okButton);

		filterLabel.setText(Messages.getString("pattern") + ':'); //$NON-NLS-1$
		filterLabel.setBounds(20, 30, 110, 25);
		filterPanel.add(filterLabel);

		filterField.setEnabled(false);
		filterField.setBounds(130, 30, 150, 25);
		filterPanel.add(filterField);

		sampleLabel.setText(Messages.getString("samples") + ':'); //$NON-NLS-1$
		sampleLabel.setBounds(20, 65, 110, 25);
		filterPanel.add(sampleLabel);

		StringTokenizer st = new StringTokenizer(sample, ", "); //$NON-NLS-1$
		int y = 65;
		while (st.hasMoreTokens()) {
			JLabel sampleText = new JLabel(st.nextToken());
			sampleText.setEnabled(false);
			sampleText.setBounds(130, y, 150, 25);
			filterPanel.add(sampleText);
			y += 25;
		}

		// Fill list
		filterList = config.getConfigProperty(key, "*"); //$NON-NLS-1$
		st = new StringTokenizer(filterList, ", \t;|"); //$NON-NLS-1$
		HashSet set = new HashSet();
		while (st.hasMoreTokens()) {
			String mask = st.nextToken();
			if (mask.equals("*")) { //$NON-NLS-1$
				set.clear();
				break;
			}
			set.add(mask);
		}
		if (set.isEmpty()) {
			set.add("*"); //$NON-NLS-1$
		}
		DefaultListModel model = new DefaultListModel();
		Iterator masks = set.iterator();
		while (masks.hasNext()) {
			model.addElement((String) masks.next());
		}
		list.setModel(model);
		list.setSelectedIndex(0);
		boolean enableDelete = !(set.size() == 1 && set.contains("*")); //$NON-NLS-1$
		deleteButton.setEnabled(enableDelete);
		deleteAllButton.setEnabled(enableDelete);

		// Action listeners
		addWindowListener(this);
		newButton.addActionListener(this);
		editButton.addActionListener(this);
		deleteButton.addActionListener(this);
		deleteAllButton.addActionListener(this);
		okButton.addActionListener(this);
		saveButton.addActionListener(this);
		list.addMouseListener(this);

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
		filterList = null;
		dispose();
	}

	// --- FILTER GETTER ---

	final String getFilterList() {
		return filterList;
	}

	// --- ACTION HANDLER ---

	public final void actionPerformed(ActionEvent evt) {
		Object source = evt.getSource();
		if (newButton == source) {
			filterField.setEnabled(true);
			newButton.setEnabled(false);
			editButton.setEnabled(false);
			deleteButton.setEnabled(true);
			deleteAllButton.setEnabled(true);
			saveButton.setEnabled(true);
			okButton.setEnabled(true);
			String defaultFilter = "localhost"; //$NON-NLS-1$
			if (key.equals(Configurator.MAILTERM_ALLOWED_ADDRESSES)) {
				AccountInfo[] accounts = config.getAccounts();
				if (accounts.length > 0) {
					defaultFilter = accounts[0].username;
				} else {
					defaultFilter = "user@gmail.com"; //$NON-NLS-1$
				}
			} else {
				if (key.equals(Configurator.NOTIFIER_LOCAL_USERS)) {
					defaultFilter = System.getProperty("user.name", ""); //$NON-NLS-1$ //$NON-NLS-2$
					if (defaultFilter.length() == 0) {
						defaultFilter = "root"; //$NON-NLS-1$
					}
				} else {
					if (key.indexOf("addresses") != -1) { //$NON-NLS-1$
						defaultFilter = "127.0.0.1"; //$NON-NLS-1$
					}
				}
			}
			filterField.setText(defaultFilter);
			DefaultListModel model = (DefaultListModel) list.getModel();
			model.addElement(defaultFilter);
			list.setModel(model);
			list.setSelectedIndex(model.getSize() - 1);
			filterField.selectAll();
			filterField.requestFocus();
			return;
		}
		if (editButton == source) {
			editFilter();
			return;
		}
		if (saveButton == source) {
			String filter = filterField.getText().trim();
			DefaultListModel model = (DefaultListModel) list.getModel();
			if (filter.equals("*")) { //$NON-NLS-1$
				model.removeAllElements();
				model.addElement("*"); //$NON-NLS-1$
				list.setModel(model);
				list.setSelectedIndex(0);
			} else {
				int oldIndex = model.indexOf(filter);
				int newIndex = list.getSelectedIndex();
				if (oldIndex != -1 && newIndex != -1 && oldIndex != newIndex) {
					model.removeElementAt(newIndex);
					list.setModel(model);
					list.setSelectedIndex(0);
				} else {
					if (filter.length() != 0 && newIndex != -1) {
						model.setElementAt(filter, newIndex);
						list.setModel(model);
						list.setSelectedIndex(newIndex);
						model.removeElement("*"); //$NON-NLS-1$
					}
				}
			}
			filterField.setEnabled(false);
			filterField.setText(""); //$NON-NLS-1$
			newButton.setEnabled(true);
			saveButton.setEnabled(false);
			editButton.setEnabled(true);
			return;
		}
		if (deleteAllButton == source) {
			filterList = "*"; //$NON-NLS-1$
			dispose();
			return;
		}
		if (deleteButton == source) {
			DefaultListModel model = (DefaultListModel) list.getModel();
			int index = list.getSelectedIndex();
			if (index != -1 && model.getSize() > 1) {
				model.removeElementAt(index);
				list.setModel(model);
				list.setSelectedIndex(0);
			} else {
				model.removeAllElements();
				model.addElement("*"); //$NON-NLS-1$
				list.setModel(model);
				list.setSelectedIndex(0);
			}
			filterField.setEnabled(false);
			filterField.setText(""); //$NON-NLS-1$
			newButton.setEnabled(true);
			saveButton.setEnabled(false);
			editButton.setEnabled(true);
			boolean enableDelete = !(model.getSize() == 1 && model
					.contains("*")); //$NON-NLS-1$
			deleteButton.setEnabled(enableDelete);
			deleteAllButton.setEnabled(enableDelete);
			return;
		}
		if (okButton == source) {
			DefaultListModel model = (DefaultListModel) list.getModel();
			QuickWriter writer = new QuickWriter(100);
			HashSet filters = new HashSet();
			int size = model.getSize();
			String filter;
			for (int i = 0; i < size; i++) {
				filter = (String) model.elementAt(i);
				if (filter.equals("*")) {
					writer.flush();
					writer.write("*");
					break;
				}
				if (!filters.contains(filter)) {
					filters.add(filter);
					writer.write(filter);
					writer.write(',');
				}
			}
			filterList = writer.toString();
			while (filterList.endsWith(",")) { //$NON-NLS-1$
				filterList = filterList.substring(0, filterList.length() - 1);
			}
			dispose();
			return;
		}
		if (source == root) {
			filterList = null;
			dispose();
		}
	}

	// --- ITEM SELECTED ---

	public final void mouseClicked(MouseEvent evt) {
		if (evt.getClickCount() > 1) {
			editFilter();
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

	// --- EDIT FILTER ---

	private final void editFilter() {
		filterField.setText((String) list.getSelectedValue());
		filterField.setEnabled(true);
		newButton.setEnabled(true);
		editButton.setEnabled(true);
		DefaultListModel model = (DefaultListModel) list.getModel();
		boolean enableDelete = !(model.getSize() == 1 && model.contains("*")); //$NON-NLS-1$
		deleteButton.setEnabled(enableDelete);
		deleteAllButton.setEnabled(enableDelete);
		saveButton.setEnabled(true);
		okButton.setEnabled(true);
	}

}
