(ns aqua.db-utils
  )

(def ^:private byte-array-class  (Class/forName "[B"))

(defn impl-set-arg [^java.sql.PreparedStatement statement index arg]
  (cond
    (nil? arg) (.setNull statement index java.sql.Types/NULL)
    (instance? Long arg) (.setLong statement index arg)
    (instance? Integer arg) (.setInt statement index arg)
    (instance? String arg) (.setString statement index arg)
    (instance? byte-array-class arg) (.setBytes statement index arg)
    :else (throw (Exception. (str "Can't handle class " (type arg) " " arg)))))

(defn- make-set-args [args]
  (map-indexed
    #(list `impl-set-arg (+ %1 1) %2)
    args))

(defn impl-runtime-set-args [rs args]
  (doseq [[index arg] (map list (range) args)]
    (impl-set-arg rs (+ index 1) arg)))

(defn cast-symbol [ty sym]
  (vary-meta sym assoc :tag ty))

(defmacro get-int [rs index]
  `(.getInt ~(vary-meta rs assoc :tag `java.sql.ResultSet) ~index))

(defmacro get-string [rs index]
  `(.getString ~(vary-meta rs assoc :tag `java.sql.ResultSet) ~index))

(defn- get-connection-helper [data-source]
  `(.getConnection ~(vary-meta data-source assoc :tag `javax.sql.DataSource)))

(defmacro with-query
  [data-source rs-name query args & body]
    (let [statement-sym (gensym 'statement_)
          connection (get-connection-helper data-source)
          set-args-list (if (= (type args) clojure.lang.PersistentVector)
                          (make-set-args args)
                          `[(impl-runtime-set-args ~args)])]
      `(with-open [connection# ~connection
                   ~statement-sym (doto (.prepareStatement connection# ~query)
                                        ~@set-args-list)
                   ~rs-name (.executeQuery ~statement-sym)]
        ~@body)))

(defmacro placeholders [items]
  `(clojure.string/join "," (repeat (count ~items) "?")))

(defmacro execute [connection query args]
  (let [typed-connection (cast-symbol 'java.sql.Connection connection)
        set-args-list (if (= (type args) clojure.lang.PersistentVector)
                        (make-set-args args)
                        `[(impl-runtime-set-args ~args)])]
    `(with-open [statement# (doto (.prepareStatement ~typed-connection ~query)
                                  ~@set-args-list)]
       (.execute statement#))))

(defmacro with-transaction [data-source connection-name & body]
  (let [connection (get-connection-helper data-source)]
    `(with-open [~connection-name ~connection]
       (.setAutoCommit ~connection-name false)
       ~@body
       (.commit ~connection-name))))

(defmacro with-connection [data-source connection-name & body]
  (let [connection (get-connection-helper data-source)]
    `(with-open [~connection-name ~connection]
       ~@body)))
