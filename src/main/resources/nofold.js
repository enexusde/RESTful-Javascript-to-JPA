function build(schema, urlContextWithTailingSlash) {
	"use strict";
	var urlContext = urlContextWithTailingSlash;
	var r = XMLHttpRequest;

	var memory = new Object();
	memory.cache = {};
	memory.working = [];

	var undef = function(o) {
		return typeof o === 'undefined';
	}
	function addMessage(errMsg, entity, entityTypeMap, fieldName) {
		var fieldValue = entity[fieldName];
		errMsg += typeCheck(fieldValue, entityTypeMap[fieldName].t == tm.REQUIRED_BOOLEAN, 'boolean', false, fieldName);
		errMsg += typeCheck(fieldValue, entityTypeMap[fieldName].t == tm.OPTIONAL_BOOLEAN, 'boolean', true, fieldName);
		errMsg += typeCheck(fieldValue, entityTypeMap[fieldName].t == tm.REQUIRED_NUMBER, 'number', false, fieldName);
		errMsg += typeCheck(fieldValue, entityTypeMap[fieldName].t == tm.OPTIONAL_NUMBER, 'number', true, fieldName);
		errMsg += typeCheck(fieldValue, entityTypeMap[fieldName].t == tm.REQUIRED_STRING_OR_CHAR, 'string', false, fieldName);
		errMsg += typeCheck(fieldValue, entityTypeMap[fieldName].t == tm.OPTIONAL_STRING_OR_CHAR, 'string', true, fieldName);
		errMsg += typeCheck(fieldValue, entityTypeMap[fieldName].t == tm.REQUIRED_BODY_DATA, 'object', false, fieldName);
		errMsg += typeCheck(fieldValue, entityTypeMap[fieldName].t == tm.OPTIONAL_BODY_DATA, 'object', true, fieldName);
		errMsg += typeCheck(fieldValue, entityTypeMap[fieldName].t == tm.OPTIONAL_MANY_TO_ONE, 'number', true, fieldName);
		return errMsg;
	}
	function getRowcount(entity) {
		var request = new r();
		var result = -1;
		request.open('GET', urlContext + entity + '/', false);
		request.onreadystatechange = function() {
			if (request.readyState == 4) {
				result = JSON.parse(request.responseText).count * 1;
			}
		}
		request.send();
		return result;
	}

	function addRequestHeader(x, name, value) {
		x.setRequestHeader("x-" + name, encodeURIComponent(value));
	}

	function loadIntoCache(url) {
		var x = new r();
		x.open('GET', url, false);
		x.onreadystatechange = function() {
			if (x.readyState == 4) {
				var l = JSON.parse(x["responseText"]);
				if (memory.cache[l.entity] === undefined) {
					memory.cache[l.entity] = new Object();
				}
				memory.cache[l.entity][l.index] = l;
			}
		}
		x.send();
	}

	function typeCheck(fieldValue, enableThisCheck, requiredTypename, nullable, fieldName) {
		if (enableThisCheck) {
			if (nullable && fieldValue === null) {
				return "";
			}
			if (typeof fieldValue !== requiredTypename) {
				return '\n  ' + fieldName + ' must be ' + requiredTypename + (nullable ? ' or null' : '') + ' but is ' + (fieldValue === null ? 'null' : "" + typeof fieldValue) + '! ';
			}
		}
		return '';
	}

	function lazy(entityName, entityRowIndex) {
		loadIntoCache(urlContext + entityName + '/' + entityRowIndex);
		var json = memory.cache[entityName][entityRowIndex];
		var entityPropertyNames = Object.keys(json.payload);
		var o = new Object();
		function constructProperty(thisEntityName, entityRowIndex, entityFieldName) {
			Object.defineProperty(o, entityFieldName, {
				enumerable : true,
				configurable : true,
				get : function(e) {
					var val = memory.cache[thisEntityName][entityRowIndex].payload[entityFieldName];
					var p = schema[thisEntityName][entityFieldName];
					if (p.t == tm.ONE_TO_MANY) {
						var o = [];
						for (var i = 0; i < val.length; i++) {
							o.push(memory[p.type][val[i]]);
						}
						return o;
					}
					if (p.t == tm.MANY_TO_MANY_OWNER || p.t == tm.MANY_TO_MANY_NON_OWNER) {
						var o = [];
						for (var i = 0; i < val.length; i++) {
							o.push(memory[p.type][val[i]]);
						}
						o.push = function(e,cb){
							var request = new r();
							request.onreadystatechange = function() {
								if (request.readyState == 4) {
									if (cb!=null) cb(request);
								}
							}
							request.open('POST', urlContext + thisEntityName + '/' + entityRowIndex + '/' + entityFieldName + '/', cb!=null);
							request.send(e);
						}
						return o;
					}
					if (p.t == tm.REQUIRED_MANY_TO_ONE) {
						return memory[p.type][val]
					}
					return val;
				},
				set : function(newPropertyValue) {
					var myTypeDescription = schema[thisEntityName];
					var example = {};
					example[entityFieldName] = newPropertyValue;
					var errMsg = addMessage('', example, myTypeDescription, entityFieldName);
					if (errMsg != '')
						throw errMsg;
					var request = new r();
					request.onreadystatechange = function() {
						if (request.readyState == 4) {
							memory.cache[thisEntityName][entityRowIndex].payload[entityFieldName] = newPropertyValue;
							memory.working.splice(memory.working.indexOf(request));
						}
					}
					request.open('POST', urlContext + thisEntityName + '/' + entityRowIndex + '/' + entityFieldName, false);
					memory.working.push(request);
					if (newPropertyValue == null) {
						addRequestHeader(request, entityFieldName + '-null', true);
						request.send();
					} else {
						request.send(newPropertyValue);
					}

				}
			});
		}
		for (var i = 0; i < entityPropertyNames.length; i++) {
			constructProperty(entityName, entityRowIndex, entityPropertyNames[i]);
		}
		// construct virtual properties
		var entityNamespace = schema[entityName];
		entityPropertyNames = Object.keys(entityNamespace);
		for (var i = 0; i < entityPropertyNames.length; i++) {
			var entityPropertyName = entityPropertyNames[i];
			var virt = entityNamespace[entityPropertyName].t == tm.OPTIONAL_BODY_DATA || entityNamespace[entityPropertyName].t == tm.REQUIRED_BODY_DATA || entityNamespace[entityPropertyName].t == tm.OPTIONAL_BODY_DATA_BLOB || entityNamespace[entityPropertyName].t == tm.REQUIRED_BODY_DATA_BLOB;
			if (virt) {
				var constructLazyVirtual=function(o, name, index) {
					Object.defineProperty(o, name, {
						enumerable : true,
						configurable : true,
						get : function(e) {
							var x = new r();
							x.open("GET", urlContextWithTailingSlash + entityName + '/' + index + '/' + name, false);
							x.send();
							var json = JSON.parse(x["responseText"]);
							return json;
						},
						set : function(e) {
							var x = new r();
							x.open("POST", urlContextWithTailingSlash + entityName + '/' + index + '/' + name, false);
							x.send(new Int8Array(e));
							return e;
						}
					});
				}
				constructLazyVirtual(o, entityPropertyName, entityRowIndex);
			}
		}

		o.remove = function(cb) {
			var x = new r();
			x.onreadystatechange = function() {
				if (x.readyState == 4) {
					if (cb != null) {
						var val = x;
						if (x.status == 428) {
							val = 'Can not remove ' + entityName + ' because preconditions failed!';
							console.warn(val);
							val = new Error(val);
						}
						cb(x);
					}
				}
			}
			x.open('DELETE', urlContextWithTailingSlash + '/' + entityName + '/' + entityRowIndex, cb != null);
			x.send();
			if (cb == null && x.status == 428)
				throw 'Preconditions failed';
			return true;
		};
		return o;
	}
	function constructLazy(entityNamespace, entityRowIndex, entityName) {
		Object.defineProperty(entityNamespace, entityRowIndex, {
			enumerable : false,
			configurable : false,
			get : function() {
				return lazy(entityName, entityRowIndex);
			}
		})
	}

	function construct(ob, entityName) {
		var entityNamespace = schema[entityName];
		var entityFieldnames = Object.keys(entityNamespace);
		function checkEntityCreation(newEntityJSON) {
			var required = [];
			var optional = [];
			var msg = '';
			for (var entityFieldIndex = 0; entityFieldIndex < entityFieldnames.length; entityFieldIndex++) {
				var c = entityNamespace[entityFieldnames[entityFieldIndex]];
				var cn = entityFieldnames[entityFieldIndex];
				if (c.t == tm.OPTIONAL_NUMBER || c.t == tm.OPTIONAL_BOOLEAN || c.t == tm.OPTIONAL_STRING_OR_CHAR || c.t == tm.OPTIONAL_BODY_DATA || c.t == tm.OPTIONAL_BODY_DATA_BLOB
						|| c.t == tm.OPTIONAL_MANY_TO_ONE) {
					optional.push(cn);
				}
				if (c.t == tm.REQUIRED_NUMBER || c.t == tm.REQUIRED_BOOLEAN || c.t == tm.REQUIRED_STRING_OR_CHAR || c.t == tm.REQUIRED_BODY_DATA || c.t == tm.REQUIRED_BODY_DATA_BLOB
						|| c.t == tm.REQUIRED_MANY_TO_ONE) {
					required.push(cn);
				}
			}
			var newEntityFieldnames = Object.keys(newEntityJSON);
			for (var newEntityFieldnameIndex = 0; newEntityFieldnameIndex < newEntityFieldnames.length; newEntityFieldnameIndex++) {
				var newEntityFieldname = newEntityFieldnames[newEntityFieldnameIndex];
				if (undef(entityNamespace[newEntityFieldname])) {
					msg += '\n  ' + newEntityFieldname + ' is unknown! ';
					continue;
				}
				msg = addMessage(msg, newEntityJSON, entityNamespace, newEntityFieldname);
			}
			for (var newEntityFieldnameIndex = 0; newEntityFieldnameIndex < required.length; newEntityFieldnameIndex++) {
				var newEntityFieldname = required[newEntityFieldnameIndex];
				if (undef(newEntityJSON[newEntityFieldname])) {
					msg += '\n  ' + newEntityFieldname + ' is required! ';
				}
			}
			for (var newEntityFieldnameIndex = 0; newEntityFieldnameIndex < optional.length; newEntityFieldnameIndex++) {
				var newEntityFieldname = optional[newEntityFieldnameIndex];
				if (undef(newEntityJSON[newEntityFieldname])) {
					msg += '\n  ' + newEntityFieldname + ' is required, may be null! ';
				}
			}
			if (msg != '') {
				throw entityName + ": " + msg;
			}
		}

		var tableDescriptor = {
			enumerable : true,
			configurable : false,
			get : function() {
				var rowcount = getRowcount(entityName);
				var entityNamespace = new Array(rowcount);
				function pushFunction(data, cb, additional) {
					var saveNewEntityFunction = function(entityJson, callback, additional){
						var val = null;
						var request = new r();
						checkEntityCreation(entityJson);
						var xurl = urlContextWithTailingSlash + entityName + '/';
						if(typeof additional != 'undefined') {
							xurl = xurl + '?' + additional;
						}
						request.open('PUT', xurl, arguments.length > 1);
						request.onreadystatechange = function() {
							if (request.readyState == 4) {
								var json = JSON.parse(request["responseText"]);
								if (json.error) {
									console.warn("Server error while adding a '" + entityName + "': " + json.error);
									alert(json.error);
								}
								if (typeof json.id === 'number') {
									val = json.id;
									if (typeof callback === 'function') {
										callback(json);
									}
								}
							}
						}
						var body = null;
						var hasBody = false;
						for (var i = 0; i < entityFieldnames.length; i++) {
							var fieldname = entityFieldnames[i];
							var value = entityJson[fieldname];
							var f = schema[entityName][fieldname];
							if (f.t == tm.REQUIRED_BODY_DATA || f.t == tm.OPTIONAL_BODY_DATA||f.t == tm.REQUIRED_BODY_DATA_BLOB || f.t == tm.OPTIONAL_BODY_DATA_BLOB) {
								hasBody = true;
								var d = entityJson[entityFieldnames[i]];
								if (d instanceof File) {
									var fr = new FileReader();
									fr.onload = function (res){
										var newData = Object.assign({},entityJson,{});
										newData[entityFieldnames[i]] = new Int8Array(res.target.result);
										saveNewEntityFunction(newData, callback);
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
									addRequestHeader(request, fieldname + "-null", true);
								}
								addRequestHeader(request, fieldname, value);
							}
						}
						if (hasBody) {
							request.send(body);
						} else {
							request.send();
						}
						return val;
					}
					return saveNewEntityFunction(data, cb, additional); 
				}
				Object.defineProperty(entityNamespace, 'push', {
					enumerable : false,
					configurable : false,
					get : function() {
						return pushFunction;
					}
				});
				entityNamespace.load = function(id, cb) {
					var request = new r();
					var result;
					request.onreadystatechange = function() {
						if (request.readyState == 4) {
							result = JSON.parse(request["responseText"]);
							if (result.error != null) {
								console.warn(result.error);
							} else {
								result = result.index;
							}
						}
					}
					request.open('GET', urlContextWithTailingSlash + entityName + '/?id=' + id, cb != null);
					request.send();
					if (cb == null) {
						return result;
					}
				}
				for (var i = 0; i < rowcount; i++) {
					constructLazy(entityNamespace, i, entityName);
				}
				return entityNamespace;
			},
			set : function(v) {
			}
		}
		Object.defineProperty(ob, entityName, tableDescriptor);
	}
	var keys = Object.keys(schema);
	for (var i = 0; i < keys.length; i++) {
		var key = keys[i];
		construct(memory, key);
	}
	return memory;
}