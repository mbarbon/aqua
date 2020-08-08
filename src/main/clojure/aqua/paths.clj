(ns aqua.paths)

(def *maldump-directory (atom "maldump"))

(defmacro ^:private model-path [sym file]
  `(defn ~sym []
     (str @*maldump-directory "/" ~file)))

(defmacro ^:private short-model-path [sym]
  `(model-path ~sym ~(str (name sym))))

(short-model-path anime-user-sample)
(short-model-path manga-user-sample)

(short-model-path anime-co-occurrency-model)
(short-model-path anime-co-occurrency-model-airing)
(short-model-path manga-co-occurrency-model)

(short-model-path anime-lfd-model)
(short-model-path anime-lfd-model-airing)
(short-model-path anime-lfd-user-model)
(short-model-path anime-lfd-items-model)
(short-model-path anime-lfd-items-model-airing)
(short-model-path manga-lfd-model)
(short-model-path manga-lfd-user-model)

(short-model-path anime-embedding)
(short-model-path anime-embedding-items-model)
(short-model-path manga-embedding)
(short-model-path manga-embedding-items-model)

(short-model-path anime-rp-model)
(short-model-path anime-rp-model-unfiltered)
(short-model-path manga-rp-model)
(short-model-path manga-rp-model-unfiltered)

(model-path mal-db "maldump.sqlite")
(model-path images "images")
