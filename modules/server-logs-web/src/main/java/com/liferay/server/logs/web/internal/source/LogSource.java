package com.liferay.server.logs.web.internal.source;

import java.io.File;

/**
 * One selectable entry in the Server Logs dropdown.
 *
 * @author Liferay DXP AI Copilot
 */
public class LogSource {

	public LogSource(
		String key, String label, Type type, File directory, long companyId) {

		_key = key;
		_label = label;
		_type = type;
		_directory = directory;
		_companyId = companyId;
	}

	public long getCompanyId() {
		return _companyId;
	}

	public File getDirectory() {
		return _directory;
	}

	public String getKey() {
		return _key;
	}

	public String getLabel() {
		return _label;
	}

	public Type getType() {
		return _type;
	}

	public enum Type {

		COMPANY, FOLDER, ROOT

	}

	private final long _companyId;
	private final File _directory;
	private final String _key;
	private final String _label;
	private final Type _type;

}
