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
import java.awt.Font;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;

import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.core.StringUtils;
import org.gcaldaemon.gui.config.MainConfig;

final class LogDialog extends JDialog implements WindowListener,
		ActionListener, ItemListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = -6845913386766054706L;

	// --- VARIABLES ---

	private final ConfigEditor editor;

	// --- COMPONENTS ---

	private JPanel header;
	private JLabel label;
	private JComboBox combo;
	private JScrollPane scroll;
	private JTextArea area;
	private JPanel footer;
	private JButton copy;
	private JButton cancel;

	// --- CONFIG CONTAINER ---

	private final MainConfig config;

	// --- CONSTRUCTOR ---

	LogDialog(ConfigEditor editor, MainConfig config) {
		super(editor, Messages.getString("log.viewer"), true); //$NON-NLS-1$
		this.editor = editor;
		this.config = config;
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setSize(650, 500);
		Container root = getContentPane();
		BorderLayout rootLayout = new BorderLayout();
		rootLayout.setVgap(5);
		root.setLayout(rootLayout);

		// Build components
		header = new JPanel(new BorderLayout());
		label = new JLabel(" " + Messages.getString("log.file") + ": "); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		combo = new JComboBox();
		header.add(label, BorderLayout.WEST);
		header.add(combo, BorderLayout.CENTER);
		root.add(header, BorderLayout.NORTH);
		area = new JTextArea();
		area.setEditable(false);
		area.setFont(new Font("Monospaced", Font.PLAIN, 12)); //$NON-NLS-1$
		area.setText(Configurator.VERSION);
		scroll = new JScrollPane(area);
		root.add(scroll, BorderLayout.CENTER);
		copy = new JButton(Messages.getString("copy")); //$NON-NLS-1$
		cancel = new JButton(Messages.getString("exit")); //$NON-NLS-1$
		FlowLayout footerLayout = new FlowLayout();
		footerLayout.setAlignment(FlowLayout.RIGHT);
		footer = new JPanel(footerLayout);
		footer.add(copy);
		footer.add(cancel);
		copy.setIcon(editor.getIcon("copy")); //$NON-NLS-1$
		cancel.setIcon(editor.getIcon("exit")); //$NON-NLS-1$
		root.add(footer, BorderLayout.SOUTH);

		// Load logs
		try {
			getAllLogs();
		} catch (Exception ioError) {
			editor.error(
					Messages.getString("error"), ioError.getMessage(), ioError); //$NON-NLS-1$
		}

		// Action listeners
		addWindowListener(this);
		copy.addActionListener(this);
		cancel.addActionListener(this);
		combo.addItemListener(this);

		// Exit on escape
		KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
		header.registerKeyboardAction(this, stroke,
				JComponent.WHEN_IN_FOCUSED_WINDOW);

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

	private final void getAllLogs() throws Exception {

		// Compute log config path
		String programDir = System.getProperty("gcaldaemon.program.dir", //$NON-NLS-1$
				"/Progra~1/GCALDaemon"); //$NON-NLS-1$		
		String logConfig = config.getConfigProperty("log.config", //$NON-NLS-1$
				"logger-config.cfg"); //$NON-NLS-1$
		logConfig = logConfig.replace('\\', '/');
		File logConfigFile;
		if (logConfig.indexOf('/') == -1) {
			logConfigFile = new File(programDir + "/conf", logConfig); //$NON-NLS-1$
		} else {
			logConfigFile = new File(logConfig);
		}
		if (!logConfigFile.isFile()) {
			return;
		}
		Properties props = new Properties();
		FileInputStream in = new FileInputStream(logConfigFile);
		props.load(new BufferedInputStream(in));
		in.close();
		String logDir = props.getProperty("log4j.appender.file.Directory", //$NON-NLS-1$
				"autodetect"); //$NON-NLS-1$
		if (logDir.equals("autodetect")) { //$NON-NLS-1$
			logDir = programDir + "/log"; //$NON-NLS-1$
		}
		File dir = new File(logDir);
		if (!dir.isDirectory()) {
			return;
		}
		File[] files = dir.listFiles();
		HashSet pathSet = new HashSet();
		int i;
		for (i = 0; i < files.length; i++) {
			if (files[i].isFile()) {
				pathSet.add(files[i].getCanonicalPath().replace('\\', '/'));
			}
		}
		String[] paths = new String[pathSet.size()];
		pathSet.toArray(paths);
		Arrays.sort(paths, String.CASE_INSENSITIVE_ORDER);
		for (i = 0; i < paths.length; i++) {
			combo.addItem(paths[i]);
		}
		combo.setSelectedIndex(paths.length - 1);
		loadLog(paths[paths.length - 1]);
	}

	// --- ACTION HANDLER ---

	public final void actionPerformed(ActionEvent evt) {
		Object source = evt.getSource();
		if (cancel == source) {
			dispose();
			return;
		}
		if (copy == source) {
			String content = area.getSelectedText();
			if (content == null || content.length() == 0) {
				content = area.getText();
			}
			Clipboard clipboard = ConfigEditor.TOOLKIT.getSystemClipboard();
			StringSelection selection = new StringSelection(content);
			clipboard.setContents(selection, null);
			return;
		}
		if (source == header) {
			dispose();
		}
	}

	// --- LOG SELECTED ---

	public final void itemStateChanged(ItemEvent evt) {
		Object item = combo.getSelectedItem();
		Object old = evt.getItem();
		if (old != item && item != null) {
			loadLog((String) item);
		}
	}

	private final void loadLog(String path) {
		try {
			RandomAccessFile raf = new RandomAccessFile(path, "r"); //$NON-NLS-1$
			byte[] bytes = new byte[(int) raf.length()];
			raf.readFully(bytes);
			raf.close();
			String content;
			try {
				content = StringUtils.decodeToString(bytes, StringUtils.UTF_8);
			} catch (Exception encodingException) {
				content = StringUtils.decodeToString(bytes,
						StringUtils.US_ASCII);
			}
			area.setText(content);
			int i = content.indexOf("ERROR"); //$NON-NLS-1$
			if (i == -1) {
				area.setSelectionStart(content.length());
			} else {
				area.setSelectionStart(i);
			}
		} catch (Exception isError) {
			editor.error(
					Messages.getString("error"), isError.getMessage(), isError); //$NON-NLS-1$
		}
	}

}
