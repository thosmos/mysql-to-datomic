(ns mysql-to-datomic.transform
  (:require [clojure.string :refer [join]]
            [clojure.java.jdbc :as j]
            [clojure.pprint :refer [pprint]]
            [datomic.api :as d]))

(defn rand-str [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))

(defn ns-kw [ns kw]
  (keyword (str ns "/" (name kw))))


(defn main-fields [state]
  (remove empty?
    (flatten
     (remove empty?
       (for [[t tm] (:tables @state)]
         (let [mydb    (:mysql-db @state)
               t-nm    (name t)
               _       (println "processing " t-nm)
               ;; get all fields from SQL
               sql     (str "SELECT * FROM `" t-nm "`")

               ;; query mysql and add namespaces to all field names
               results (j/query mydb sql {:identifiers #(keyword (str t-nm "/" (name %)))})

               ;; make a primary tempid, save foreign keys for later in :fks and remove them,
               ;; calc all reverse referenced keys, give them tempids, and save in a lookup table :pks
               results (for [r results]
                         (let [
                               ;; remove nil values
                               r      (into {} (remove (fn [[_ v]] (= v nil)) r))
                               r      (if (empty? r) nil r)

                               pks    (:primary-keys tm)
                               ;; if table only has one pri-key, use it
                               pk     (when (= (count pks) 1) (first pks))

                               ;; create namespaced key
                               pkk    (keyword (str t-nm "/" pk))
                               ;; generate a tempid string of PK name and its value
                               dbid   (when pk (str pk (get r pkk)))
                               ;; if dbid exists, add it, otherwise let datomic generate one as we won't use it
                               r      (when r
                                        (assoc r :db/id (if dbid dbid (str t-nm (rand-str 37)))))


                               ;; save minimal records with :db/id and FKs to assert after all records are transacted
                               fks    (:foreign-keys tm)
                               fkks   (for [[fk _] fks]
                                        (ns-kw t-nm fk))
                               _      (when (seq fkks)
                                        (swap! state update-in [:fks t-nm]
                                          conj
                                          (select-keys r (concat [:db/id] fkks))))

                               ;; remove foreign keys to avoid asserting mysql lookup values
                               r      (reduce-kv
                                        (fn [r fk _]
                                          (let [fkk (ns-kw t-nm fk)]
                                            (dissoc r fkk)))
                                        r fks)

                               rks    (:rev-keys tm)
                               ;; get a list of the various primary key names other tables reference
                               rk-pks (into #{} (for [[_ rm] rks]
                                                  (:pkcolumn_name rm)))]

                           ;; set the various Rev-PK and their lookup values into state along with the correct dbid
                           (doseq [rpk rk-pks]
                             (let [rkk     (ns-kw t-nm rpk)
                                   rpk-val (get r rkk)]
                               (swap! state assoc-in [:pks t-nm rpk rpk-val] dbid)))
                           r))]
           results))))))


(defn run-txes [state txes]
  (let [parts (partition 100 100 nil txes)]
    (doseq [part parts]
      (try
        (let [tx (d/transact (:datomic @state) part)
              t-ids (:tempids @tx)]
          (swap! state update :tempids merge t-ids)
          (print ".")
          (flush))
        (catch Exception ex (do
                              (pprint part)
                              (throw ex)))))))

(defn run-main-fields [state]
  (run-txes state (main-fields state)))

(defn foreign-keys [state]
  ;; loop through our saved foreign key records by table
  (let [{:keys [tables fks pks tempids]} @state]
    (flatten
      (for [[t-nm fk-rs] fks]
       (let [tk (keyword t-nm)
             tm (get tables tk)
             t-fks (:foreign-keys tm)]
         (println "processing " t-nm)
         ;; loop through rows
         (for [r fk-rs]
           ;; loop through fields
           (let [ ;; replace tempid with final one
                 dbid-str (:db/id r)
                 dbid (get tempids dbid-str)
                 ;; replace :db/id with new eid value
                 r (assoc r :db/id dbid)
                 ;; loop through FKs and lookup new ref values
                 r (into {}
                     (for [[fkk fk-val] r]
                       (if (= fkk :db/id)
                         [fkk fk-val]
                         (let [;; get the foreign key field name
                               fk-nm (name fkk)
                               fk (keyword fk-nm)

                               ;; get the foreign key info map
                               fk-m (get t-fks fk)

                               ;; get the primary key table and field name info
                               pk-table (:pktable_name fk-m)
                               pk-col (:pkcolumn_name fk-m)

                               ;; get the tempid from the primary key lookup table
                               pk-tempid (get-in pks [pk-table pk-col fk-val])

                               ;; get the eid of the referenced entity from :tempids lookup table
                               pk-dbid (get tempids pk-tempid)]

                           ;; return a MapEntry with the new value
                           [fkk pk-dbid]))))]
             r)))))))

(defn run-fks [state]
  (run-txes state (foreign-keys state)))

