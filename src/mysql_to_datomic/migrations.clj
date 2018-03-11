(ns mysql-to-datomic.migrations
  (:require [io.rkn.conformity :as c]
            [datomic.api :as d]))

;(def norms-map (c/read-resource "something.edn"))
;(println (str "Has attribute? " (c/has-attribute? (d/db cx) :something/title)))
;(c/ensure-conforms cx norms-map [:my-project/something-schema])
;(println (str "Has attribute? " (c/has-attribute? (d/db cx) :something/title)))

(defn schema-norms-map [key schemas]
  {key
   {:txes [schemas]}})

(defn raw-add [cx schemas]
  (d/transact cx schemas))

(defn conform-it [cx norms]
  (c/ensure-conforms cx norms))
