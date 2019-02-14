(ns mysql-to-datomic.core
  (:gen-class)
  (:require
    [clojure.java.jdbc :as j]
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

(defonce state (atom {:tables  nil
                      :fks     nil
                      :pks     nil
                      :tempids nil}))

;(defn start-dbs [state opts]
;  (when-not (:mysql @state)
;    (swap! state assoc :mysql {:datasource (make-datasource (datasource-options opts))}))
;  (do
;    (d/create-database (uri))
;    (swap! state assoc :datomic (d/connect (uri))))
;  (debug "USING " (datasource-options opts) (uri)))


(defn create-mysql-datasource [opts]
  {:datasource (make-datasource (datasource-options (merge datasource-options opts)))})

(defn run-conversion [state cx my-ds]
  (let [;mydb (:mysql @state)
        ;cx   (:datomic @state)
        _      (debug "generating mysql table infos")
        tables (get-mysql/tablator my-ds)
        ;_      (swap! state assoc :tables tables)

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
        tx     (transform/run-main-fields-with db state my-ds specs tables)
        tx     (transform/run-fks-with (:db-after tx) state tables)]

    (debug "done")
    (:db-after tx)))


;(defn -main []
;  (start-dbs state nil)
;  (let [db-after (run-conversion state)]
;    (swap! state assoc :db-after db-after)))

;:foreign-keys {:AgencyCode {:pktable_name "agencylookup",
;                            :pkcolumn_name "AgencyCode",
;                            :fktable_name "sitevisit",
;                            :fkcolumn_name "AgencyCode",
;                            :delete_rule 3,
;                            :update_rule 0},
;               :ProjectID {:pktable_name "projectslookup",
;                           :pkcolumn_name "ProjectID",
;                           :fktable_name "sitevisit",
;                           :fkcolumn_name "ProjectID",
;                           :delete_rule 3,
;                           :update_rule 0},
;               :SeasonCode {:pktable_name "seasonlookup",
;                            :pkcolumn_name "SeasonCode",
;                            :fktable_name "sitevisit",
;                            :fkcolumn_name "SeasonCode",
;                            :delete_rule 3,
;                            :update_rule 0},
;               :StationFailCode {:pktable_name "stationfaillookup",
;                                 :pkcolumn_name "StationFailCode",
;                                 :fktable_name "sitevisit",
;                                 :fkcolumn_name "StationFailCode",
;                                 :delete_rule 3,
;                                 :update_rule 0},
;               :StationID {:pktable_name "stationlookup",
;                           :pkcolumn_name "StationsRowID",
;                           :fktable_name "sitevisit",
;                           :fkcolumn_name "StationID",
;                           :delete_rule 3,
;                           :update_rule 0},
;               :VisitType {:pktable_name "sitevisittype",
;                           :pkcolumn_name "id",
;                           :fktable_name "sitevisit",
;                           :fkcolumn_name "VisitType",
;                           :delete_rule 2,
;                           :update_rule 0}},
;:primary-keys ["SiteVisitID"],


(defn find-attr-max [db attr]
  (d/q '[:find (max ?v) .
         :in $ ?attr
         :where [_ ?attr ?v]]
    db attr))

(defn get-pk-maxes [db tables-map table-keys]
  (into {}
    (for [t-k table-keys]
      (let [t-nm (name t-k)
            t-map (get tables-map t-k)
            pk-kw (keyword t-nm (first (:primary-keys t-map)))
            pk-max (find-attr-max db pk-kw)]
        [pk-kw pk-max]))))


(defn migrate-table-txes [mydb tables-map table-key pk-maxes]
  (let [table   (get tables-map table-key)
        fks     (:foreign-keys table)
        t-nm    (name table-key)
        sql     (str "SELECT * FROM `" t-nm "`")
        pk-kw   (keyword t-nm (first (:primary-keys table)))
        pk-max  (get pk-maxes pk-kw)
        ;; query mysql and add namespaces to all field names
        results (j/query mydb sql {:identifiers #(keyword t-nm (name %))})]
    (vec
      (for [result results]
        (let [pk-val (get result pk-kw)
              result (assoc result pk-kw (+ pk-val pk-max))
              result (into {} (remove (fn [[k v]] (nil? v)) result))]
          (reduce-kv
            (fn [result k {:keys [fktable_name fkcolumn_name pktable_name pkcolumn_name]}]
              (let [fk-kw  (keyword fktable_name fkcolumn_name)
                    fk-val (get result fk-kw)
                    pk-kw  (keyword pktable_name pkcolumn_name)
                    fk-val (if-let [fk-max (get pk-maxes pk-kw)]
                             (+ fk-val fk-max)
                             fk-val)
                    ;fk-ref (get-fk-ref (d/db cx) pk-kw "SYRCL")
                    fk-ref [pk-kw fk-val]]
                ;(debug "FK from" fkcolumn_name "to" pktable_name "/" pkcolumn_name " ref " (when fk-val fk-ref))
                (if fk-val
                  (assoc result fk-kw fk-ref)
                  result)))
            result fks))))))


(defn migrate-tables-txes
  ([mydb db tables-map table-keys]
   (let [pk-maxes (get-pk-maxes db tables-map table-keys)]
     (migrate-tables-txes mydb db tables-map pk-maxes)))
  ([mydb db tables-map table-keys pk-maxes]
   (for [t-key table-keys]
     (migrate-table-txes mydb tables-map t-key pk-maxes))))


;(migrate-tables tables mydb cxr [:sitevisit :sample :fieldresult])

(comment
  (start-dbs state {:mysql-username "rimdb"
                    :mysql-password "rimdb123"
                    :mysql-database "rimdb"})

  (def mydb (:mysql @state))
  (def cx (:datomic @state))
  (def tables (get-mysql/tablator mydb))
  (swap! state assoc :tables tables)



  (def dsd (domain-spec.core/new-specs-ds))
  (def specs (gen-spec/tables->specs tables))
  (ds/transact dsd specs)



  ;; 2018


  (swap! state
    #(-> %
       (assoc :pks nil)
       (assoc :fks nil)
       (assoc :tables nil)
       (assoc :tempids nil)))

  (def cxr (d/connect "..."))

  (def mydb {:datasource (make-datasource (datasource-options {:mysql-username "rimdb"
                                                               :mysql-password "rimdb123"
                                                               :mysql-database "rimdb"}))})
  (def mydb2 {:datasource (make-datasource (datasource-options {:mysql-username "rimdb"
                                                                :mysql-password "rimdb123"
                                                                :mysql-database "rimdb2018"}))})
  (def tables (get-mysql/tablator mydb2))
  (def specs (gen-spec/tables->specs tables))


  ;;; 2019 migration


  ;; test running the data import.
  ;; FIXME very important is the order of table keys for references to work (sample references sitevisit so [:sitevisit :sample])
  ;; FIXME one fix is to use string tempids, but then ALL data will run in one TX.  At least this way we have one per table

  (require 'dotenv)
  (def uri2 (dotenv/env :DATOMIC_URI))
  (def cx2 (d/connect uri2))

  (def test-db-2 (reduce
                   (fn [result next]
                     (:db-after (d/with result next)))
                   (d/db cx2) (migrate-tables-txes mydb2 (d/db cx2) tables [:sitevisit :sample])))

  (get-pk-maxes (d/db cx2) tables [:sitevisit :sample :fieldresult :labresult :fieldobsresult])

  ;; Feb 1, 2018
  {:sitevisit/SiteVisitID 84894,
   :sample/SampleRowID 184944,
   :fieldresult/FieldResultRowID 1438179,
   :labresult/LabResultRowID 108537,
   :fieldobsresult/FieldObsResultRowID 162685}

  ;; after retract all 2018 data (had errors in sample and result IDs)
  {:sitevisit/SiteVisitID 84650,
   :sample/SampleRowID 184944,
   :fieldresult/FieldResultRowID 1438179,
   :labresult/LabResultRowID 108537,
   :fieldobsresult/FieldObsResultRowID 162685}

  ;; after data migration
  {:sitevisit/SiteVisitID 84963,
   :sample/SampleRowID 185528,
   :fieldresult/FieldResultRowID 1442921,
   :labresult/LabResultRowID 108537,
   :fieldobsresult/FieldObsResultRowID 164277}

  ;; now do it for real

  (for [tx (migrate-tables-txes mydb2 (d/db cxr) tables
             [:sitevisit :sample :fieldresult :labresult :fieldobsresult]
             {:sitevisit/SiteVisitID 84650,
              :sample/SampleRowID 184944,
              :fieldresult/FieldResultRowID 1438179,
              :labresult/LabResultRowID 108537,
              :fieldobsresult/FieldObsResultRowID 162685})]
    (count (:tx-data @(d/transact cxr tx))))


  ;; server-side:

  (require 'dotenv)
  (def uri2 (dotenv/env :DATOMIC_URI))
  (def cx2 (d/connect uri2))

  (get-pk-maxes (d/db cx2) tables [:sitevisit :sample :fieldresult :labresult :fieldobsresult])

  {:sitevisit/SiteVisitID 84650,
   :sample/SampleRowID 184944,
   :fieldresult/FieldResultRowID 1438179,
   :labresult/LabResultRowID 108537,
   :fieldobsresult/FieldObsResultRowID 162685}

  ;; check it

  (ffirst (migrate-tables-txes mydb2 (d/db cx2) tables
            [:sitevisit :sample :fieldresult :labresult :fieldobsresult]
            {:sitevisit/SiteVisitID              84650,
             :sample/SampleRowID                 184944,
             :fieldresult/FieldResultRowID       1438179,
             :labresult/LabResultRowID           108537,
             :fieldobsresult/FieldObsResultRowID 162685}))

  #:sitevisit{:MetalCollected false, :WidthMeasured false, :CheckPerson 5265, :VisitType [:sitevisittype/id 1], :HydroMod "NR", :CreationTimestamp #inst "2018-05-15T22:11:22.000000000-00:00", :PointID 0, :SiteVisitDate #inst "2018-03-12T07:00:00.000-00:00", :SiteVisitID 84651, :QACheck false, :Datum "NR", :StationID [:stationlookup/StationsRowID 2080], :BacteriaCollected false, :DataEntryPerson 5269, :WaterDepth 30.00000M, :AgencyCode [:agencylookup/AgencyCode "SYRCL"], :Lon 0.00000M, :QAPerson 5297, :TssCollected false, :HydroModLoc "NR", :ProjectID [:projectslookup/ProjectID "07SY0000"], :Notes "Huck: Missing Time of 1st Sample\r\rD.O. on YSI would not stabalize", :DepthMeasured false, :TurbidityCollected false, :StreamWidth 0.00000M, :GPSDeviceCode 1, :Time "15:50", :StationFailCode [:stationfaillookup/StationFailCode 0], :DataEntryDate #inst "2018-05-15T07:00:00.000-00:00", :Lat 0.00000M}

  (first (second (migrate-tables-txes mydb2 (d/db cx2) tables)
                 [:sitevisit :sample :fieldresult :labresult :fieldobsresult]
                 {:sitevisit/SiteVisitID              84650,
                  :sample/SampleRowID                 184944,
                  :fieldresult/FieldResultRowID       1438179,
                  :labresult/LabResultRowID           108537,
                  :fieldobsresult/FieldObsResultRowID 162685}))


  #:sample{:SiteVisitID [:sitevisit/SiteVisitID 84651], :SampleTypeCode [:sampletypelookup/SampleTypeCode "FieldMeasure"], :EventType [:eventtypelookup/EventType "WaterChem"], :QCCheck false, :DepthSampleCollection 0.00000M, :SampleComplete false, :SampleRowID 184945, :SampleReplicate 0}


  ;; for real?

  (for [tx (migrate-tables-txes mydb2 (d/db cx2) tables
             [:sitevisit :sample :fieldresult :labresult :fieldobsresult]
             {:sitevisit/SiteVisitID              84650,
              :sample/SampleRowID                 184944,
              :fieldresult/FieldResultRowID       1438179,
              :labresult/LabResultRowID           108537,
              :fieldobsresult/FieldObsResultRowID 162685})]
    (count (:tx-data @(d/transact cx2 tx))))

  => (9202 4705 46422 1 7921)  ;; seems like it worked!


  ;; retract 2018 data

  (defn retract-entities-txs [eids]
    (vec (for [e eids]
           [:db.fn/retractEntity e])))

  (count (d/q '[:find [(pull ?e [*]) ...]
                :where
                [(> ?dt (java.util.Date. (java.util.Date/parse "2018/01/01")))]
                [?e :sitevisit/SiteVisitDate ?dt]] (d/db cxr)))

  (d/transact cxr
    (retract-entities-txs
      (d/q '[:find [?e ...]
             :where
             [(> ?dt (java.util.Date. (java.util.Date/parse "2018/01/01")))]
             [?e :sitevisit/SiteVisitDate ?dt]] (d/db cxr))))



  ;;; older migration

  (swap! state assoc :tables tables)

  (def schema-terse (gen-schema/spec-schemalator specs))
  (def with-db-tx (d/with (d/db cxr) (schema-tx schema-terse)))

  (def tx (transform/run-main-fields-with (:db-after with-db-tx) state mydb2 specs tables))
  (def tx' (transform/run-fks-with (:db-after tx) state tables))
  (def db-after (:db-after tx'))


  (d/q '[:find ?sv ?fn ?ln (count ?sv)
         :with ?sc
         :where
         [?sc :samplingcrew/SiteVisitID ?sv]
         [?sc :samplingcrew/PersonID ?p]
         [?p :person/FName ?fn]
         [?p :person/LName ?ln]]
    db-after)

  (d/pull db-after '[{:samplingcrew/_SiteVisitID [*]} :db/id] 17592186190722)


  ;;; update crew to use compound-key
  (def crew (d/q '[:find [(pull ?e [:db/id
                                    {:samplingcrew/SiteVisitID [:sitevisit/SiteVisitID]}
                                    {:samplingcrew/PersonID [:person/PersonID]}]) ...]
                   :where [?e :samplingcrew/PersonID]]
              (d/db cxr)))


  (def crew-txs (vec (map
                       #(-> %
                          (assoc :samplingcrew/compound-key
                                 (str "SiteVisitID"
                                   (get-in % [:samplingcrew/SiteVisitID :sitevisit/SiteVisitID])
                                   "PersonID"
                                   (get-in % [:samplingcrew/PersonID :person/PersonID])))
                          (dissoc :samplingcrew/SiteVisitID)
                          (dissoc :samplingcrew/PersonID))
                       crew)))

  (d/transact cxr (schema-tx [[:samplingcrew/compound-key
                               :one
                               :string
                               :identity
                               "a unique identity key to join multiple primary key values: [\"SiteVisitID\" \"PersonID\"]"]]))

  (d/transact cxr crew-txs)

  (d/transact
    cxr
    [[:db/add :sitevisit/SiteVisitID :db/unique :db.unique/identity]
     [:db/add :db.part/db :db.alter/attribute :sitevisit/SiteVisitID]])


  ;;; now do it for real

  (swap! state
    #(-> %
       (assoc :pks nil)
       (assoc :fks nil)
       (assoc :tempids nil)))

  (def tx nil)
  (def tx' nil)
  (def db-after nil)

  (def tx (transform/run-main-fields cxr state mydb2 specs))
  (def tx' (transform/run-fks cxr state))
  (def db-after (:db-after tx'))






  ;;;; OLDER STUFF

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



