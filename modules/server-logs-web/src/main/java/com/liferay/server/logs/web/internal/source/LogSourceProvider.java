package com.liferay.server.logs.web.internal.source;

import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.log4j.Log4JUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.ResourceBundleUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.server.logs.web.configuration.ServerLogsConfiguration;
import com.liferay.server.logs.web.constants.ServerLogsConstants;

import java.io.File;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

/**
 * Discovers the log sources a given user may browse: the per company log
 * directories Liferay already manages, the root <code>logs</code> directory and
 * every sub folder of it that is not excluded.
 *
 * <p>
 * Only public kernel API is used so that the module keeps working across
 * Liferay upgrades.
 * </p>
 *
 * @author Liferay DXP AI Copilot
 */
@Component(
	configurationPid = "com.liferay.server.logs.web.configuration.ServerLogsConfiguration",
	service = LogSourceProvider.class
)
public class LogSourceProvider {

	public File getLogsDirectory() {
		String logsDirectory = _serverLogsConfiguration.logsDirectory();

		if (Validator.isNotNull(logsDirectory)) {
			File file = new File(logsDirectory);

			if (file.isDirectory()) {
				return file;
			}

			if (_log.isWarnEnabled()) {
				_log.warn(
					"Configured logs directory " + logsDirectory +
						" does not exist, falling back to the Liferay home");
			}
		}

		File derivedFile = _deriveLogsDirectoryFromLog4j();

		if (derivedFile != null) {
			return derivedFile;
		}

		return new File(_getLiferayHome(), "logs");
	}

	public LogSource getLogSource(
			PermissionChecker permissionChecker, Locale locale, String key)
		throws Exception {

		for (LogSource logSource : getLogSources(permissionChecker, locale)) {
			if (key.equals(logSource.getKey())) {
				return logSource;
			}
		}

		throw new PrincipalException(
			"User " + permissionChecker.getUserId() +
				" cannot access the log source " + key);
	}

	public List<LogSource> getLogSources(
			PermissionChecker permissionChecker, Locale locale)
		throws Exception {

		List<LogSource> logSources = new ArrayList<>();

		if (_serverLogsConfiguration.companyLogsEnabled()) {
			_addCompanyLogSources(permissionChecker, locale, logSources);
		}

		if (_canAccessServerLogSources(permissionChecker)) {
			_addServerLogSources(locale, logSources);
		}

		return logSources;
	}

	public long getMaxPreviewSize() {
		long maxPreviewSize = _serverLogsConfiguration.maxPreviewSize();

		if (maxPreviewSize <= 0) {
			return _DEFAULT_MAX_PREVIEW_SIZE;
		}

		return maxPreviewSize;
	}

	@Activate
	@Modified
	protected void activate(Map<String, Object> properties) {
		_serverLogsConfiguration = ConfigurableUtil.createConfigurable(
			ServerLogsConfiguration.class, properties);
	}

	private void _addCompanyLogSource(
		Company company, Locale locale, List<LogSource> logSources) {

		File directory = _getCompanyLogDirectory(company.getCompanyId());

		if (directory == null) {
			return;
		}

		logSources.add(
			new LogSource(
				ServerLogsConstants.SOURCE_KEY_PREFIX_COMPANY +
					company.getCompanyId(),
				_language.format(
					_getResourceBundle(locale), "liferay-x", company.getWebId(),
					false),
				LogSource.Type.COMPANY, directory, company.getCompanyId()));
	}

	private void _addCompanyLogSources(
			PermissionChecker permissionChecker, Locale locale,
			List<LogSource> logSources)
		throws Exception {

		if (permissionChecker.isOmniadmin()) {
			_companyLocalService.forEachCompany(
				company -> _addCompanyLogSource(company, locale, logSources));
		}
		else if (permissionChecker.isCompanyAdmin()) {
			_addCompanyLogSource(
				_companyLocalService.getCompany(
					permissionChecker.getCompanyId()),
				locale, logSources);
		}
	}

	private void _addServerLogSources(
		Locale locale, List<LogSource> logSources) {

		File logsDirectory = getLogsDirectory();

		if (!logsDirectory.isDirectory()) {
			return;
		}

		if (_serverLogsConfiguration.rootLogsEnabled() &&
			_hasRegularFiles(logsDirectory)) {

			logSources.add(
				new LogSource(
					ServerLogsConstants.SOURCE_KEY_ROOT,
					_language.get(
						_getResourceBundle(locale), "liferay-server"),
					LogSource.Type.ROOT, logsDirectory, 0));
		}

		File[] files = logsDirectory.listFiles(File::isDirectory);

		if (files == null) {
			return;
		}

		Arrays.sort(files, Comparator.comparing(File::getName));

		Set<String> excludedFolderNames = _getExcludedFolderNames();
		Map<String, String> folderLabels = _getFolderLabelOverrides();

		for (File file : files) {
			String name = file.getName();

			if (file.isHidden() ||
				excludedFolderNames.contains(StringUtil.toLowerCase(name))) {

				continue;
			}

			logSources.add(
				new LogSource(
					ServerLogsConstants.SOURCE_KEY_PREFIX_FOLDER + name,
					folderLabels.getOrDefault(
						StringUtil.toLowerCase(name), _toLabel(name)),
					LogSource.Type.FOLDER, file, 0));
		}
	}

	private boolean _canAccessServerLogSources(
		PermissionChecker permissionChecker) {

		if (permissionChecker.isOmniadmin()) {
			return true;
		}

		if (_serverLogsConfiguration.serverSourcesOmniadminOnly()) {
			return false;
		}

		return permissionChecker.isCompanyAdmin();
	}

	private File _deriveLogsDirectoryFromLog4j() {
		try {
			File companyLogDirectory = Log4JUtil.getCompanyLogDirectory(
				_portal.getDefaultCompanyId());

			File companiesDirectory = companyLogDirectory.getParentFile();

			if (companiesDirectory == null) {
				return null;
			}

			File logsDirectory = companiesDirectory.getParentFile();

			if ((logsDirectory != null) && logsDirectory.isDirectory()) {
				return logsDirectory;
			}
		}
		catch (Throwable throwable) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"Unable to derive the logs directory from the Log4j " +
						"configuration",
					throwable);
			}
		}

		return null;
	}

	private File _getCompanyLogDirectory(long companyId) {
		File file = null;

		try {
			file = Log4JUtil.getCompanyLogDirectory(companyId);
		}
		catch (Throwable throwable) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"Unable to get the log directory of company " + companyId,
					throwable);
			}
		}

		if ((file == null) || !file.isDirectory()) {
			file = new File(
				new File(getLogsDirectory(), "companies"),
				String.valueOf(companyId));
		}

		if (!file.isDirectory()) {
			return null;
		}

		return file;
	}

	private Set<String> _getExcludedFolderNames() {
		Set<String> excludedFolderNames = new LinkedHashSet<>();

		String[] names = _serverLogsConfiguration.excludedFolderNames();

		if (names != null) {
			for (String name : names) {
				if (Validator.isNotNull(name)) {
					excludedFolderNames.add(
						StringUtil.toLowerCase(StringUtil.trim(name)));
				}
			}
		}

		return excludedFolderNames;
	}

	private Map<String, String> _getFolderLabelOverrides() {
		Map<String, String> folderLabels = new HashMap<>();

		String[] folderLabelOverrides =
			_serverLogsConfiguration.folderLabelOverrides();

		if (folderLabelOverrides == null) {
			return folderLabels;
		}

		for (String folderLabelOverride : folderLabelOverrides) {
			if (Validator.isNull(folderLabelOverride)) {
				continue;
			}

			int index = folderLabelOverride.indexOf('=');

			if (index <= 0) {
				continue;
			}

			folderLabels.put(
				StringUtil.toLowerCase(
					StringUtil.trim(folderLabelOverride.substring(0, index))),
				StringUtil.trim(folderLabelOverride.substring(index + 1)));
		}

		return folderLabels;
	}

	private ResourceBundle _getResourceBundle(Locale locale) {
		return ResourceBundleUtil.getBundle(
			"content.Language", locale, LogSourceProvider.class);
	}

	private String _getLiferayHome() {
		String liferayHome = PropsUtil.get(PropsKeys.LIFERAY_HOME);

		if (Validator.isNull(liferayHome)) {
			liferayHome = System.getProperty("liferay.home");
		}

		if (Validator.isNull(liferayHome)) {
			liferayHome = System.getProperty("user.dir");
		}

		return liferayHome;
	}

	private boolean _hasRegularFiles(File directory) {
		File[] files = directory.listFiles(File::isFile);

		if ((files == null) || (files.length == 0)) {
			return false;
		}

		return true;
	}

	private String _toLabel(String name) {
		String[] words = StringUtil.split(
			StringUtil.replace(
				name, new String[] {"-", "_"}, new String[] {" ", " "}),
			' ');

		StringBuilder sb = new StringBuilder();

		for (String word : words) {
			if (word.isEmpty()) {
				continue;
			}

			if (sb.length() > 0) {
				sb.append(' ');
			}

			sb.append(StringUtil.toUpperCase(word.substring(0, 1)));
			sb.append(word.substring(1));
		}

		if (sb.length() == 0) {
			return name;
		}

		return sb.toString();
	}

	private static final long _DEFAULT_MAX_PREVIEW_SIZE = 1024L * 1024L;

	private static final Log _log = LogFactoryUtil.getLog(
		LogSourceProvider.class);

	@Reference
	private CompanyLocalService _companyLocalService;

	@Reference
	private Language _language;

	@Reference
	private Portal _portal;

	private volatile ServerLogsConfiguration _serverLogsConfiguration;

}
