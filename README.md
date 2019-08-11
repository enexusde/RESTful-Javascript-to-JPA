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
