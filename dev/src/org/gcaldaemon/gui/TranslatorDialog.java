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
import java.awt.FlowLayout;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.core.StringUtils;

final class TranslatorDialog extends JDialog implements WindowListener,
		ActionListener, ItemListener {

	// --- SERIALVERSION ---
	
	private static final long serialVersionUID = 388413817772250190L;
	
	// --- VARIABLES ---

	private final ConfigEditor editor;
	private String selectedLanguage;

	// --- COMPONENTS ---

	private JPanel header;
	private JLabel label;
	private JComboBox combo;
	private JScrollPane scroll;
	private JTable table;
	private JPanel footer;
	private JButton save;
	private JButton copy;
	private JButton cancel;

	// --- CONSTRUCTOR ---

	TranslatorDialog(ConfigEditor editor) {
		super(editor, Messages.getString("translate"), true); //$NON-NLS-1$
		this.editor = editor;
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setSize(600, 500);
		Container root = getContentPane();
		BorderLayout rootLayout = new BorderLayout();
		rootLayout.setVgap(5);
		root.setLayout(rootLayout);

		// Build components
		header = new JPanel(new BorderLayout());
		label = new JLabel(" " + Messages.getString("language") + ": "); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		combo = new JComboBox();
		getAllInstalledLanguages();
		header.add(label, BorderLayout.WEST);
		header.add(combo, BorderLayout.CENTER);
		root.add(header, BorderLayout.NORTH);
		table = new JTable();
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		Locale userLocale = Messages.getUserLocale();
		if (userLocale == null) {
			userLocale = Locale.ENGLISH;
		}
		int count = combo.getItemCount();
		String locale = userLocale.getDisplayLanguage(Locale.ENGLISH);
		String item;
		for (int i = 0; i < count; i++) {
			item = (String) combo.getItemAt(i);
			if (item.startsWith(locale)) {
				combo.setSelectedIndex(i);
				break;
			}
		}
		setUserLocale(userLocale);
		scroll = new JScrollPane(table);
		root.add(scroll, BorderLayout.CENTER);
		save = new JButton(Messages.getString("save.and.test")); //$NON-NLS-1$
		copy = new JButton(Messages.getString("copy")); //$NON-NLS-1$
		cancel = new JButton(Messages.getString("exit")); //$NON-NLS-1$
		FlowLayout footerLayout = new FlowLayout();
		footerLayout.setAlignment(FlowLayout.RIGHT);
		footer = new JPanel(footerLayout);
		footer.add(save);
		footer.add(copy);
		footer.add(cancel);
		copy.setIcon(editor.getIcon("copy")); //$NON-NLS-1$
		cancel.setIcon(editor.getIcon("exit")); //$NON-NLS-1$
		save.setIcon(editor.getIcon("save")); //$NON-NLS-1$
		root.add(footer, BorderLayout.SOUTH);

		// Action listeners
		addWindowListener(this);
		copy.addActionListener(this);
		cancel.addActionListener(this);
		save.addActionListener(this);
		combo.addItemListener(this);

		// Show dialog
		setResizable(true);
		validate();
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
		dispose();
	}

	// --- FILL COMBO ---

	private final void getAllInstalledLanguages() {
		Locale[] availables = Locale.getAvailableLocales();
		Locale userLocale = Messages.getUserLocale();
		HashSet set = new HashSet();
		String temp, lang;
		int i;
		for (i = 0; i < availables.length; i++) {
			lang = availables[i].getDisplayLanguage(Locale.ENGLISH);
			if (lang == null || lang.length() == 0) {
				continue;
			}
			temp = availables[i].getDisplayLanguage(userLocale);
			if (temp != null && temp.length() > 0 && !temp.equals(lang)) {
				lang = lang + " [" + temp + ']';
			}
			set.add(lang);
		}
		String[] langs = new String[set.size()];
		set.toArray(langs);
		Arrays.sort(langs, String.CASE_INSENSITIVE_ORDER);
		for (i = 0; i < langs.length; i++) {
			combo.addItem(langs[i]);
		}
	}

	// --- ACTION HANDLER ---

	public final void actionPerformed(ActionEvent evt) {
		Object source = evt.getSource();
		if (source == cancel) {
			selectedLanguage = null;
			dispose();
			return;
		}
		if (source == save) {
			save();
			return;
		}
		if (source == copy) {
			try {
				String item = (String) combo.getSelectedItem();
				int n = item.indexOf('[');
				if (n != -1) {
					item = item.substring(0, n).trim();
				}
				Locale[] availables = Locale.getAvailableLocales();
				Locale locale;
				String lang;
				for (int i = 0; i < availables.length; i++) {
					locale = availables[i];
					lang = locale.getDisplayLanguage(Locale.ENGLISH);
					if (item.equals(lang)) {
						String code = locale.getLanguage().toLowerCase();
						copy(code);
						return;
					}
				}
			} catch (Exception ioError) {
				editor
						.error(
								Messages.getString("error"), ioError.getMessage(), ioError); //$NON-NLS-1$
			}
		}
	}

	// --- COPY TO CLIPBOARD ---

	private final void copy(String code) throws Exception {
		Properties properties = new Properties();
		TableModel model = table.getModel();
		int rows = model.getRowCount();
		for (int i = 0; i < rows; i++) {
			properties.setProperty((String) model.getValueAt(i, 0),
					(String) model.getValueAt(i, 2));
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		properties.store(out, "Language file for " + Configurator.VERSION //$NON-NLS-1$
				+ " [messages-" + code + ".txt]"); //$NON-NLS-1$ //$NON-NLS-2$

		String content = StringUtils.decodeToString(out.toByteArray(),
				StringUtils.US_ASCII);
		Clipboard clipboard = ConfigEditor.TOOLKIT.getSystemClipboard();
		StringSelection selection = new StringSelection(content);
		clipboard.setContents(selection, null);
	}

	// --- SAVE AND TEST ---

	private final void save() {
		String item = (String) combo.getSelectedItem();
		int n = item.indexOf('[');
		if (n != -1) {
			item = item.substring(0, n).trim();
		}
		Locale[] availables = Locale.getAvailableLocales();
		Locale locale;
		String lang;
		for (int i = 0; i < availables.length; i++) {
			locale = availables[i];
			lang = locale.getDisplayLanguage(Locale.ENGLISH);
			if (item.equals(lang)) {
				String code = locale.getLanguage().toLowerCase();
				saveLangFile(code, locale);
				dispose();
				return;
			}
		}
	}

	private final void saveLangFile(String code, Locale locale) {
		try {
			Properties properties = new Properties();
			TableModel model = table.getModel();
			int rows = model.getRowCount();
			for (int i = 0; i < rows; i++) {
				properties.setProperty((String) model.getValueAt(i, 0),
						(String) model.getValueAt(i, 2));
			}
			String programDir = System.getProperty("gcaldaemon.program.dir", //$NON-NLS-1$
					"/Progra~1/GCALDaemon"); //$NON-NLS-1$
			File langDir = new File(programDir, "lang"); //$NON-NLS-1$
			if (!langDir.isDirectory()) {
				langDir.mkdir();
			}
			File msgFile = new File(langDir, "messages-" + code + ".txt"); //$NON-NLS-1$ //$NON-NLS-2$
			FileOutputStream out = new FileOutputStream(msgFile);
			String lang;
			try {
				lang = (new Locale(code)).getDisplayLanguage(Locale.ENGLISH);
			} catch (Exception ignored) {
				lang = "";
			}
			properties.store(out, lang
					+ " language file for " + Configurator.VERSION //$NON-NLS-1$
					+ " [" + msgFile.getName() + ']'); //$NON-NLS-1$
			out.close();
			selectedLanguage = code;
		} catch (Exception ioError) {
			editor.error(
					Messages.getString("error"), ioError.getMessage(), ioError); //$NON-NLS-1$
		}
	}

	final String getSelectedLanguage() {
		return selectedLanguage;
	}

	// --- LANGUAGE SELECTED ---

	public final void itemStateChanged(ItemEvent evt) {
		String item = (String) combo.getSelectedItem();
		String old = (String) evt.getItem();
		if (item != null && !item.equals(old)) {
			int n = item.indexOf('[');
			if (n != -1) {
				item = item.substring(0, n).trim();
			}
			Locale[] availables = Locale.getAvailableLocales();
			String lang;
			for (int i = 0; i < availables.length; i++) {
				lang = availables[i].getDisplayLanguage(Locale.ENGLISH);
				if (item.equals(lang)) {
					setUserLocale(availables[i]);
					return;
				}
			}
		}
	}

	// --- SELECT LANGUAGE AND FILL TABLE ---

	private final void setUserLocale(Locale locale) {
		setTitle(Messages.getString("translate") + " [GCALDaemon/lang/messages-" //$NON-NLS-1$ //$NON-NLS-2$
				+ locale.getLanguage().toLowerCase() + ".txt]"); //$NON-NLS-1$
		String[] headers = {
				Messages.getString("key"), Messages.getString("english"), locale.getDisplayLanguage() }; //$NON-NLS-1$ //$NON-NLS-2$
		String[][] data = Messages.getTranslatorTable(locale);
		TranslatorTableModel model = new TranslatorTableModel(data, headers);
		table.setModel(model);
		for (int i = 0; i < data.length; i++) {
			if (data[i][1] != null && !"ok".equals(data[i][0])
					&& data[i][1].equals(data[i][2])) {
				table.getSelectionModel().setSelectionInterval(i, i);
				return;
			}
		}
	}

	// --- TABLE MODEL ---

	private final class TranslatorTableModel extends DefaultTableModel {

		// --- SERIALVERSION ---

		private static final long serialVersionUID = 2251429309814589328L;

		private TranslatorTableModel(String[][] data, String[] headers) {
			super(data, headers);
		}

		public final boolean isCellEditable(int y, int x) {
			if (x < 2) {
				return false;
			}
			return true;
		}

	}

}
