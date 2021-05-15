function build(jsm, urlContextWithTailingSlash) {
	"use strict";
	var ctx = urlContextWithTailingSlash;
	var r = XMLHttpRequest;
	var p = Object.defineProperty;
	var euc = encodeURIComponent;
	var jp = JSON.parse;
	var rt = "responseText";

	var m = new Object();
	m.cache = {};
	m.working = [];

	var undef = function(o) {
		return typeof o === 'undefined';
	}
	function sizeOf(entity) {
		var x = new r();
		var l = -1;
		x.open('GET', ctx + entity + '/', false);
		x.onreadystatechange = function() {
			if (x.readyState == 4) {
				l = jp(x[rt]).count * 1;
			}
		}
		x.send();
		return l;
	}

	function add(x, name, value) {
		x.setRequestHeader("x-" + name, euc(value));
	}

	function loadIntoCache(url) {
		var x = new r();
		x.open('GET', url, false);
		x.onreadystatechange = function() {
			if (x.readyState == 4) {
				var l = jp(x[rt]);
				if (m.cache[l.entity] === undefined) {
					m.cache[l.entity] = new Object();
				}
				m.cache[l.entity][l.index] = l;
			}
		}
		x.send();
	}

	function tcheck(act, req, reqtn, nullable, ky) {
		if (req) {
			if (nullable && act === null) {
				return "";
			}
			if (typeof act !== reqtn) {
				return '\n  ' + ky + ' must be ' + reqtn + (nullable ? ' or null' : '') + ' but is ' + (act === null ? 'null' : "" + typeof act) + '! ';

			}
		}
		return '';
	}

	function lazy(entity, index) {
		loadIntoCache(ctx + entity + '/' + index);
		var json = m.cache[entity][index];
		var names = Object.keys(json.payload);
		var o = new Object();
		function constructProperty(entity, index, property) {
			p(o, property, {
				enumerable : true,
				configurable : true,
				get : function(e) {
					var val = m.cache[entity][index].payload[property];
					var p = jsm[entity][property];
					if (p.t == tm.ONE_TO_MANY) {
						var o = [];
						for (var i = 0; i < val.length; i++) {
							o.push(m[p.type][val[i]]);
						}
						return o;
					}
					if (p.t == tm.MANY_TO_MANY_OWNER || p.t == tm.MANY_TO_MANY_NON_OWNER) {
						var o = [];
						for (var i = 0; i < val.length; i++) {
							o.push(m[p.type][val[i]]);
						}
						o.push = function(e,cb){
							var x = new r();
							x.onreadystatechange = function() {
								if (x.readyState == 4) {
									if (cb!=null) cb(x);
								}
							}
							x.open('POST', ctx + entity + '/' + index + '/' + property + '/', cb!=null);
							x.send(e);
						}
						return o;
					}
					if (p.t == tm.REQUIRED_MANY_TO_ONE) {
						return m[p.type][val]
					}
					return val;
				},
				set : function(e) {
					var type = jsm[entity];
					var prop = type[property];
					var msg = '';
					msg += tcheck(e, prop.t == tm.REQUIRED_STRING_OR_CHAR, 'string', false, property);
					msg += tcheck(e, prop.t == tm.REQUIRED_BOOLEAN, 'boolean', false, property);
					msg += tcheck(e, prop.t == tm.REQUIRED_NUMBER, 'number', false, property);
					msg += tcheck(e, prop.t == tm.REQUIRED_BODY_DATA, 'object', false, property);
					msg += tcheck(e, prop.t == tm.OPTIONAL_STRING_OR_CHAR, 'string', true, property);
					msg += tcheck(e, prop.t == tm.OPTIONAL_BOOLEAN, 'boolean', true, property);
					msg += tcheck(e, prop.t == tm.OPTIONAL_NUMBER, 'number', true, property);
					msg += tcheck(e, prop.t == tm.OPTIONAL_BODY_DATA, 'object', true, property);
					if (msg != '')
						throw msg;
					var x = new r();
					x.onreadystatechange = function() {
						if (x.readyState == 4) {
							m.cache[entity][index].payload[property] = e;
							m.working.splice(m.working.indexOf(x));
						}
					}
					x.open('POST', ctx + entity + '/' + index + '/' + property, false);
					m.working.push(x);
					if (e == null) {
						add(x, property + '-null', true);
						x.send();
					} else {
						x.send(e);
					}

				}
			});
		}
		for (var i = 0; i < names.length; i++) {
			constructProperty(entity, index, names[i]);
		}
		// construct virtual properties
		var mod = jsm[entity];
		names = Object.keys(mod);
		for (var i = 0; i < names.length; i++) {
			var name = names[i];
			var virt = mod[name].t == tm.OPTIONAL_BODY_DATA || mod[name].t == tm.REQUIRED_BODY_DATA || mod[name].t == tm.OPTIONAL_BODY_DATA_BLOB || mod[name].t == tm.REQUIRED_BODY_DATA_BLOB;
			if (virt) {
				var constructLazyVirtual=function(o, name, index) {
					p(o, name, {
						enumerable : true,
						configurable : true,
						get : function(e) {
							var x = new r();
							x.open("GET", urlContextWithTailingSlash + entity + '/' + index + '/' + name, false);
							x.send();
							var json = jp(x[rt]);
							return json;
						},
						set : function(e) {
							var x = new r();
							x.open("POST", urlContextWithTailingSlash + entity + '/' + index + '/' + name, false);
							x.send(new Int8Array(e));
							return e;
						}
					});
				}
				constructLazyVirtual(o, name, index);
			}
		}

		o.remove = function(cb) {
			var x = new r();
			x.onreadystatechange = function() {
				if (x.readyState == 4) {
					if (cb != null) {
						var val = x;
						if (x.status == 428) {
							val = 'Can not remove ' + entity + ' because preconditions failed!';
							console.warn(val);
							val = new Error(val);
						}
						cb(x);
					}
				}
			}
			x.open('DELETE', urlContextWithTailingSlash + '/' + entity + '/' + index, cb != null);
			x.send();
			if (cb == null && x.status == 428)
				throw 'Preconditions failed';
			return true;
		};
		return o;
	}
	function constructLazy(ob, i, key) {
		p(ob, i, {
			enumerable : false,
			configurable : false,
			get : function() {
				return lazy(key, i);
			}
		})
	}

	function construct(ob, key) {
		var t = jsm[key];
		var fields = Object.keys(t);
		function check(data) {
			var required = [];
			var optional = [];
			var msg = '';
			for (var m = 0; m < fields.length; m++) {
				var c = t[fields[m]];
				var cn = fields[m];
				if (c.t == tm.OPTIONAL_NUMBER || c.t == tm.OPTIONAL_BOOLEAN || c.t == tm.OPTIONAL_STRING_OR_CHAR || c.t == tm.OPTIONAL_BODY_DATA || c.t == tm.OPTIONAL_BODY_DATA_BLOB
						|| c.t == tm.OPTIONAL_MANY_TO_ONE) {
					optional.push(cn);
				}
				if (c.t == tm.REQUIRED_NUMBER || c.t == tm.REQUIRED_BOOLEAN || c.t == tm.REQUIRED_STRING_OR_CHAR || c.t == tm.REQUIRED_BODY_DATA || c.t == tm.REQUIRED_BODY_DATA_BLOB
						|| c.t == tm.REQUIRED_MANY_TO_ONE) {
					required.push(cn);
				}
			}
			var keys = Object.keys(data);
			for (var k = 0; k < keys.length; k++) {
				var ky = keys[k];
				if (undef(t[ky])) {
					msg += '\n  ' + ky + ' is unknown! ';
					continue;
				}
				msg += tcheck(data[ky], t[ky].t == tm.REQUIRED_BOOLEAN, 'boolean', false, ky);
				msg += tcheck(data[ky], t[ky].t == tm.OPTIONAL_BOOLEAN, 'boolean', true, ky);
				msg += tcheck(data[ky], t[ky].t == tm.REQUIRED_NUMBER, 'number', false, ky);
				msg += tcheck(data[ky], t[ky].t == tm.OPTIONAL_NUMBER, 'number', true, ky);
				msg += tcheck(data[ky], t[ky].t == tm.REQUIRED_STRING_OR_CHAR, 'string', false, ky);
				msg += tcheck(data[ky], t[ky].t == tm.OPTIONAL_STRING_OR_CHAR, 'string', true, ky);
				msg += tcheck(data[ky], t[ky].t == tm.REQUIRED_BODY_DATA, 'object', false, ky);
				msg += tcheck(data[ky], t[ky].t == tm.OPTIONAL_BODY_DATA, 'object', true, ky);
				msg += tcheck(data[ky], t[ky].t == tm.OPTIONAL_MANY_TO_ONE, 'number', true, ky);
			}
			for (var k = 0; k < required.length; k++) {
				var ky = required[k];
				if (undef(data[ky])) {
					msg += '\n  ' + ky + ' is required! ';
				}
			}
			for (var k = 0; k < optional.length; k++) {
				var ky = optional[k];
				if (undef(data[ky])) {
					msg += '\n  ' + ky + ' is required, may be null! ';
				}
			}
			if (msg != '') {
				throw key + ": " + msg;
			}
		}

		var tableDescriptor = {
			enumerable : true,
			configurable : false,
			get : function(e) {
				var size = sizeOf(key);
				var ob = new Array(size);
				function push(data, cb, additional) {
					var pushInternal = function(data, cb, additional){
						var val = null;
						check(data);
						var x = new r();
						var xurl = urlContextWithTailingSlash + key + '/';
						if(typeof additional != 'undefined') {
							xurl = xurl + '?' + additional;
						}
						x.open('PUT', xurl, arguments.length > 1);
						x.onreadystatechange = function() {
							if (x.readyState == 4) {
								var json = jp(x[rt]);
								if (json.error) {
									console.warn("Server error while adding a '" + key + "': " + json.error);
									alert(json.error);
								}
								if (typeof json.id === 'number') {
									val = json.id;
									if (typeof cb === 'function') {
										cb(json);
									}
								}
							}
						}
						var body = null;
						var hasBody = false;
						for (var i = 0; i < fields.length; i++) {
							var name = fields[i];
							var value = data[name];
							var f = t[name];
							if (f.t == tm.REQUIRED_BODY_DATA || f.t == tm.OPTIONAL_BODY_DATA||f.t == tm.REQUIRED_BODY_DATA_BLOB || f.t == tm.OPTIONAL_BODY_DATA_BLOB) {
								hasBody = true;
								var d = data[fields[i]];
								if (d instanceof File) {
									var fr = new FileReader();
									fr.onload = function (res){
										var newData = Object.assign({},data,{});
										newData[fields[i]] = new Int8Array(res.target.result);
										pushInternal(newData, cb);
									}
									fr.readAsArrayBuffer(d);
									return;
								} else if (d instanceof Int8Array) {
									body = d;
								} else {
									body = new Int8Array(d);
								}
							} else {
								if (value === null) {
									add(x, name + "-null", true);
								}
								add(x, name, value);
							}
						}
						if (hasBody) {
							x.send(body);
						} else {
							x.send();
						}
						return val;
					}
					return pushInternal(data, cb, additional); 
				}
				p(ob, 'push', {
					enumerable : false,
					configurable : false,
					get : function() {
						return push;
					}
				});
				ob.load = function(id, cb) {
					var x = new r();
					var result;
					x.onreadystatechange = function() {
						if (x.readyState == 4) {
							result = jp(x[rt]);
							if (result.error != null) {
								console.warn(result.error);
							} else {
								result = result.index;
							}
						}
					}
					x.open('GET', urlContextWithTailingSlash + key + '/?id=' + id, cb != null);
					x.send();
					if (cb == null) {
						return result;
					}
				}
				for (var i = 0; i < size; i++) {
					constructLazy(ob, i, key);
				}
				return ob;
			},
			set : function(v) {
			}
		}
		p(ob, key, tableDescriptor);
	}
	var keys = Object.keys(jsm);
	for (var i = 0; i < keys.length; i++) {
		var key = keys[i];
		construct(m, key);
	}
	return m;
}