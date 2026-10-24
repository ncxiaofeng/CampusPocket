package com.ecut.campuspocket.capture

/**
 * 注入到每个页面（含 iframe）的抓包脚本。
 *
 * 只依赖 WebView 自身的 JS 环境，不需要 CA 证书或 root。
 * 记录 fetch / XMLHttpRequest / sendBeacon 与页面错误；
 * Cookie、Authorization、密码、验证码、token、手机号等字段在写入日志前脱敏。
 */
object CaptureScript {

    const val SCRIPT: String = """
(function () {
  if (window.__ccInstalled) { return; }
  window.__ccInstalled = true;
  var bridge = window.CaptureBridge;
  if (!bridge || !bridge.log) { return; }

  var SEQ = 0;
  var MAX_REQ = 65536;
  var MAX_RES = 131072;
  var EXACT_SENSITIVE = {
    password: 1, passwd: 1, pwd: 1, smscode: 1, verifycode: 1, captcha: 1,
    authcode: 1, code: 1, token: 1, accesstoken: 1, refreshtoken: 1,
    authorization: 1, cookie: 1, ticket: 1, idcard: 1, phone: 1, mobile: 1,
    jsessionid: 1, castgc: 1, sessionid: 1, openid: 1
  };

  function normalizeKey(key) {
    return String(key == null ? '' : key).toLowerCase().replace(/[^a-z0-9]/g, '');
  }

  function isSensitive(key) {
    var normalized = normalizeKey(key);
    if (EXACT_SENSITIVE[normalized] === 1) { return true; }
    return normalized.indexOf('password') >= 0 ||
      normalized.indexOf('token') >= 0 ||
      normalized.indexOf('sessionid') >= 0 ||
      normalized.indexOf('secret') >= 0;
  }

  function isSensitiveHeader(key) {
    var normalized = normalizeKey(key);
    return normalized === 'cookie' || normalized === 'setcookie' ||
      normalized === 'authorization' || normalized === 'castgc' ||
      normalized.indexOf('token') >= 0;
  }

  function truncate(text, limit) {
    var value = String(text == null ? '' : text);
    return value.length > limit ? value.slice(0, limit) + '...[truncated]' : value;
  }

  function redactValue(value, key, depth) {
    if (isSensitive(key)) { return '***'; }
    if (depth > 6 || value == null) { return value; }
    if (typeof value === 'string') {
      var trimmed = value.trim();
      if (trimmed.length > 1 && (trimmed.charAt(0) === '{' || trimmed.charAt(0) === '[')) {
        try {
          return JSON.stringify(redactValue(JSON.parse(trimmed), key, depth + 1));
        } catch (e) { }
      }
      return value;
    }
    if (Object.prototype.toString.call(value) === '[object Array]') {
      var list = [];
      for (var i = 0; i < value.length; i++) {
        list.push(redactValue(value[i], key, depth + 1));
      }
      return list;
    }
    if (typeof value === 'object') {
      var obj = {};
      for (var k in value) {
        if (Object.prototype.hasOwnProperty.call(value, k)) {
          obj[k] = redactValue(value[k], k, depth + 1);
        }
      }
      return obj;
    }
    return value;
  }

  function redactText(text) {
    if (text == null) { return ''; }
    var raw = String(text);
    if (raw.length > 200000) { raw = raw.slice(0, 200000); }
    try {
      var parsed = JSON.parse(raw);
      return JSON.stringify(redactValue(parsed, '', 0));
    } catch (e) { }
    try {
      var params = new URLSearchParams(raw);
      var changed = false;
      var out = new URLSearchParams();
      params.forEach(function (value, key) {
        if (isSensitive(key)) { changed = true; out.append(key, '***'); }
        else { out.append(key, value); }
      });
      if (changed) { return out.toString(); }
    } catch (e) { }
    return raw;
  }

  function redactUrl(url) {
    try {
      var parsed = new URL(String(url), location.href);
      var changed = false;
      var keys = [];
      parsed.searchParams.forEach(function (value, key) { keys.push(key); });
      for (var i = 0; i < keys.length; i++) {
        if (isSensitive(keys[i])) {
          parsed.searchParams.set(keys[i], '***');
          changed = true;
        }
      }
      return changed ? parsed.href : String(url);
    } catch (e) {
      return String(url);
    }
  }

  function bodyToString(body) {
    if (body == null) { return ''; }
    if (typeof body === 'string') { return truncate(redactText(body), MAX_REQ); }
    try {
      if (body instanceof URLSearchParams) {
        return truncate(redactText(body.toString()), MAX_REQ);
      }
      if (typeof FormData !== 'undefined' && body instanceof FormData) {
        var parts = [];
        body.forEach(function (value, key) {
          parts.push(encodeURIComponent(key) + '=' +
            encodeURIComponent(isSensitive(key) ? '***' : String(value)));
        });
        return truncate(parts.join('&'), MAX_REQ);
      }
    } catch (e) { }
    if (typeof body === 'object') {
      var name = body.constructor && body.constructor.name ? body.constructor.name : 'object';
      return '[' + name + ']';
    }
    return truncate(String(body), MAX_REQ);
  }

  function headersToObj(headers) {
    var result = {};
    if (!headers) { return result; }
    try {
      if (typeof headers.forEach === 'function' && typeof headers.get === 'function') {
        headers.forEach(function (value, key) {
          result[key] = isSensitiveHeader(key) ? '***' : String(value);
        });
        return result;
      }
      if (Object.prototype.toString.call(headers) === '[object Array]') {
        for (var i = 0; i < headers.length; i++) {
          if (headers[i] && headers[i].length >= 2) {
            var key = headers[i][0];
            result[key] = isSensitiveHeader(key) ? '***' : String(headers[i][1]);
          }
        }
        return result;
      }
      for (var k in headers) {
        if (Object.prototype.hasOwnProperty.call(headers, k)) {
          result[k] = isSensitiveHeader(k) ? '***' : String(headers[k]);
        }
      }
    } catch (e) { }
    return result;
  }

  function send(payload) {
    try {
      payload.page = location.href;
      payload.ts = Date.now();
      bridge.log(JSON.stringify(payload));
    } catch (e) { }
  }

  var originalFetch = window.fetch;
  if (originalFetch) {
    window.fetch = function (input, init) {
      init = init || {};
      var id = 'f' + (++SEQ);
      var url = (typeof input === 'string') ? input : ((input && input.url) || '');
      var method = String(init.method || (input && input.method) || 'GET').toUpperCase();
      var headers = headersToObj(init.headers || (input && input.headers));
      var body = bodyToString(init.body);
      send({ t: 'req', id: id, url: redactUrl(url), method: method, headers: headers, body: body });
      return originalFetch.apply(this, arguments).then(function (response) {
        var contentType = '';
        try {
          contentType = response.headers && response.headers.get
            ? (response.headers.get('content-type') || '') : '';
        } catch (e) { }
        var clone = null;
        try { clone = response.clone(); } catch (e) { }
        if (clone && /json|text|xml|javascript|x-www-form-urlencoded/i.test(contentType)) {
          clone.text().then(function (text) {
            send({
              t: 'res', id: id, url: redactUrl(url), status: response.status,
              mime: contentType, headers: headersToObj(response.headers),
              body: truncate(redactText(text), MAX_RES)
            });
          }).catch(function () {
            send({
              t: 'res', id: id, url: redactUrl(url), status: response.status,
              mime: contentType, body: '[body unavailable]'
            });
          });
        } else if (clone) {
          send({
            t: 'res', id: id, url: redactUrl(url), status: response.status,
            mime: contentType, body: '[binary or empty]'
          });
        }
        return response;
      }).catch(function (error) {
        send({ t: 'err', id: id, url: redactUrl(url), error: String(error) });
        throw error;
      });
    };
  }

  var originalOpen = XMLHttpRequest.prototype.open;
  var originalSend = XMLHttpRequest.prototype.send;
  var originalSetHeader = XMLHttpRequest.prototype.setRequestHeader;

  XMLHttpRequest.prototype.open = function (method, url) {
    this.__cc = {
      method: String(method || 'GET').toUpperCase(),
      url: String(url || ''),
      headers: {}
    };
    return originalOpen.apply(this, arguments);
  };

  XMLHttpRequest.prototype.setRequestHeader = function (key, value) {
    if (this.__cc) {
      this.__cc.headers[key] = isSensitiveHeader(key) ? '***' : String(value);
    }
    return originalSetHeader.apply(this, arguments);
  };

  XMLHttpRequest.prototype.send = function (body) {
    var meta = this.__cc || { method: 'GET', url: '', headers: {} };
    var id = 'x' + (++SEQ);
    meta.id = id;
    send({
      t: 'req', id: id, url: redactUrl(meta.url), method: meta.method,
      headers: meta.headers, body: bodyToString(body)
    });
    var xhr = this;
    this.addEventListener('load', function () {
      var text = '';
      try {
        var type = xhr.responseType;
        if (!type || type === 'text') { text = xhr.responseText; }
        else if (type === 'json') { text = JSON.stringify(xhr.response); }
        else { text = '[binary]'; }
      } catch (e) { text = '[body unavailable]'; }
      var responseHeaders = {};
      try {
        var raw = xhr.getAllResponseHeaders() || '';
        var lines = raw.split('\r\n');
        for (var i = 0; i < lines.length; i++) {
          var index = lines[i].indexOf(':');
          if (index > 0) {
            var key = lines[i].slice(0, index).trim();
            var value = lines[i].slice(index + 1).trim();
            responseHeaders[key] = isSensitiveHeader(key) ? '***' : value;
          }
        }
      } catch (e) { }
      send({
        t: 'res', id: id, url: redactUrl(meta.url), status: xhr.status,
        mime: (xhr.getResponseHeader('content-type') || ''),
        headers: responseHeaders, body: truncate(redactText(text), MAX_RES)
      });
    });
    this.addEventListener('error', function () {
      send({ t: 'err', id: id, url: redactUrl(meta.url), error: 'XHR error' });
    });
    return originalSend.apply(this, arguments);
  };

  var originalBeacon = navigator.sendBeacon;
  if (originalBeacon) {
    navigator.sendBeacon = function (url, data) {
      send({
        t: 'req', id: 'b' + (++SEQ), url: redactUrl(url),
        method: 'BEACON', headers: {}, body: bodyToString(data)
      });
      return originalBeacon.apply(navigator, arguments);
    };
  }

  var originalConsoleError = console.error;
  var originalConsoleWarn = console.warn;
  function logConsole(level, args) {
    try {
      var parts = [];
      for (var i = 0; i < args.length; i++) {
        var item = args[i];
        if (typeof item === 'string') { parts.push(item); }
        else {
          try { parts.push(JSON.stringify(item)); }
          catch (e) { parts.push(String(item)); }
        }
      }
      send({
        t: 'console', level: level,
        body: truncate(redactText(parts.join(' ')), 20000)
      });
    } catch (e) { }
  }
  console.error = function () {
    logConsole('error', arguments);
    return originalConsoleError.apply(console, arguments);
  };
  console.warn = function () {
    logConsole('warn', arguments);
    return originalConsoleWarn.apply(console, arguments);
  };

  window.addEventListener('error', function (event) {
    send({
      t: 'err', url: redactUrl(location.href),
      error: (event.message || 'script error') + ' @ ' +
        (event.filename || '') + ':' + (event.lineno || 0)
    });
  });
  window.addEventListener('unhandledrejection', function (event) {
    send({
      t: 'err', url: redactUrl(location.href),
      error: 'unhandled rejection: ' + String(event.reason)
    });
  });
})();
"""
}
