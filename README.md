![Java CI with Maven](https://github.com/enexusde/RESTful-Javascript-to-JPA/workflows/Java%20CI%20with%20Maven/badge.svg)

# RESTful-Javascript-to-JPA
Do you ever like to work with the Database in Javascript? Here is a Library that supports a very easy-to-use implementation.

In example adding user Tom to the table user (id, username) you could simply call:

```javascript
var db = build('rest/');
db.User.push({username:'Tom'});
```
Reading all users works like this:

```javascript
var db = build('rest/');
for(var i =0; i < db.User.length;i++){
  alert (db.User[i].username);
}
```

And change the name of Tom to Marry is like this:
```javascript
var db = build('rest/');
for(var i =0; i < db.User.length;i++){
  if (db.User[i].username == 'Tom') {
    db.User[i].username = 'Marry';
  }
}
```

# Integration

Add this servlet to `web.xml`:
```xml
  <servlet>
		<servlet-name>ReSTful-js2jpa-Servlet</servlet-name>
		<servlet-class>de.e_nexus.web.jpa.js.JSMapperServlet</servlet-class>
	</servlet>
	<servlet-mapping>
		<servlet-name>ReSTful-js2jpa-Servlet</servlet-name>
		<url-pattern>/rest/*</url-pattern>
	</servlet-mapping>
```

Add this dependency to `pom.xml` (unfortunately currently not served by mvncentral)
```
		<dependency>
			<groupId>com.sotacms</groupId>
			<artifactId>restfuljs2jpa</artifactId>
			<version>1.0.13</version>
		</dependency>
```
And this to the HTML:
```
<script type="text/javascript" src="/rest/jsm.js"></script>
```
