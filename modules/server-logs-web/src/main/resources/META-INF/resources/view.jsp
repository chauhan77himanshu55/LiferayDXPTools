<%@ include file="/init.jsp" %>

<%
ResourceBundle serverLogsResourceBundle = ResourceBundleUtil.getBundle("content.Language", locale, getClass());

JSONObject configJSONObject = JSONFactoryUtil.createJSONObject();

configJSONObject.put(
	"iconSpritemap", themeDisplay.getPathThemeImages() + "/clay/icons.svg"
).put(
	"namespace", liferayPortletResponse.getNamespace()
).put(
	"serviceURL", PortalUtil.getPathModule() + "/server-logs"
).put(
	"strings",
	JSONFactoryUtil.createJSONObject(
	).put(
		"back", LanguageUtil.get(locale, "back")
	).put(
		"download", LanguageUtil.get(locale, "download")
	).put(
		"emptyFiles",
		LanguageUtil.get(
			serverLogsResourceBundle,
			"no-logs-are-available-for-this-source")
	).put(
		"emptySources",
		LanguageUtil.get(
			serverLogsResourceBundle, "no-log-sources-are-available")
	).put(
		"error",
		LanguageUtil.get(serverLogsResourceBundle, "unable-to-load-the-logs")
	).put(
		"newest", LanguageUtil.get(locale, "newest")
	).put(
		"truncated",
		LanguageUtil.get(
			serverLogsResourceBundle,
			"showing-the-last-x-of-x-download-the-file-to-read-it-in-full")
	).put(
		"view", LanguageUtil.get(locale, "view")
	)
);
%>

<div class="bg-white container mt-4 p-4 server-logs-root" id="<portlet:namespace />root">
	<h1 class="mb-4"><%= HtmlUtil.escape(LanguageUtil.get(locale, "logs")) %></h1>

	<div class="form-group server-logs-source">
		<label class="control-label" for="<portlet:namespace />source"><%= HtmlUtil.escape(LanguageUtil.get(serverLogsResourceBundle, "log-source")) %></label>

		<select class="form-control" id="<portlet:namespace />source"></select>
	</div>

	<div id="<portlet:namespace />content">
		<span aria-hidden="true" class="loading-animation loading-animation-sm"></span>
	</div>
</div>

<style type="text/css">
	.server-logs-root .server-logs-source {
		max-width: 28rem;
	}

	/* Mirrors the stock viewer: the dark box is a fixed height scroll region, so
	   long log lines never widen the page. */

	.server-logs-root .logs-container {
		margin: 0;
		max-height: 800px;
		max-width: 100%;
		overflow: auto;
		white-space: pre;
	}
</style>

<script src="<%= PortalUtil.getPathModule() %>/server-logs-web/js/server_logs.js" type="text/javascript"></script>

<script type="text/javascript">
	(function () {
		var config = <%= configJSONObject.toString() %>;

		function start() {
			if (window.ServerLogsApp) {
				window.ServerLogsApp.init(config);
			}
			else {
				setTimeout(start, 50);
			}
		}

		start();
	})();
</script>
