(ns mysql-to-datomic.get-mysql
  (:require
    [clojure.tools.logging :as log :refer [debug info warn error]]
    [clojure.string :as s :refer [join]]
    [clojure.java.jdbc :as j]
    [clojure.pprint :refer [pprint]]
    [com.rpl.specter :refer :all]
    [thosmos.util :refer [merge-tree]]))


(defn get-tables [meta]
  (map :table_name
    (j/metadata-result
      (.getTables meta nil nil nil (into-array String ["TABLE"])))))

(defn get-columns [meta table]
  (map #(select-keys % [:column_name
                        :type_name
                        :data_type
                        :column_size
                        :decimal_digits
                        :nullable
                        :is_autoincrement
                        :ordinal_position])
    (j/metadata-result
      (.getColumns meta nil nil table nil))))


(defn into-map [key coll]
  (into {}
    (for [c coll]
      [(keyword (s/replace (get c key) #" " "_")) c])))

(defn get-columns-map [meta table]
  (into-map :column_name (get-columns meta table)))

(defn get-primary-keys [meta table]
  (vec
    (map :column_name
      (sort-by :key_seq
        (j/metadata-result
          (.getPrimaryKeys meta nil nil table))))))

(defn get-foreign-keys [meta table]
  (map #(select-keys % [:pktable_name :pkcolumn_name
                        :fktable_name :fkcolumn_name
                        :delete_rule :update_rule])
    (j/metadata-result
      (.getImportedKeys meta nil nil table))))

(defn get-foreign-keys-map [meta table]
  (into-map :fkcolumn_name (get-foreign-keys meta table)))


(defn add-rev-map-keys [tables]
  (into {}
    (for [[k table] tables]
      (let [v (assoc table :rev-keys
                           (into-map :fktable_name
                             (select [MAP-VALS :foreign-keys MAP-VALS #(= (:pktable_name %) (name k))] tables)))]
        [k v]))))

(defn get-indexes [meta table]
  (j/metadata-result
    (.getIndexInfo meta nil nil table false false)))

(defn tablator [mysql-db]
  (j/with-db-metadata [meta mysql-db]
    (let [tables (get-tables meta)
          tables (remove #(clojure.string/ends-with? % "_entry") tables)
          tables (into (sorted-map)
                   (for [table tables]
                     [(keyword table) {:columns (get-columns-map meta table)
                                       :foreign-keys (get-foreign-keys-map meta table)
                                       :primary-keys (get-primary-keys meta table)}]))

          tables (add-rev-map-keys tables)]

      tables)))
