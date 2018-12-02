(ns mysql-to-datomic.core
  (:gen-class)
  (:require
    [clojure.tools.logging :as log :refer [debug info warn error]]
    [mysql-to-datomic.get-mysql :as get-mysql]
    [mysql-to-datomic.gen-schema :as gen-schema]
    [mysql-to-datomic.migrations :as migrate]
    [mysql-to-datomic.transform :as transform]
    [mysql-to-datomic.gen-spec :as gen-spec]
    [datomic.api :as d]
    [clojure.java.io :as io]
    [datascript.core :as ds]
    [environ.core :refer [env]]
    [clojure.data :refer [diff]]
    [hikari-cp.core :refer [make-datasource close-datasource]]
    [domain-spec.literals :refer [schema-tx-ds schema-tx]]
    domain-spec.core))

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

(defn uri [] (or (env :datomic-uri) "datomic:mem://mysql-datomic-test1"))

(defonce state (atom {:mysql   nil
                      :tables  nil
                      :datomic nil
                      :pks     nil}))

(defn start-dbs [state opts]
  (when-not (:mysql @state)
    (swap! state assoc :mysql {:datasource (make-datasource (datasource-options opts))}))
  (do
    (d/create-database (uri))
    (swap! state assoc :datomic (d/connect (uri))))
  (debug "USING " (datasource-options opts) (uri)))

(defn run-conversion [state cx my-ds]
  (let [;mydb (:mysql @state)
        ;cx   (:datomic @state)
        _      (debug "generating mysql table infos")
        tables (get-mysql/tablator my-ds)
        _      (swap! state assoc :tables tables)

        _      (debug "generating model specs")
        specs  (gen-spec/tables->specs tables)

        ;; generate our terse DB schema from the specs
        ;_ (debug "generating terse DB schemas from the specs")
        ;d-schema-terse (gen-schema/spec-schemalator specs)

        ;_ (debug "generating datascript schemas")
        ;ds-schemas (schema-tx-ds d-schema-terse)

        ;_ (debug "generating datomic schemas")
        ;d-schemas (schema-tx d-schema-terse)

        ;; convert the schemas (old way)
        ;schemas      (g/generator state "rimdb" tables)
        ;schema-norms (mi/schema-norms-map :rimdb-schema/initial schemas)

        ;(debug "transacting schemas")
        ;; transact the schema
        ;(mi/conform-it cx schema-norms)

        ;(debug "transacting data")

        ;; transact the data
        db     (d/db cx)
        tx     (transform/run-main-fields-with db state my-ds specs)
        tx     (transform/run-fks-with (:db-after tx) state)]

    (debug "done")
    (:db-after tx)))

(defn run-update [state cx my-ds]
  (let [_      (debug "generating mysql table infos")
        tables (get-mysql/tablator my-ds)
        _      (swap! state assoc :tables tables)

        _      (debug "generating model specs")
        specs  (gen-spec/tables->specs tables)

        ;; generate our terse DB schema from the specs
        ;_ (debug "generating terse DB schemas from the specs")
        ;d-schema-terse (gen-schema/spec-schemalator specs)

        ;_ (debug "generating datascript schemas")
        ;ds-schemas (schema-tx-ds d-schema-terse)

        ;_ (debug "generating datomic schemas")
        ;d-schemas (schema-tx d-schema-terse)

        ;; convert the schemas (old way)
        ;schemas      (g/generator state "rimdb" tables)
        ;schema-norms (mi/schema-norms-map :rimdb-schema/initial schemas)

        ;(debug "transacting schemas")
        ;; transact the schema
        ;(mi/conform-it cx schema-norms)

        ;(debug "transacting data")

        ;; transact the data
        db     (d/db cx)
        tx     (transform/run-main-fields-with db state my-ds specs)
        tx     (transform/run-fks-with (:db-after tx) state)]

    (debug "done")
    (:db-after tx)))


;(defn -main []
;  (start-dbs state nil)
;  (let [db-after (run-conversion state)]
;    (swap! state assoc :db-after db-after)))


(comment
  (start-dbs state {:mysql-username "rimdb"
                    :mysql-password "rimdb123"
                    :mysql-database "rimdb"})

  (def mydb (:mysql @state))
  (def cx (:datomic @state))
  (def tables (get-mysql/tablator mydb))
  (swap! state assoc :tables tables)

  (def mydb2 {:datasource (make-datasource (datasource-options {:mysql-username "rimdb"
                                                                :mysql-password "rimdb123"
                                                                :mysql-database "rimdb2018"}))})

  (def dsd (domain-spec.core/new-specs-ds))
  (def specs (gen-spec/tables->specs tables))
  (ds/transact dsd specs)

  (require '[clojure.java.io :as io])
  (spit (io/file "resources/specs.edn") (with-out-str (clojure.pprint/pprint specs)))
  (def table-schema (gen-schema/spec-schemalator specs))

  (schema-tx-ds table-schema)
  (schema-tx table-schema)

  (into {}
    (for [[t tm] tables]
      [(:primary-keys tm)
       (for [[r rm] (:rev-keys tm)]
         [(:fktable_name rm) (:pkcolumn_name rm)])]))

  (count (d/q '[:find [?e ...]
                :where [?e :sample/SampleRowID]])
    (d/db (:datomic @state)))
  (count (d/q '[:find [?e ...]
                :where [?e :sample/SampleRowID]]
           (d/db (:datomic @state))))
  (d/pull
    (d/db (:datomic @state))
    '[* {:sitevisit/ProjectID [:projectslookup/AgencyCode]}
      {:sitevisit/StationID [*]}] [:sitevisit/SiteVisitID 81924]))

;(filter #(= :db.type/keyword (:db/valueType %)) schemas)
;(filter #(= :samplingcrew/PersonID (:db/ident %)) schemas)
;(map #(select-keys % [:db/ident]) (filter #(= :db.type/ref (:db/valueType %)) schemas))

;(filter #(= "NY" (:stationlookup/ForkTribGroup %)) data)
;(filter #(:stationlookup/GeoID %) data)
;(filter #(< (count %) 4) data)



