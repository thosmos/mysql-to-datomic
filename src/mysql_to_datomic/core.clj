(ns mysql-to-datomic.core
  (:gen-class)
  (:require [mysql-to-datomic.get-mysql :as m]
            [mysql-to-datomic.gen-schema :as g]
            [mysql-to-datomic.migrations :as mi]
            [mysql-to-datomic.transform :as tx]
            [datomic.api :as d]
            [environ.core :refer [env]]
            [hikari-cp.core :refer [make-datasource close-datasource]]))

(def datasource-options {:minimum-idle 2
                         :maximum-pool-size 10
                         :pool-name "db-pool"
                         :adapter "mysql"
                         :username (env :mysql-username)
                         :password (env :mysql-password)
                         :database-name (env :mysql-database)
                         :server-name (or (env :mysql-host) "127.0.0.1")
                         :port-number (or (env :mysql-port) 3306)
                         :use-ssl false})

(def uri (or (env :datomic-uri) "datomic:mem://mysql-datomic-test1"))

(defonce state (atom {:mysql nil
                      :tables nil
                      :datomic nil
                      :pks nil}))

(defn start-dbs [state]
  (if-not (:mysql @state)
    (swap! state assoc :mysql {:datasource (make-datasource datasource-options)}))
  (do
    (d/create-database uri)
    (swap! state assoc :datomic (d/connect uri)))
  (println "USING " datasource-options uri))

(defn run-conversion [state]
  (let [mydb (:mysql @state)
        cx (:datomic @state)
        _ (println "generating mysql table infos")
        tables (m/tablator mydb)
        _ (swap! state assoc :tables tables)
        _ (println "generating datomic schemas")
        ;; convert the schemas
        schemas (g/generator state "rimdb" tables)
        schema-norms (mi/schema-norms-map :rimdb-schema/initial schemas)]


    (println "transacting schemas")
    ;; transact the schema
    (mi/conform-it cx schema-norms)

    (println "transacting data")
    ;; transact the data
    ;(mi/conform-it cx data-norms)

    (tx/run-main-fields state)
    (tx/run-fks state)

    (println "done")
    ))

(defn -main []
  (start-dbs state)
  (run-conversion state))


(comment
  (start-dbs state)
  (def mydb (:mysql @state))
  (def cx (:datomic @state))
  (def  tables (m/tablator mydb))
  (swap! state assoc :tables tables)

  (into {}
    (for [[t tm] tables]
      [(:primary-keys tm) (for [[r rm] (:rev-keys tm)]
                            [(:fktable_name rm) (:pkcolumn_name rm)])]))

  (count (d/q '[:find [?e ...]
          :where [?e :sample/SampleRowID]]
     (d/db (:datomic @state))))

  (d/pull
    (d/db (:datomic @state))
    '[* {:sitevisit/ProjectID [:projectslookup/AgencyCode]}
      {:sitevisit/StationID [*]}] [:sitevisit/SiteVisitID 81924])

  ;(filter #(= :db.type/keyword (:db/valueType %)) schemas)
  ;(filter #(= :samplingcrew/PersonID (:db/ident %)) schemas)
  ;(map #(select-keys % [:db/ident]) (filter #(= :db.type/ref (:db/valueType %)) schemas))

  ;(filter #(= "NY" (:stationlookup/ForkTribGroup %)) data)
  ;(filter #(:stationlookup/GeoID %) data)
  ;(filter #(< (count %) 4) data)


  )

