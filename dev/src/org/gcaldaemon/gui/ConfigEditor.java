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
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;

import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.border.BevelBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.core.ProgressMonitor;
import org.gcaldaemon.gui.config.MainConfig;
import org.gcaldaemon.gui.editors.BooleanEditor;
import org.gcaldaemon.gui.pages.AbstractPage;
import org.gcaldaemon.gui.pages.CommonPage;
import org.gcaldaemon.gui.pages.FeedPage;
import org.gcaldaemon.gui.pages.FilePage;
import org.gcaldaemon.gui.pages.HttpPage;
import org.gcaldaemon.gui.pages.LdapPage;
import org.gcaldaemon.gui.pages.MailtermPage;
import org.gcaldaemon.gui.pages.NotifierPage;
import org.gcaldaemon.gui.pages.SendmailPage;

/**
 * Config editor GUI.
 * 
 * Created: Apr 16, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class ConfigEditor extends JFrame implements ActionListener,
		WindowListener, ChangeListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = -4602812599254063414L;

	// --- CONSTANTS ---

	public static final Runtime RUNTIME = Runtime.getRuntime();
	public static final Toolkit TOOLKIT = Toolkit.getDefaultToolkit();

	// --- LOGGER ---

	private static final Log log = LogFactory.getLog(ConfigEditor.class);

	// --- CONFIG CONTAINER ---

	private final MainConfig config;

	// --- GUI ---

	private JMenuBar menuBar = new JMenuBar();
	private DecoratedFolder folder = new DecoratedFolder();
	private JLabel status = new JLabel(Configurator.VERSION,
			getIcon("messages"), SwingConstants.LEFT); //$NON-NLS-1$

	private JMenu fileMenu;
	private JMenuItem saveMenu;
	private JMenuItem exitMenu;
	private JMenu viewMenu;
	private JMenu langMenu;
	private JMenuItem transMenu;
	private JMenu lafMenu;
	private JMenuItem logMenu;
	private JMenu helpMenu;
	private JMenuItem aboutMenu;

	// --- CONSTRUCTORS ---

	public ConfigEditor(Configurator configurator, ProgressMonitor monitor)
			throws Exception {
		this(new MainConfig(configurator), monitor);
	}

	private ConfigEditor(MainConfig config, ProgressMonitor monitor)
			throws Exception {
		this.config = config;
		try {

			// Init swing
			System.setProperty("sun.awt.noerasebackground", "false"); //$NON-NLS-1$ //$NON-NLS-2$
			System.setProperty("swing.aatext", "true"); //$NON-NLS-1$ //$NON-NLS-2$
			System.setProperty("swing.boldMetal", "false"); //$NON-NLS-1$ //$NON-NLS-2$
			Locale locale = Locale.getDefault();
			String code = null;
			try {
				code = config.getConfigProperty(Configurator.EDITOR_LANGUAGE,
						null);
				if (code != null) {
					locale = new Locale(code);
				}
			} catch (Exception invalidLocale) {
				log.warn(invalidLocale);
			}
			if (!Messages.setUserLocale(locale)) {
				locale = Locale.ENGLISH;
				Messages.setUserLocale(locale);
				code = null;
			}
			if (code == null) {
				config.setConfigProperty(Configurator.EDITOR_LANGUAGE, locale
						.getLanguage().toLowerCase());
			}
			try {
				boolean lookAndFeelChanged = false;
				String oldLaf = UIManager.getLookAndFeel().getClass().getName();
				String newLaf = config.getConfigProperty(
						Configurator.EDITOR_LOOK_AND_FEEL, UIManager
								.getCrossPlatformLookAndFeelClassName());
				lookAndFeelChanged = !oldLaf.equals(newLaf);
				if (lookAndFeelChanged) {
					UIManager.setLookAndFeel(newLaf);
				}
				if (config.getConfigProperty(Configurator.EDITOR_LOOK_AND_FEEL,
						null) == null) {
					config.setConfigProperty(Configurator.EDITOR_LOOK_AND_FEEL,
							newLaf);
				}
				if (lookAndFeelChanged) {
					new ConfigEditor(config, monitor);
					dispose();
					return;
				}
			} catch (Exception invalidLaf) {
				log.warn(invalidLaf);
			}

			// Window settings
			setTitle(Messages.getString("config.editor") + " - " + config.getConfigPath()); //$NON-NLS-1$ //$NON-NLS-2$
			setIconImage(TOOLKIT.getImage(ConfigEditor.class
					.getResource("/org/gcaldaemon/gui/icons/icon.gif"))); //$NON-NLS-1$
			setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
			Container root = getContentPane();
			root.setLayout(new BorderLayout());
			root.add(folder, BorderLayout.CENTER);
			folder.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
			folder.setTabPlacement(JTabbedPane.LEFT);
			folder.addChangeListener(this);
			status.setBorder(new BevelBorder(BevelBorder.LOWERED));
			root.add(status, BorderLayout.SOUTH);

			// Create menu items
			fileMenu = new JMenu(Messages.getString("file")); //$NON-NLS-1$
			saveMenu = new JMenuItem(
					Messages.getString("save"), getIcon("save")); //$NON-NLS-1$ //$NON-NLS-2$
			exitMenu = new JMenuItem(
					Messages.getString("exit"), getIcon("exit")); //$NON-NLS-1$ //$NON-NLS-2$
			viewMenu = new JMenu(Messages.getString("view")); //$NON-NLS-1$
			langMenu = new JMenu(Messages.getString("language")); //$NON-NLS-1$
			lafMenu = new JMenu(Messages.getString("look.and.feel")); //$NON-NLS-1$
			transMenu = new JMenuItem(Messages.getString("translate")); //$NON-NLS-1$
			logMenu = new JMenuItem(Messages.getString("log.viewer")); //$NON-NLS-1$
			helpMenu = new JMenu(Messages.getString("help")); //$NON-NLS-1$
			aboutMenu = new JMenuItem(Messages.getString("about")); //$NON-NLS-1$

			// Build menu
			setJMenuBar(menuBar);
			menuBar.add(fileMenu);
			fileMenu.add(saveMenu);
			fileMenu.addSeparator();
			fileMenu.add(exitMenu);
			menuBar.add(viewMenu);
			viewMenu.add(logMenu);
			viewMenu.addSeparator();
			viewMenu.add(langMenu);
			viewMenu.add(lafMenu);
			langMenu.add(transMenu);
			menuBar.add(helpMenu);
			helpMenu.add(aboutMenu);

			// Build language menu
			Locale[] locales = Messages.getAvailableLocales();
			String[] names = new String[locales.length];
			String temp;
			int i;
			for (i = 0; i < locales.length; i++) {
				names[i] = locales[i].getDisplayLanguage(Locale.ENGLISH);
				if (names[i] == null || names[i].length() == 0) {
					names[i] = locales[i].getLanguage().toLowerCase();
				}
				temp = locales[i].getDisplayLanguage(locale);
				if (temp != null && temp.length() > 0 && !temp.equals(names[i])) {
					names[i] = names[i] + " [" + temp + ']';
				}
			}
			Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);
			if (locales.length != 0) {
				langMenu.addSeparator();
			}
			for (i = 0; i < locales.length; i++) {
				JMenuItem item = new JMenuItem(names[i]);
				item.setName('!' + names[i]);
				langMenu.add(item);
				item.addActionListener(this);
			}

			// Build look and feel menu
			LookAndFeelInfo[] infos = UIManager.getInstalledLookAndFeels();
			LookAndFeelInfo info;
			for (i = 0; i < infos.length; i++) {
				info = infos[i];
				JMenuItem item = new JMenuItem(info.getName());
				item.addActionListener(this);
				item.setName('#' + info.getClassName());
				lafMenu.add(item);
			}

			// Action listeners
			addWindowListener(this);
			folder.addChangeListener(this);
			exitMenu.addActionListener(this);
			saveMenu.addActionListener(this);
			transMenu.addActionListener(this);
			logMenu.addActionListener(this);
			aboutMenu.addActionListener(this);

			// Add pages
			addPage("common.settings", new CommonPage(config, this)); //$NON-NLS-1$
			addPage("http.settings", new HttpPage(config, this)); //$NON-NLS-1$
			addPage("file.settings", new FilePage(config, this)); //$NON-NLS-1$
			addPage("feed.settings", new FeedPage(config, this)); //$NON-NLS-1$
			addPage("ldap.settings", new LdapPage(config, this)); //$NON-NLS-1$
			addPage("notifier.settings", new NotifierPage(config, this)); //$NON-NLS-1$
			addPage("sendmail.settings", new SendmailPage(config, this)); //$NON-NLS-1$
			addPage("mailterm.settings", new MailtermPage(config, this)); //$NON-NLS-1$

			// Set tab colors
			Iterator editors = disabledServices.iterator();
			while (editors.hasNext()) {
				setServiceEnabled((BooleanEditor) editors.next(), false);
			}
			disabledServices = null;

			// Show GUI
			setResizable(true);
			Dimension size = TOOLKIT.getScreenSize();
			int w = size.width - 50;
			int h = size.height - 70;
			w = Math.min(w, 1000);
			h = Math.min(h, 700);
			setSize(w, h);
			setLocation((size.width - w) / 2, (size.height - h) / 2);
			validate();
			if (monitor != null) {
				monitor.dispose();
			}
			setVisible(true);
			toFront();
		} catch (Exception error) {
			if (monitor != null) {
				monitor.dispose();
			}
			error(Messages.getString("error"), "Unable to start configurator: "
					+ error, error);
		}
	}

	private final void addPage(String titleKey, AbstractPage instance) {
		String title = Messages.getString(titleKey);
		folder.addTab(title, instance);
		int pos = titleKey.indexOf('.');
		int index = folder.getTabCount() - 1;
		folder.setToolTipTextAt(index, title + " [" //$NON-NLS-1$
				+ titleKey.substring(0, pos) + ']');
	}

	// --- ICON HANDLER ---

	private final static HashMap icons = new HashMap();

	public final ImageIcon getIcon(String name) {
		ImageIcon icon = (ImageIcon) icons.get(name);
		if (icon == null) {
			icon = new ImageIcon(ConfigEditor.class
					.getResource("/org/gcaldaemon/gui/icons/" + name //$NON-NLS-1$
							+ ".gif")); //$NON-NLS-1$
			icons.put(name, icon);
		}
		return icon;
	}

	// --- COMMON UTILS ---

	public final void status(String message) {
		status.setText(' ' + message);
	}

	public final void status(String key, String value) {
		String nameKey;
		if (key.endsWith("google.username")) { //$NON-NLS-1$
			nameKey = "google.account"; //$NON-NLS-1$
		} else {
			nameKey = key;
		}
		status(Messages.getString(nameKey) + " [" + key + " = " + value + ']'); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public final void status(String key, boolean value) {
		status(key, Boolean.toString(value));
	}

	public final void error(String title, String message, Exception e) {
		if (message == null || message.length() < 5) {
			message = e.getClass().getName() + ": " + message; //$NON-NLS-1$
		}
		log.error(message, e);
		status(message);
		TOOLKIT.beep();
		String[] options = new String[1];
		options[0] = Messages.getString("ok"); //$NON-NLS-1$
		JOptionPane.showOptionDialog(this, message, title,
				JOptionPane.OK_OPTION, JOptionPane.ERROR_MESSAGE, null,
				options, null);
	}

	public final void info(String title, String message) {
		status(message);
		String[] options = new String[1];
		options[0] = Messages.getString("ok"); //$NON-NLS-1$
		JOptionPane.showOptionDialog(this, message, title,
				JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
				options, null);
	}

	public final boolean question(String title, String message) {
		status(message);
		String[] options = new String[2];
		options[0] = Messages.getString("ok"); //$NON-NLS-1$
		options[1] = Messages.getString("cancel"); //$NON-NLS-1$
		return (0 == JOptionPane.showOptionDialog(this, message, title,
				JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
				options, null));
	}

	public final String selectFile(String title, String path) {
		JFileChooser fd = new JFileChooser(path);
		fd.setDialogTitle(title);
		int state = fd.showOpenDialog(this);
		if (state != JFileChooser.APPROVE_OPTION) {
			return null;
		}
		String file = fd.getSelectedFile().getAbsolutePath();
		return file.replace('\\', '/');
	}

	public final String selectDir(String title, String path) {
		JFileChooser fd = new JFileChooser(path);
		fd.setDialogTitle(title);
		fd.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int state = fd.showOpenDialog(this);
		if (state != JFileChooser.APPROVE_OPTION) {
			return null;
		}
		String dir = fd.getSelectedFile().getAbsolutePath();
		return dir.replace('\\', '/');
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

	// --- ACTIONS ---

	public final void stateChanged(ChangeEvent evt) {
		status(folder.getTitleAt(folder.getSelectedIndex()));
	}

	public final void actionPerformed(ActionEvent evt) {

		// Common actions
		Object source = evt.getSource();
		if (source == null) {
			return;
		}
		if (source == exitMenu) {
			exit();
			return;
		}
		if (source == saveMenu) {
			save();
			return;
		}
		if (source == logMenu) {
			status(Messages.getString("log.viewer")); //$NON-NLS-1$
			new LogDialog(this, config);
			return;
		}
		if (source == transMenu) {
			status(Messages.getString("translate")); //$NON-NLS-1$
			TranslatorDialog translator = new TranslatorDialog(this);
			String code = translator.getSelectedLanguage();
			if (code != null) {
				setLanguage(code);
			}
			return;
		}
		if (source == aboutMenu) {
			info(
					Messages.getString("about"), Messages.getString("config.editor") + " - " + Configurator.VERSION); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			return;
		}
		if (source instanceof JMenuItem) {
			JMenuItem item = (JMenuItem) source;
			String param = item.getName();
			if (param != null && param.length() > 1) {
				if (param.charAt(0) == '!') {
					param = param.substring(1);
					int i = param.indexOf('[');
					if (i > 0) {
						param = param.substring(0, i).trim();
					}
					setLanguage(param);
				} else {
					status(item.getText());
					setLookAndFeel(param.substring(1));
					SwingUtilities.updateComponentTreeUI(this);
				}
			}
		}
	}

	public final void setLookAndFeel(String className) {
		try {
			UIManager.setLookAndFeel(className);
			config.setConfigProperty(Configurator.EDITOR_LOOK_AND_FEEL,
					className);
		} catch (Exception error) {
			log.warn(error);
			try {
				UIManager.setLookAndFeel(UIManager
						.getCrossPlatformLookAndFeelClassName());
			} catch (Exception ignored) {
			}
		}
	}

	public final void setLanguage(String lang) {
		Locale[] locales = Messages.getAvailableLocales();
		Locale locale = null;
		int i;
		for (i = 0; i < locales.length; i++) {
			if (lang.equalsIgnoreCase(locales[i]
					.getDisplayLanguage(Locale.ENGLISH))) {
				locale = locales[i];
				break;
			}
		}
		if (locale == null) {
			for (i = 0; i < locales.length; i++) {
				if (lang.equalsIgnoreCase(locales[i].getLanguage()
						.toLowerCase())) {
					locale = locales[i];
					break;
				}
			}
		}
		if (locale == null) {
			for (i = 0; i < locales.length; i++) {
				if (lang
						.equalsIgnoreCase(locales[i].getCountry().toLowerCase())) {
					locale = locales[i];
					break;
				}
			}
		}
		if (locale != null) {
			Messages.setUserLocale(locale);
			setVisible(false);
			config.setConfigProperty(Configurator.EDITOR_LANGUAGE, locale
					.getLanguage().toLowerCase());
			try {
				new ConfigEditor(config, null);
				dispose();
			} catch (Exception error) {
				log.error("Unable to select language (" + lang + ")!"); //$NON-NLS-1$ //$NON-NLS-2$
				setVisible(true);
			}
		}
	}

	public final void windowClosing(WindowEvent evt) {
		exit();
	}

	private final void exit() {
		if (config.isConfigChanged()) {
			if (question(Messages.getString("save.config"), //$NON-NLS-1$
					Messages.getString("confirm.save.exit"))) { //$NON-NLS-1$
				save();
				String msg = Messages.getString("config.saved"); //$NON-NLS-1$
				int i = msg.indexOf('.');
				if (i != -1 && i < msg.length() - 2) {
					msg = msg.substring(0, i) + ".\r\n" //$NON-NLS-1$
							+ msg.substring(i + 1).trim();
				}
				info(Messages.getString("save.config"), //$NON-NLS-1$
						msg);
			}
		} else {
			if (!question(
					Messages.getString("confirm.exit.title"), Messages.getString("confirm.exit.msg"))) { //$NON-NLS-1$ //$NON-NLS-2$
				return;
			}
		}
		System.exit(0);
	}

	private final void save() {
		config.saveConfig();
		status(Messages.getString("config.saved")); //$NON-NLS-1$
	}

	// --- ACCOUNT LIST CHANGED ---

	public final void updateAccountEditors() {
		int pageCount = folder.getComponentCount();
		AbstractPage page;
		Component component;
		for (int i = 0; i < pageCount; i++) {
			component = folder.getComponent(i);
			if (component != null && component instanceof AbstractPage) {
				page = (AbstractPage) component;
				page.updateAccountEditors();
			}
		}
	}

	// --- FOLDER UTILS ---

	private LinkedList disabledServices = new LinkedList();

	public final void setServiceEnabled(BooleanEditor editor, boolean enabled) {
		Component page = editor.getParent();
		if (page == null) {
			if (!enabled) {
				disabledServices.addLast(editor);
			}
		} else {
			page = page.getParent();
			int index = folder.indexOfComponent(page);
			if (index != -1) {
				if (enabled) {
					folder.setForegroundAt(index, Color.BLACK);
				} else {
					folder.setForegroundAt(index, Color.GRAY);
				}
			}
		}
	}

}
