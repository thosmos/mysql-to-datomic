(ns mysql-to-datomic.transform
  (:require
    [clojure.tools.logging :as log :refer [debug info warn error]]
    [clojure.string :refer [join]]
    [clojure.java.jdbc :as j]
    [clojure.pprint :refer [pprint]]
    [datomic.api :as d]))

(defn rand-str [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))

(defn ns-kw [ns kw]
  (keyword (str ns "/" (name kw))))

;
;(defn main-fields [state mydb]
;  (remove empty?
;    (flatten
;      (remove empty?
;        (for [[t tm] (:tables @state)]
;          (let [;mydb    (:mysql-db @state)
;                t-nm    (name t)
;                _       (debug "processing " t-nm)
;                ;; get all fields from SQL
;                sql     (str "SELECT * FROM `" t-nm "`")
;
;                ;; query mysql and add namespaces to all field names
;                results (j/query mydb sql {:identifiers #(keyword (str t-nm "/" (name %)))})
;
;                ;; make a primary tempid, save foreign keys for later in :fks and remove them,
;                ;; calc all reverse referenced keys, give them tempids, and save in a lookup table :pks
;                results (for [r results]
;                          (let [
;                                ;; remove nil values
;                                r      (into {} (remove (fn [[_ v]] (= v nil)) r))
;                                r      (if (empty? r) nil r)
;
;                                pks    (:primary-keys tm)
;
;
;                                ;;; if table only has one pri-key, use it
;                                ;pk     (cond
;                                ;         (= (count pks) 1)
;                                ;         (first pks)
;                                ;         (> (count pks) 1)
;                                ;         :compound-key
;                                ;         :default
;                                ;         nil)
;
;                                pks-kw (when (seq pks)
;                                         (map #(keyword (str t-nm "/" %)) pks))
;
;                                ;;; create namespaced key
;                                ;pkk    (keyword (str t-nm "/" pk))
;
;                                ;; generate a tempid string of PK name and its value
;                                dbid   (when (seq pks-kw)
;                                         (apply str
;                                           (map #(str (name %) (get r %))
;                                             pks-kw)))
;
;                                r      (if
;                                         (and (seq pks-kw) (> (count pks-kw) 1))
;                                         (do
;                                           (debug "compound record: " r)
;                                           (assoc r (keyword t-nm "compound-key") dbid))
;                                         r)
;
;
;                                ;; if dbid exists, add it, otherwise let datomic generate one as we won't use it
;                                r      (when r
;                                         (assoc r :db/id (if dbid dbid (str t-nm (rand-str 37)))))
;
;
;                                ;; save minimal records with :db/id and FKs to assert after all records are transacted
;                                fks    (:foreign-keys tm)
;                                fkks   (for [[fk _] fks]
;                                         (ns-kw t-nm fk))
;                                _      (when (seq fkks)
;                                         (swap! state update-in [:fks t-nm]
;                                           conj
;                                           (select-keys r (concat [:db/id] fkks))))
;
;                                ;; remove foreign keys to avoid asserting mysql lookup values
;                                r      (reduce-kv
;                                         (fn [r fk _]
;                                           (let [fkk (ns-kw t-nm fk)]
;                                             (dissoc r fkk)))
;                                         r fks)
;
;                                rks    (:rev-keys tm)
;                                ;; get a list of the various primary key names other tables reference
;                                rk-pks (into #{} (for [[_ rm] rks]
;                                                   (:pkcolumn_name rm)))]
;
;                            ;; set the various Rev-PK and their lookup values into state along with the correct dbid
;                            (doseq [rpk rk-pks]
;                              (let [rkk     (ns-kw t-nm rpk)
;                                    rpk-val (get r rkk)]
;                                (swap! state assoc-in [:pks t-nm rpk rpk-val] dbid)))
;                            r))]
;            results))))))


;;; impure function updates state with foreign-key infos
(defn main-fields-from-specs [state mydb specs]
  (remove empty?
    (flatten
      (remove empty?
        (for [{:keys [db/id entity/pks entity/compound-key entity/attrs]} specs]
          (let [tables    (:tables @state)
                t-key     (keyword id)
                table-map (get tables t-key)
                t-nm      id
                _         (debug "processing " t-nm)
                ;; get all fields from SQL
                sql       (str "SELECT * FROM `" t-nm "`")

                ;; query mysql and add namespaces to all field names
                results   (j/query mydb sql {:identifiers #(keyword (str t-nm "/" (name %)))})]

            ;; make a primary tempid, save foreign keys for later in :fks and remove them,
            ;; calc all reverse referenced keys, give them tempids, and save in a lookup table :pks

            (for [r results]
              (let [
                    ;; remove nil values
                    r (into {} (remove (fn [[_ v]] (= v nil)) r))
                    r (if (empty? r) nil r)]

                (when r
                  (let [
                        ;; generate a tempid string of PK name and its value
                        dbid   (when (seq pks)
                                 (apply str
                                   (map #(str (name %) (get r %))
                                     pks)))

                        r      (if
                                 compound-key
                                 (assoc r compound-key dbid)
                                 r)

                        ;; if dbid exists, add it, otherwise let datomic generate one as we won't use it
                        r      (assoc r :db/id (if dbid dbid (str t-nm (rand-str 37))))


                        ;; save minimal records with :db/id and FKs to assert after all records are transacted
                        fks    (:foreign-keys table-map)
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

                        rks    (:rev-keys table-map)
                        ;; get a list of the various primary key names other tables reference
                        rk-pks (into #{} (for [[_ rm] rks]
                                           (:pkcolumn_name rm)))]

                    ;; set the various Rev-PK and their lookup values into state along with the correct dbid
                    (doseq [rpk rk-pks]
                      (let [rkk     (ns-kw t-nm rpk)
                            rpk-val (get r rkk)]
                        (swap! state assoc-in [:pks t-nm rpk rpk-val] dbid)))

                    r))))))))))


(defn run-txes [state txes]
  (let [parts (partition 100 100 nil txes)]
    (doseq [part parts]
      (try
        (let [tx    (d/transact (:datomic @state) part)
              t-ids (:tempids @tx)]
          (swap! state update :tempids merge t-ids)
          (print ".")
          (flush))
        (catch Exception ex (do
                              (pprint part)
                              (throw ex)))))))

(defn run-txes-with [db txes]
  (let [parts (partition 100 100 nil txes)]
    (loop [dbwith db
           parts  parts
           t-ids  {}]
      (let [part     (vec (first parts))
            tx       (try
                       (d/with dbwith part)
                       (catch Exception ex (do
                                             ;(pprint part)
                                             (pprint ex)
                                             (throw ex))))
            t-ids    (merge t-ids (:tempids tx))
            db-after (:db-after tx)
            next     (next parts)]
        (print ".")
        (flush)
        (if next
          (recur db-after next t-ids)
          {:db-before db :db-after db-after :tempids t-ids})))))

(defn run-txes-real [cx txes]
  (let [db-before (d/db cx)
        parts (partition 100 100 nil txes)]
    (loop [parts  parts
           t-ids  {}]
      (let [part     (vec (first parts))
            tx       (try
                       @(d/transact cx part)
                       (catch Exception ex (do
                                             ;(pprint part)
                                             (pprint ex)
                                             (throw ex))))
            t-ids    (merge t-ids (:tempids tx))
            next     (next parts)]
        (print ".")
        (flush)
        (if next
          (recur next t-ids)
          {:db-before db-before :db-after (:db-after tx) :tempids t-ids})))))



;(defn run-main-fields-old [state]
;  (run-txes state (main-fields state (:mysql-db @state))))

(defn run-main-fields-with [db state my-db specs]
  (swap! state
    #(-> %
       (assoc :pks nil)
       (assoc :fks nil)
       (assoc :tempids nil)))
  (let [tx      (run-txes-with db (main-fields-from-specs state my-db specs))
        tempids (:tempids tx)]
    (swap! state assoc :tempids tempids)
    tx))

(defn run-main-fields-real [cx state my-db specs]
  (swap! state
    #(-> %
       (assoc :pks nil)
       (assoc :fks nil)
       (assoc :tempids nil)))
  (let [tx      (run-txes-real cx (main-fields-from-specs state my-db specs))
        tempids (:tempids tx)]
    (swap! state assoc :tempids tempids)
    tx))

(defn foreign-keys [state]
  ;; loop through our saved foreign key records by table
  (let [{:keys [tables fks pks tempids]} @state]
    (flatten
      (for [[t-nm fk-rs] fks]
        (let [tk    (keyword t-nm)
              tm    (get tables tk)
              t-fks (:foreign-keys tm)]
          (debug "processing " t-nm)
          ;; loop through rows
          (for [r fk-rs]
            ;; loop through fields
            (let [;; replace tempid with final one
                  dbid-str (:db/id r)
                  dbid     (get tempids dbid-str)
                  ;; replace :db/id with new eid value
                  r        (assoc r :db/id dbid)
                  ;; loop through FKs and lookup new ref values
                  r        (into {}
                             (for [[fkk fk-val] r]
                               (if (= fkk :db/id)
                                 [fkk fk-val]
                                 (let [;; get the foreign key field name
                                       fk-nm     (name fkk)
                                       fk        (keyword fk-nm)

                                       ;; get the foreign key info map
                                       fk-m      (get t-fks fk)

                                       ;; get the primary key table and field name info
                                       pk-table  (:pktable_name fk-m)
                                       pk-col    (:pkcolumn_name fk-m)

                                       ;; get the tempid from the primary key lookup table
                                       pk-tempid (get-in pks [pk-table pk-col fk-val])

                                       ;; get the eid of the referenced entity from :tempids lookup table
                                       pk-dbid   (get tempids pk-tempid)]

                                   ;; return a MapEntry with the new value
                                   [fkk pk-dbid]))))]
              r)))))))

;(defn run-fks-old [state]
;  (run-txes state (foreign-keys state)))

(defn run-fks-with [db state]
  (run-txes-with db (foreign-keys state)))

(defn run-fks-real [cx state]
  (run-txes-real cx (foreign-keys state)))

