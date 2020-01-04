(ns mysql-to-datomic.migrations
  (:require
    [clojure.tools.logging :as log :refer [debug info warn error]]
    [datomic.api :as d]
    [clojure.java.jdbc :as j]))


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
              result (into {} (remove (fn [[k v]] (nil? v)) result))
              result (assoc result :riverdb.entity/ns (keyword "entity.ns" t-nm))]
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


(comment
  ;;;;;;; migrate new data that has PK values that were reset to 0 to a few tables that already exist in the destination,
  ;;;;;;; so we need to add the new PK values to the existing highest PK values

  ;; get primary key values for subsequent migration of select tables
  (get-pk-maxes (d/db cx) tables [:sitevisit :sample :fieldresult :labresult :fieldobsresult])
  => {:sitevisit/SiteVisitID 84650,
      :sample/SampleRowID 184944,
      :fieldresult/FieldResultRowID 1438179,
      :labresult/LabResultRowID 108537,
      :fieldobsresult/FieldObsResultRowID 164283}

  ;; double check
  (ffirst (migrate-tables-txes mydb (d/db cx) tables
            [:sitevisit :sample :fieldresult :labresult :fieldobsresult]
            {:sitevisit/SiteVisitID 84650,
             :sample/SampleRowID 184944,
             :fieldresult/FieldResultRowID 1438179,
             :labresult/LabResultRowID 108537,
             :fieldobsresult/FieldObsResultRowID 164283}))

  ;; do it for real
  (for [tx (migrate-tables-txes mydb (d/db cx) tables
             [:sitevisit :sample :fieldresult :labresult :fieldobsresult]
             {:sitevisit/SiteVisitID 84650,
              :sample/SampleRowID 184944,
              :fieldresult/FieldResultRowID 1438179,
              :labresult/LabResultRowID 108537,
              :fieldobsresult/FieldObsResultRowID 164283})]
    (count (:tx-data @(d/transact cx tx))))
  => (18025 9974 96709 1 17797))


