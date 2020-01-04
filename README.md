# MySQL-to-Datomic
[![Clojars Project](https://img.shields.io/clojars/v/thosmos/mysql-to-datomic.svg)](https://clojars.org/thosmos/mysql-to-datomic)

A Clojure library designed to automatically transfer data from MySQL to Datomic.

It analyzes the schema of the MySQL DB, including single-field foreign keys, 
generates a Datomic schema, transforms the data from MySQL into Datomic format, and transacts it.


### Run as an app

This depends on some environment variables:
 ```
 MYSQL_DATABASE
 MYSQL_USERNAME
 MYSQL_PASSWORD
 MYSQL_HOST (default 127.0.0.1)
 MYSQL_PORT (default 3306)
 DATOMIC_URI (default "datomic:mem://mysql-datomic-test1")
 ```

LIKE:
```bash
clojure -A:run
```

OR POSSIBLY LIKE?:
```bash
MYSQL_DATABASE=mydb MYSQL_USERNAME=myuser clojure -A:run
```

OR MAYBE LIKE?:
```bash
clojure -J-Dmysql.database=riverdb -J-Dmysql.username=riverdb -A:run
```

### From the REPL

Start a repl like:

```bash
clj 
```

The function `mysql-to-datomic.core/run-conversion` will do the full process:

```clojure
(require 'mysql-to-datomic.core)
(in-ns 'mysql-to-datomic.core)
(def uri "datomic:sql://newdb?jdbc:mysql://localhost:3306/datomic?user=datomic&password=datomic&useSSL=false")
(d/create-database uri)
(def cx (d/connect uri))
(def my-ds (create-mysql-datasource  {:mysql-username "myuser"
                                      :mysql-password "mypass"
                                      :mysql-database "mydb"}))
(def tx    (run-conversion state cx my-ds))
```

or in more detail allowing doing other things with the generated pieces:

```clojure
(require 'mysql-to-datomic.core)
(in-ns 'mysql-to-datomic.core)

(def uri "datomic:sql://newdb?jdbc:mysql://localhost:3306/datomic?user=datomic&password=datomic&useSSL=false")
(def cx (d/connect uri))
(defn db [] (d/db cx))

(def mydb (create-mysql-datasource  {:mysql-username "myuser"
                                     :mysql-password "mypass"
                                     :mysql-database "mydb"}))
(def tables (get-mysql/tablator mydb))
(def specs (gen-spec/tables->specs tables))

;; put the specs into a datascript DB for querying
(def dsdb (domain-spec.core/new-specs-ds))
(ds/transact dsdb specs)

;;;; generate terse DB schemas from the specs
(def d-schema-terse (gen-schema/spec-schemalator specs))

;;;; generate datascript schemas
(schema-tx-ds d-schema-terse)

;;;; generate datomic schemas
(schema-tx d-schema-terse)

;;;; transact the data into a temp DB
(def txw (transform/run-main-fields-with (db) state my-ds specs tables))
(def txw' (transform/run-fks-with (:db-after txs) state tables))
(:db-after txw')

;;;; transact it for real ... careful
(def tx (transform/run-main-fields cx state my-ds specs tables))
(def tx' (transform/run-fks cx state tables))
```


## JVM Resource Usage

It has been somewhat optimized to run in a low memory virtual machine.  You will probably need to 
tweak the JVM memory settings though.

`datomic.objectCacheMax` should match your transactor's equivalent setting.

```bash
clojure -J-server -J-Xmx370m -J-Xms128m -J-Ddatomic.objectCacheMax=128m -A:run 
```

### NOTE

It currently only handles the MySQL data types that I needed.
The code will most likely need some tweaking for your use case.
Feel free to create an issue or pull request and I'll be happy to help.

## License

Copyright © 2017 Thomas Spellman <thos37@gmail.com>

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
