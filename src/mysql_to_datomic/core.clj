(ns mysql-to-datomic.core
  (:gen-class)
  (:require
    [clojure.java.jdbc :as j]
    [clojure.tools.logging :as log :refer [debug info warn error]]
    [mysql-to-datomic.get-mysql :as get-mysql]
    [mysql-to-datomic.gen-schema :as gen-schema]
    [mysql-to-datomic.transform :as transform]
    [mysql-to-datomic.gen-spec :as gen-spec]
    [mysql-to-datomic.migrations :as mig]
    [datomic.api :as d]
    [clojure.java.io :as io]
    [datascript.core :as ds]
    [environ.core :refer [env]]
    [clojure.data :refer [diff]]
    [hikari-cp.core :refer [make-datasource close-datasource]]
    [domain-spec.literals :refer [schema-tx-ds schema-tx]]
    [domain-spec.core]))

(defn datasource-options
  [opts]
  {:minimum-idle      2
   :maximum-pool-size 10
   :pool-name         "db-pool"
   :adapter           "mysql"
   :username          (or (:mysql-username opts) (env :mysql-username))
   :password          (or (:mysql-password opts) (env :mysql-password))
   :database-name     (or (:mysql-database opts) (env :mysql-database))
   :server-name       (or (:mysql-host opts) (env :mysql-host) "127.0.0.1")
   :port-number       (or (:mysql-port opts) (env :mysql-port) 3306)
   :use-ssl           false})

(defn uri [] (or (env :datomic-uri) "datomic:mem://mysql-datomic-test"))

(defonce state (atom {:tables  nil
                      :fks     nil
                      :pks     nil
                      :tempids nil}))

(defn create-mysql-datasource [opts]
  {:datasource (make-datasource (datasource-options opts))})

(defn run-conversion [state cx my-ds]
  (let [_      (debug "generating mysql table infos")
        tables (get-mysql/tablator my-ds)

        _      (debug "generating table specs")
        specs  (gen-spec/tables->specs tables)

        _      (debug "transferring main fields")
        _      (transform/run-main-fields cx state my-ds specs tables)

        _      (debug "transferring fk fields")
        tx     (transform/run-fks cx state tables)]

    (debug "Done!")
    tx))

(defn -main []
  (debug "Running ...")
  (let [_ (d/create-database (uri))
        cx    (d/connect (uri))
        my-ds (create-mysql-datasource nil)
        tx    (run-conversion state cx my-ds)]
    (swap! state assoc :db-after (:db-after tx))))




(comment

  (def uri "datomic:free://localhost:4334/riverdb")
  (defn cx []
    (d/connect uri))
  (defn db [] (d/db (cx)))

  (def mydb {:datasource (make-datasource (datasource-options {:mysql-username "myuser"
                                                               :mysql-password "mypass"
                                                               :mysql-database "mydb"}))})
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

  ;; clear the process state
  (swap! state
    #(-> %
       (assoc :pks nil)
       (assoc :fks nil)
       (assoc :tables nil)
       (assoc :tempids nil))))





