# MySQL-to-Datomic

A Clojure library/app designed to automatically transfer data from MySQL to Datomic.

It analyzes the schema of the MySQL DB, including single-field foreign key constraints, 
generates a Datomic schema, transforms the data from MySQL into Datomic format, and transacts it.

## Code

https://github.com/thosmos/mysql-to-datomic

## Usage

### Library

`[thosmos/mysql-to-datomic "0.3.0"]`

[![Clojars Project](https://img.shields.io/clojars/v/thosmos/mysql-to-datomic.svg)](https://clojars.org/thosmos/mysql-to-datomic)


This depends on the environment variables:
 ```
 MYSQL_DATABASE
 MYSQL_USERNAME
 MYSQL_PASSWORD
 MYSQL_HOST (default 127.0.0.1)
 MYSQL_PORT (default 3306)
 DATOMIC_URI (default "datomic:mem://mysql-datomic-test1")
 ```

Assuming the env variables are defined, you can simply call `(mysql-to-datomic.core/-main)`

Alternatively, the function `mysql-to-datomic.core/run-conversion` will do the full process,
and it requires a state atom and for the database connections to be already setup and open:

```clojure
(defonce state (atom {}))
(mysql-to-datomic.core/start-dbs state)
(mysql-to-datomic.core/run-conversion state)
```


### App

To run as an app, first build a binary like:

```bash
lein uberjar
```

Then set the env params:
```
MYSQL_DATABASE
MYSQL_USERNAME
MYSQL_PASSWORD
MYSQL_HOST (default 127.0.0.1)
MYSQL_PORT (default 3306)
DATOMIC_URI (default "datomic:mem://mysql-datomic-test1")
```
POSSIBLY LIKE:
```bash
MYSQL_DATABASE=riverdb MYSQL_USERNAME=riverdb java -jar mysql-to-datomic.jar
```
OR call with JVM parameters like:
```bash
java -Dmysql.database=riverdb -Dmysql.username=riverdb -jar mysql-to-datomic.jar
```

You can run it in dev mode like `lein run` or open a repl `lein repl` and call `(-main)`

## JVM Resource Usage

It has been somewhat optimized to run in a low memory virtual machine.  You will probably need to 
tweak the JVM memory settings though.

`datomic.objectCacheMax` should match your transactor's equivalent setting.

```bash
java -server -Xmx370m -Xms128m -Ddatomic.objectCacheMax=128m -jar mysql-to-datomic.jar
```

## TODO

It currently only handles the MySQL data types that I needed.
The code will most likely need some tweaking for your use case.
Feel free to create an issue or pull request and I'll be happy to help.

## License

Copyright © 2017 Thomas Spellman <thos37@gmail.com>

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
