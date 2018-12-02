(ns mysql-to-datomic.migrations
  (:require
    [clojure.tools.logging :as log :refer [debug info warn error]]
    [io.rkn.conformity :as c]
    [datomic.api :as d]))

;(def norms-map (c/read-resource "something.edn"))
;(debug (str "Has attribute? " (c/has-attribute? (d/db cx) :something/title)))
;(c/ensure-conforms cx norms-map [:my-project/something-schema])
;(debug (str "Has attribute? " (c/has-attribute? (d/db cx) :something/title)))

(defn schema-norms-map [key schemas]
  {key
   {:txes [schemas]}})

(defn raw-add [cx schemas]
  (d/transact cx schemas))

(defn conform-it [cx norms]
  (c/ensure-conforms cx norms))
