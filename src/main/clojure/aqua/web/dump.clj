(ns aqua.web.dump
  (:require aqua.mal-local
            [aqua.web.globals :refer [*data-source-ro *data-source-rw]]))

(defn- to-page [key id-map]
  {key id-map
   "last_page" (if (seq id-map)
                 (apply max (keys id-map))
                 -1)})

(defn all-anime-ids [{after_id "after_id" limit "count"}]
  (to-page "anime" (aqua.mal-local/all-anime-ids @*data-source-ro after_id limit)))

(defn all-user-ids [{after_id "after_id" limit "count"}]
  (to-page "users" (aqua.mal-local/all-user-ids @*data-source-ro after_id limit)))

(defn changed-users [{users "users"}]
  (aqua.mal-local/select-changed-users @*data-source-ro users))

(defn changed-anime [{anime "anime"}]
  (aqua.mal-local/select-changed-anime @*data-source-ro anime))

(defn store-users [{users "users"}]
  (aqua.mal-local/store-users @*data-source-rw users))

(defn store-anime [{anime "anime"}]
  (aqua.mal-local/store-anime @*data-source-rw anime))
