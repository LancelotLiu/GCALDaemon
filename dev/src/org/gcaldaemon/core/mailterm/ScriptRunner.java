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
package org.gcaldaemon.core.mailterm;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.gcaldaemon.logger.QuickWriter;

/**
 * Script runner thread.
 * 
 * Created: Feb 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
final class ScriptRunner extends Thread {

	// --- VARIABLES ---

	private final File scriptDir;
	private final String encoding;

	private String[] args;
	private Process script;
	private QuickWriter scriptOutput;

	// --- CONSTRUCTOR ---

	ScriptRunner(File scriptDir, String encoding, String[] args) {
		this.scriptDir = scriptDir;
		this.encoding = encoding;
		this.args = args;
		this.scriptOutput = new QuickWriter();
		this.setDaemon(true);
		this.start();
	}

	public final void run() {
		try {

			// Find script
			String part = args[0].toLowerCase() + '.';
			String[] scripts = scriptDir.list();
			String fileName = null;
			for (int i = 0; i < scripts.length; i++) {
				if (scripts[i].toLowerCase().indexOf(part) != -1) {
					fileName = scripts[i];
					break;
				}
			}
			if (fileName == null) {
				scriptOutput.write("Unknown script: " + args[0]);
				return;
			}
			File scriptFile = new File(scriptDir, fileName);
			args[0] = scriptFile.getCanonicalPath();

			// Execute script
			ProcessBuilder builder = new ProcessBuilder(args);
			builder.redirectErrorStream(true);
			builder.directory(scriptDir);
			script = builder.start();

			// Read script output
			InputStream in = script.getInputStream();
			BufferedInputStream bis = new BufferedInputStream(in);
			InputStreamReader isr = new InputStreamReader(bis, encoding);
			char[] chars = new char[1024];
			int len;
			while ((len = isr.read(chars)) != -1) {
				scriptOutput.write(chars, 0, len);
			}
		} catch (Exception commandError) {
			StringWriter stringWriter = new StringWriter();
			PrintWriter printWriter = new PrintWriter(stringWriter);
			commandError.printStackTrace(printWriter);
			scriptOutput.write("Command execution failed!\r\n\r\n");
			scriptOutput.write(stringWriter.toString());
		}
		script = null;
	}

	final String getScriptOutput() {

		// Destroy script's process
		if (script != null) {
			try {
				script.destroy();
			} catch (Exception ignored) {
			}
		}

		// Interrupt thread
		interrupt();

		// Return output
		return scriptOutput.toString();
	}

}
