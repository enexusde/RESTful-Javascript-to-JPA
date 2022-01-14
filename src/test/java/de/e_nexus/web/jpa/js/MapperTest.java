package de.e_nexus.web.jpa.js;

import java.util.Enumeration;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class MapperTest {
	public void testStart() throws ScriptException {
		AnnotationConfigApplicationContext acac = new AnnotationConfigApplicationContext(AppConfig.class);
		acac.start();
		JSMapperHandler bean = acac.getBean(JSMapperHandler.class);
		assert bean != null;
		ScriptEngineManager m = new ScriptEngineManager();
		ScriptEngine e = m.getEngineByExtension("js");
		e.eval("var XMLHttpRequest = function(){var self = this;self.open = function(){};self.send = function(){self.responseText='{\"count\":1}';self.readyState=4;self.onreadystatechange();};};");
		e.eval(bean.getJavascriptCode());
		e.eval("var k = build(jsm,'api');");
		Object o = e.eval("k.Rabbit.length;");
		assert o == (Integer)1;
	}
	public static void main(String[] args) throws ScriptException {
		MapperTest t = new MapperTest();
		t.testStart();
	}
}
