/**
 * Server Logs - vanilla browser code on purpose.
 *
 * The module deliberately avoids React, the Liferay npm bundler and any
 * @liferay/* package so that it keeps working after a Liferay upgrade without a
 * frontend rebuild.
 */

(function () {
	'use strict';

	if (window.ServerLogsApp) {
		return;
	}

	var STORAGE_KEY = 'com.liferay.server.logs.web.sourceKey';

	var SVG_NAMESPACE = 'http://www.w3.org/2000/svg';

	function createElement(tagName, className, text) {
		var node = document.createElement(tagName);

		if (className) {
			node.className = className;
		}

		if (text !== undefined && text !== null) {
			node.textContent = text;
		}

		return node;
	}

	function createIcon(spritemap, symbol) {
		var svg = document.createElementNS(SVG_NAMESPACE, 'svg');

		svg.setAttribute('class', 'lexicon-icon lexicon-icon-' + symbol);
		svg.setAttribute('focusable', 'false');
		svg.setAttribute('role', 'presentation');

		var use = document.createElementNS(SVG_NAMESPACE, 'use');

		use.setAttribute('href', spritemap + '#' + symbol);

		svg.appendChild(use);

		return svg;
	}

	function ServerLogs(config) {
		this._config = config;
		this._strings = config.strings || {};

		this._contentNode = document.getElementById(
			config.namespace + 'content'
		);
		this._selectNode = document.getElementById(config.namespace + 'source');
	}

	ServerLogs.prototype = {
		start: function () {
			if (!this._contentNode || !this._selectNode) {
				return;
			}

			var instance = this;

			this._selectNode.addEventListener('change', function () {
				var sourceKey = instance._selectNode.value;

				instance._rememberSourceKey(sourceKey);
				instance._loadFiles(sourceKey);
			});

			this._loadSources();
		},

		_buildURL: function (parameters) {
			var query = [];

			Object.keys(parameters).forEach(function (name) {
				var value = parameters[name];

				if (value === undefined || value === null || value === '') {
					return;
				}

				query.push(
					encodeURIComponent(name) + '=' + encodeURIComponent(value)
				);
			});

			if (!query.length) {
				return this._config.serviceURL;
			}

			return this._config.serviceURL + '?' + query.join('&');
		},

		_fetchJSON: function (parameters) {
			return fetch(this._buildURL(parameters), {
				credentials: 'same-origin',
				headers: {Accept: 'application/json'},
			}).then(function (response) {
				if (!response.ok) {
					throw new Error('HTTP ' + response.status);
				}

				return response.json();
			});
		},

		_loadFiles: function (sourceKey) {
			var instance = this;

			if (!sourceKey) {
				return;
			}

			this._setLoading();

			this._fetchJSON({sourceKey: sourceKey})
				.then(function (data) {
					instance._renderFiles(sourceKey, data.files || []);
				})
				.catch(function (error) {
					instance._renderError(error);
				});
		},

		_loadPreview: function (sourceKey, fileName) {
			var instance = this;

			this._setLoading();

			this._fetchJSON({fileName: fileName, sourceKey: sourceKey})
				.then(function (data) {
					instance._renderPreview(sourceKey, data);
				})
				.catch(function (error) {
					instance._renderError(error);
				});
		},

		_loadSources: function () {
			var instance = this;

			this._setLoading();

			this._fetchJSON({})
				.then(function (data) {
					var sources = data.sources || [];

					if (!sources.length) {
						instance._selectNode.disabled = true;

						instance._renderMessage(instance._strings.emptySources);

						return;
					}

					var rememberedSourceKey = instance._rememberedSourceKey();
					var selectedSourceKey = sources[0].key;

					sources.forEach(function (source) {
						var option = createElement(
							'option',
							null,
							source.label
						);

						option.value = source.key;

						instance._selectNode.appendChild(option);

						if (source.key === rememberedSourceKey) {
							selectedSourceKey = source.key;
						}
					});

					instance._selectNode.value = selectedSourceKey;

					instance._loadFiles(selectedSourceKey);
				})
				.catch(function (error) {
					instance._renderError(error);
				});
		},

		_rememberSourceKey: function (sourceKey) {
			try {
				window.sessionStorage.setItem(STORAGE_KEY, sourceKey);
			}
			catch (error) {

				// Storage can be unavailable in private browsing modes

			}
		},

		_rememberedSourceKey: function () {
			try {
				return window.sessionStorage.getItem(STORAGE_KEY);
			}
			catch (error) {
				return null;
			}
		},

		_renderError: function (error) {
			if (window.console) {
				window.console.error(error);
			}

			this._renderMessage(this._strings.error, 'alert alert-danger');
		},

		_renderFiles: function (sourceKey, files) {
			var instance = this;

			if (!files.length) {
				this._renderMessage(this._strings.emptyFiles);

				return;
			}

			var listNode = createElement('ul', 'list-group mt-3');

			files.forEach(function (file, index) {
				listNode.appendChild(
					instance._renderFileItem(sourceKey, file, index === 0)
				);
			});

			this._replaceContent(listNode);
		},

		_renderFileItem: function (sourceKey, file, newest) {
			var instance = this;

			var itemNode = createElement(
				'li',
				'list-group-item list-group-item-flex'
			);

			var iconNode = createElement('div', 'autofit-col');

			iconNode.appendChild(
				createIcon(this._config.iconSpritemap, 'document')
			);

			itemNode.appendChild(iconNode);

			var mainNode = createElement('div', 'autofit-col autofit-col-expand');

			var titleNode = createElement('div', 'list-group-title');

			var linkNode = createElement('a', null, file.fileName);

			linkNode.href = '#';

			linkNode.addEventListener('click', function (event) {
				event.preventDefault();

				instance._loadPreview(sourceKey, file.fileName);
			});

			titleNode.appendChild(linkNode);

			if (newest && this._strings.newest) {
				var labelNode = createElement(
					'span',
					'label label-info ml-2',
					this._strings.newest
				);

				titleNode.appendChild(labelNode);
			}

			mainNode.appendChild(titleNode);

			var subtitle = file.fileSize || '';

			if (file.lastModified) {
				subtitle = subtitle
					? subtitle + ' \u00b7 ' + file.lastModified
					: file.lastModified;
			}

			mainNode.appendChild(
				createElement('div', 'list-group-subtitle', subtitle)
			);

			itemNode.appendChild(mainNode);

			var actionsNode = createElement('div', 'autofit-col');

			actionsNode.appendChild(
				this._renderIconButton('view', this._strings.view, function () {
					instance._loadPreview(sourceKey, file.fileName);
				})
			);

			actionsNode.appendChild(
				this._renderIconButton(
					'download',
					this._strings.download,
					function () {
						instance._download(sourceKey, file.fileName);
					}
				)
			);

			itemNode.appendChild(actionsNode);

			return itemNode;
		},

		_download: function (sourceKey, fileName) {
			window.open(
				this._buildURL({
					action: 'download',
					fileName: fileName,
					sourceKey: sourceKey,
				}),
				'_blank'
			);
		},

		_renderIconButton: function (symbol, title, onClick) {
			var buttonNode = createElement(
				'button',
				'btn btn-unstyled ml-2 quick-action-item'
			);

			buttonNode.type = 'button';

			if (title) {
				buttonNode.setAttribute('aria-label', title);
				buttonNode.setAttribute('title', title);
			}

			buttonNode.appendChild(
				createIcon(this._config.iconSpritemap, symbol)
			);

			buttonNode.addEventListener('click', onClick);

			return buttonNode;
		},

		_renderMessage: function (message, className) {
			this._replaceContent(
				createElement('div', className || 'text-secondary mt-3', message)
			);
		},

		_renderPreview: function (sourceKey, data) {
			var instance = this;

			var sectionNode = createElement('section');

			var headerNode = createElement(
				'div',
				'align-items-center d-flex justify-content-between mb-2'
			);

			var titleNode = createElement('h1', 'align-items-center d-flex mb-0');

			titleNode.appendChild(
				this._renderIconButton(
					'angle-left',
					this._strings.back,
					function () {
						instance._loadFiles(sourceKey);
					}
				)
			);

			titleNode.appendChild(
				createElement('span', 'ml-2', data.fileName)
			);

			headerNode.appendChild(titleNode);

			headerNode.appendChild(
				this._renderIconButton(
					'download',
					this._strings.download,
					function () {
						instance._download(sourceKey, data.fileName);
					}
				)
			);

			sectionNode.appendChild(headerNode);

			if (data.truncated && this._strings.truncated) {
				sectionNode.appendChild(
					createElement(
						'div',
						'alert alert-info',
						this._strings.truncated
							.replace('{0}', data.previewSize)
							.replace('{1}', data.fileSize)
					)
				);
			}

			var preNode = createElement(
				'pre',
				'logs-container px-4 py-3 text-white'
			);

			preNode.textContent = data.content || '';

			var previewNode = createElement('div', 'bg-dark');

			previewNode.appendChild(preNode);

			sectionNode.appendChild(previewNode);

			this._replaceContent(sectionNode);
		},

		_replaceContent: function (node) {
			this._contentNode.textContent = '';
			this._contentNode.appendChild(node);
		},

		_setLoading: function () {
			var loadingNode = createElement(
				'span',
				'loading-animation loading-animation-sm'
			);

			loadingNode.setAttribute('aria-hidden', 'true');

			this._replaceContent(loadingNode);
		},
	};

	window.ServerLogsApp = {
		init: function (config) {
			var serverLogs = new ServerLogs(config);

			serverLogs.start();

			return serverLogs;
		},
	};
})();
