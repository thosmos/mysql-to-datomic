(ns mysql-to-datomic.gen-schema
  (:require [datomic-schema.schema :as s]))

;; The main schema functions
(defn fields
  "Simply a helper for converting (fields [name :string :indexed]) into {:fields {\"name\" [:string #{:indexed}]}}
  Modified from the macros from datomic-schema.schema"
  [fielddefs]
  (let [defs (reduce (fn [a [nm tp & opts]] (assoc a (name nm) [tp (set opts)])) {} fielddefs)]
    {:fields defs}))

(defn schema
  "Simply merges several maps into a single schema definition and add one or two helper properties.
  Modified from the macros from datomic-schema.schema"
  [nm maps]
  (apply merge
    {:name (name nm) :basetype (keyword nm) :namespace (name nm)}
    maps))

(defn partinator [part] [(s/part part)])


(defn schemalator [state tables-maps]
  (for [[t tm] tables-maps]
    (let [flds (for [[f fm] (:columns tm)]
                 (let [result [f]
                       fk (get-in tm [:foreign-keys f])
                       pks (get tm :primary-keys)
                       ;sql-t (:type_name fm)
                       type (case (:type_name fm)
                              "VARCHAR" :string
                              "LONGTEXT" :string
                              "TEXT" :string
                              "CHAR" :string
                              "BIT" :boolean
                              "INT" :long
                              "SMALLINT" :long
                              "INT UNSIGNED" :long
                              "TINYINT UNSIGNED" :long
                              "DOUBLE" :double
                              "DECIMAL" :bigdec
                              "DATETIME" :instant
                              "DATE" :instant
                              "TIMESTAMP" :instant
                              "TIME" :instant
                              :string)
                       type (if fk :ref type)
                       _ (swap! state assoc-in [:types t f] type)
                       result (conj result type)
                       result (cond-> result
                                (not= type :string)
                                (conj :indexed)

                                (and (= 1 (count pks)) (= (first pks) (name f)))
                                (conj :unique-identity)

                                )]
                   result))]
      (schema t
        (fields flds)))))

(defn generator [state part table-maps]
  (concat
    (s/generate-parts (partinator part))
    (s/generate-schema (schemalator state table-maps))))

