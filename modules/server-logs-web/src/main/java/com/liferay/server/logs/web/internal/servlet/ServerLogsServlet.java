package com.liferay.server.logs.web.internal.servlet;

import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactory;
import com.liferay.portal.kernel.servlet.ServletResponseUtil;
import com.liferay.portal.kernel.util.FastDateFormatFactoryUtil;
import com.liferay.portal.kernel.util.MimeTypes;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.server.logs.web.constants.ServerLogsConstants;
import com.liferay.server.logs.web.internal.source.LogSource;
import com.liferay.server.logs.web.internal.source.LogSourceProvider;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.text.Format;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Read only JSON and download endpoint backing the Server Logs portlet.
 *
 * <ul>
 * <li><code>GET /o/server-logs</code> lists the log sources the current user
 * may browse.</li>
 * <li><code>GET /o/server-logs?sourceKey=...</code> lists the files of one
 * source.</li>
 * <li><code>GET /o/server-logs?sourceKey=...&amp;fileName=...</code> returns a
 * capped preview of one file.</li>
 * <li><code>GET
 * /o/server-logs?sourceKey=...&amp;fileName=...&amp;action=download</code>
 * streams the whole file as an attachment.</li>
 * </ul>
 *
 * @author Liferay DXP AI Copilot
 */
@Component(
	property = {
		"osgi.http.whiteboard.servlet.name=com.liferay.server.logs.web.internal.servlet.ServerLogsServlet",
		"osgi.http.whiteboard.servlet.pattern=/server-logs/*",
		"servlet.init.httpMethods=GET"
	},
	service = Servlet.class
)
public class ServerLogsServlet extends HttpServlet {

	@Override
	protected void doGet(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse)
		throws IOException, ServletException {

		try {
			PermissionChecker permissionChecker = _getPermissionChecker(
				httpServletRequest);

			Locale locale = httpServletRequest.getLocale();

			String sourceKey = ParamUtil.getString(
				httpServletRequest, "sourceKey");

			if (Validator.isNull(sourceKey)) {
				_listSources(
					httpServletResponse, permissionChecker, locale);

				return;
			}

			LogSource logSource = _logSourceProvider.getLogSource(
				permissionChecker, locale, sourceKey);

			String fileName = ParamUtil.getString(
				httpServletRequest, "fileName");

			if (Validator.isNull(fileName)) {
				_listFiles(httpServletResponse, logSource, locale);

				return;
			}

			File file = _getFile(logSource, fileName);

			String action = ParamUtil.getString(httpServletRequest, "action");

			if (ServerLogsConstants.ACTION_DOWNLOAD.equals(action)) {
				_download(httpServletRequest, httpServletResponse, file);
			}
			else {
				_read(httpServletResponse, file, locale);
			}
		}
		catch (PrincipalException principalException) {
			if (_log.isWarnEnabled()) {
				_log.warn(principalException);
			}

			_portal.sendError(
				HttpServletResponse.SC_FORBIDDEN, principalException,
				httpServletRequest, httpServletResponse);
		}
		catch (FileNotFoundException fileNotFoundException) {
			if (_log.isWarnEnabled()) {
				_log.warn(fileNotFoundException);
			}

			_portal.sendError(
				HttpServletResponse.SC_NOT_FOUND, fileNotFoundException,
				httpServletRequest, httpServletResponse);
		}
		catch (Exception exception) {
			if (_log.isWarnEnabled()) {
				_log.warn(exception);
			}

			_portal.sendError(
				exception, httpServletRequest, httpServletResponse);
		}
	}

	private void _download(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse, File file)
		throws Exception {

		String fileName = file.getName();

		try (InputStream inputStream = Files.newInputStream(file.toPath())) {
			ServletResponseUtil.sendFile(
				httpServletRequest, httpServletResponse, fileName, inputStream,
				file.length(), _mimeTypes.getContentType(fileName),
				"attachment");
		}
	}

	private File _getFile(LogSource logSource, String fileName)
		throws Exception {

		if (fileName.contains("/") || fileName.contains("\\") ||
			fileName.contains("..")) {

			throw new PrincipalException(
				"Invalid log file name " + fileName);
		}

		File directory = logSource.getDirectory();

		Path directoryPath = directory.toPath();

		directoryPath = directoryPath.normalize();

		Path path = Paths.get(directoryPath.toString(), fileName);

		path = path.normalize();

		if (!path.startsWith(directoryPath)) {
			throw new PrincipalException(
				"Log file " + path + " is outside of " + directoryPath);
		}

		File file = path.toFile();

		if (!file.exists() || !file.isFile()) {
			throw new FileNotFoundException(
				"Unable to get the log file " + fileName + " of source " +
					logSource.getKey());
		}

		return file;
	}

	private PermissionChecker _getPermissionChecker(
			HttpServletRequest httpServletRequest)
		throws Exception {

		User user = _portal.getUser(httpServletRequest);

		if (user == null) {
			throw new PrincipalException.MustBeAuthenticated(0);
		}

		return _permissionCheckerFactory.create(user);
	}

	private void _listFiles(
			HttpServletResponse httpServletResponse, LogSource logSource,
			Locale locale)
		throws Exception {

		JSONArray filesJSONArray = _jsonFactory.createJSONArray();

		File[] files = logSource.getDirectory(
		).listFiles(
			file -> file.isFile() && !file.isHidden()
		);

		if (files != null) {
			Arrays.sort(
				files,
				Comparator.comparingLong(
					File::lastModified
				).reversed(
				).thenComparing(
					Comparator.comparing(
						File::getName
					).reversed()
				));

			Format format = FastDateFormatFactoryUtil.getSimpleDateFormat(
				"yyyy-MM-dd HH:mm", locale);

			for (File file : files) {
				filesJSONArray.put(
					_jsonFactory.createJSONObject(
					).put(
						"fileName", file.getName()
					).put(
						"fileSize",
						_language.formatStorageSize(file.length(), locale)
					).put(
						"lastModified",
						format.format(new Date(file.lastModified()))
					));
			}
		}

		_write(
			httpServletResponse,
			_jsonFactory.createJSONObject(
			).put(
				"files", filesJSONArray
			).put(
				"label", logSource.getLabel()
			).put(
				"sourceKey", logSource.getKey()
			));
	}

	private void _listSources(
			HttpServletResponse httpServletResponse,
			PermissionChecker permissionChecker, Locale locale)
		throws Exception {

		JSONArray sourcesJSONArray = _jsonFactory.createJSONArray();

		List<LogSource> logSources = _logSourceProvider.getLogSources(
			permissionChecker, locale);

		for (LogSource logSource : logSources) {
			LogSource.Type type = logSource.getType();

			sourcesJSONArray.put(
				_jsonFactory.createJSONObject(
				).put(
					"key", logSource.getKey()
				).put(
					"label", logSource.getLabel()
				).put(
					"type", type.name()
				));
		}

		_write(
			httpServletResponse,
			_jsonFactory.createJSONObject(
			).put(
				"sources", sourcesJSONArray
			));
	}

	private void _read(
			HttpServletResponse httpServletResponse, File file, Locale locale)
		throws Exception {

		long maxPreviewSize = _logSourceProvider.getMaxPreviewSize();

		long fileLength = file.length();

		long offset = Math.max(0, fileLength - maxPreviewSize);

		String content = null;

		try (RandomAccessFile randomAccessFile = new RandomAccessFile(
				file, "r")) {

			randomAccessFile.seek(offset);

			byte[] bytes = new byte[(int)(fileLength - offset)];

			randomAccessFile.readFully(bytes);

			content = new String(bytes, StandardCharsets.UTF_8);
		}

		boolean truncated = offset > 0;

		if (truncated) {

			// Drop the partial first line so that the preview always starts on
			// a log entry boundary

			int index = content.indexOf('\n');

			if (index >= 0) {
				content = content.substring(index + 1);
			}
		}

		_write(
			httpServletResponse,
			_jsonFactory.createJSONObject(
			).put(
				"content", content
			).put(
				"fileName", file.getName()
			).put(
				"fileSize", _language.formatStorageSize(fileLength, locale)
			).put(
				"previewSize",
				_language.formatStorageSize(fileLength - offset, locale)
			).put(
				"truncated", truncated
			));
	}

	private void _write(
			HttpServletResponse httpServletResponse, JSONObject jsonObject)
		throws IOException {

		httpServletResponse.setContentType("application/json");
		httpServletResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
		httpServletResponse.setStatus(HttpServletResponse.SC_OK);

		ServletResponseUtil.write(
			httpServletResponse, jsonObject.toString());
	}

	private static final Log _log = LogFactoryUtil.getLog(
		ServerLogsServlet.class);

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private Language _language;

	@Reference
	private LogSourceProvider _logSourceProvider;

	@Reference
	private MimeTypes _mimeTypes;

	@Reference
	private PermissionCheckerFactory _permissionCheckerFactory;

	@Reference
	private Portal _portal;

}
