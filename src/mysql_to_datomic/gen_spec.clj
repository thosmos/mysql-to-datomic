(ns mysql-to-datomic.gen-spec
  (:require
    [clojure.tools.logging :as log :refer [debug info warn error]]))

(defn tables->specs [tables]
  (vec
    (for [[table-key table-map] tables]
      (let [table-name   (name table-key)
            fks          (:foreign-keys table-map)
            pks          (:primary-keys table-map)
            compound-id? (when
                           (> (count pks) 1)
                           (keyword table-name "compound-key"))

            attrs        (vec
                           (for [[col-k col-m] (:columns table-map)]
                             (let [col-k-nm (name col-k)
                                   attr-key (keyword table-name col-k-nm)

                                   ;; is this a foreign-key?
                                   fk-m?    (filter
                                              (fn [[_ fk-m]]
                                                (= (:fkcolumn_name fk-m) col-k-nm))
                                              fks)

                                   fk?      (seq fk-m?)

                                   ;; is this a primary key?
                                   pk?      (some #{col-k-nm} pks)

                                   id?      (and
                                              pk?
                                              (= 1 (count pks)))

                                   typ      (condp some [(:type_name col-m)]
                                              #{"BIT"} :boolean
                                              #{"INT" "SMALLINT" "INT UNSIGNED" "TINYINT UNSIGNED"} :long
                                              #{"DOUBLE"} :double
                                              #{"DECIMAL"} :bigdec
                                              #{"DATETIME" "TIMESTAMP" "DATE" "TIME"} :instant
                                              :string)]


                               (merge
                                 {:attr/name     col-k-nm
                                  :attr/key      attr-key
                                  :attr/position (:ordinal_position col-m)}

                                 (when pk?
                                   {:attr/primary? true})

                                 (when id?
                                   {:attr/identity? true})

                                 (when (= 0 (:nullabe col-m))
                                   {:attr/required? true})

                                 (if fk?
                                   {:attr/cardinality :one
                                    :attr/type        :ref
                                    :attr/ref         {:entity/ns
                                                       (keyword "entity.ns" (:pktable_name (last (first fk-m?))))}}
                                   {:attr/cardinality :one
                                    :attr/type        typ})

                                 (when (= typ :double)
                                   {:attr/decimals (:decimal_digits col-m)})

                                 (when (= typ :string)
                                   {:attr/strlen (:column_size col-m)})))))

            attrs        (if compound-id?
                           (conj attrs
                             {:attr/name        "compound-key"
                              :attr/key         compound-id?
                              :attr/identity?   true
                              :attr/cardinality :one
                              :attr/type        :string
                              :attr/doc         (str "a unique identity key to join multiple primary key values: " pks)})
                           attrs)


            result       {:db/id          table-name
                          :entity/name    table-name
                          :entity/ns      (keyword "entity.ns" table-name)
                          :entity/pks     (mapv #(keyword table-name %) pks)
                          :entity/pr-keys (mapv #(keyword table-name %) pks)
                          :entity/attrs   attrs}

            result       (if compound-id?
                           (assoc result :entity/compound-key compound-id?)
                           result)]
        result))))


