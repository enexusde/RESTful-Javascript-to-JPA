# RESTful-Javascript-to-JPA
Do you ever like to work with the Database in Javascript? Here is a Library that supports a very easy-to-use implementation.

In example having a table user (id, username) you could simply call:

var db = build('rest/');
db.User.push({username:'Tom'});
