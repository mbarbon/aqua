(ns aqua.paths)

(def *maldump-directory (atom "maldump"))

(defmacro ^:private model-path [sym file]
  `(defn ~sym []
     (str @*maldump-directory "/" ~file)))

(defmacro ^:private short-model-path [sym]
  `(model-path ~sym ~(str (name sym))))

(short-model-path anime-user-sample)

(short-model-path anime-co-occurrency-model)
(short-model-path anime-co-occurrency-model-airing)

(short-model-path anime-lfd-model)
(short-model-path anime-lfd-model-airing)
(short-model-path anime-lfd-user-model)
(short-model-path anime-lfd-items-model)
(short-model-path anime-lfd-items-model-airing)

(short-model-path anime-rp-model)
(short-model-path anime-rp-model-unfiltered)

(model-path mal-db "maldump.sqlite")
(model-path images "images")
