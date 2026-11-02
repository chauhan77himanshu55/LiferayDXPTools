package com.liferay.server.logs.web.internal.application.list;

import com.liferay.application.list.BasePanelApp;
import com.liferay.application.list.PanelApp;
import com.liferay.application.list.constants.PanelCategoryKeys;
import com.liferay.portal.kernel.model.Portlet;
import com.liferay.server.logs.web.constants.ServerLogsPortletKeys;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Places the portlet in Control Panel &rarr; Security, at the slot Liferay uses
 * for its own Server Logs entry.
 *
 * @author Liferay DXP AI Copilot
 */
@Component(
	property = {
		"panel.app.order:Integer=700",
		"panel.category.key=" + PanelCategoryKeys.CONTROL_PANEL_SECURITY
	},
	service = PanelApp.class
)
public class ServerLogsPanelApp extends BasePanelApp {

	@Override
	public Portlet getPortlet() {
		return _portlet;
	}

	@Override
	public String getPortletId() {
		return ServerLogsPortletKeys.SERVER_LOGS;
	}

	@Reference(
		target = "(jakarta.portlet.name=" + ServerLogsPortletKeys.SERVER_LOGS + ")"
	)
	private Portlet _portlet;

}
