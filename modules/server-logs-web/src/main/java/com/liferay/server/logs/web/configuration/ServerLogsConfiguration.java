package com.liferay.server.logs.web.configuration;

import aQute.bnd.annotation.metatype.Meta;

import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;

/**
 * Every log source the Server Logs portlet exposes is discovered at runtime, so
 * no code change is needed when a new logging folder appears under
 * {@code ${liferay.home}/logs}. This configuration only tunes that discovery.
 *
 * @author Liferay DXP AI Copilot
 */
@ExtendedObjectClassDefinition(
	category = "infrastructure", scope = ExtendedObjectClassDefinition.Scope.SYSTEM
)
@Meta.OCD(
	id = "com.liferay.server.logs.web.configuration.ServerLogsConfiguration",
	localization = "content/Language", name = "server-logs-configuration-name"
)
public interface ServerLogsConfiguration {

	@Meta.AD(
		deflt = "true", description = "company-logs-enabled-help",
		name = "company-logs-enabled", required = false
	)
	public boolean companyLogsEnabled();

	@Meta.AD(
		deflt = "companies", description = "excluded-folder-names-help",
		name = "excluded-folder-names", required = false
	)
	public String[] excludedFolderNames();

	@Meta.AD(
		description = "folder-label-overrides-help",
		name = "folder-label-overrides", required = false
	)
	public String[] folderLabelOverrides();

	@Meta.AD(
		deflt = "", description = "logs-directory-help", name = "logs-directory",
		required = false
	)
	public String logsDirectory();

	@Meta.AD(
		deflt = "1048576", description = "max-preview-size-help",
		name = "max-preview-size", required = false
	)
	public long maxPreviewSize();

	@Meta.AD(
		deflt = "true", description = "root-logs-enabled-help",
		name = "root-logs-enabled", required = false
	)
	public boolean rootLogsEnabled();

	@Meta.AD(
		deflt = "true", description = "server-sources-omniadmin-only-help",
		name = "server-sources-omniadmin-only", required = false
	)
	public boolean serverSourcesOmniadminOnly();

}
