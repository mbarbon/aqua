(ns aqua.web.globals
  ; no dependencies on aqua.* here
  )

; this is in a separate, rarely modified file to avoid the globals
; being empty on reload

(def *data-source-rw (atom nil))
(def *data-source-ro (atom nil))
(def *users (atom nil))
(def *anime (atom nil))
(def *manga (atom nil))
(def *anime-co-occurrency (atom nil))
(def *lfd-anime (atom nil))
(def *lfd-anime-airing (atom nil))
(def *lfd-users (atom nil))
(def *manga-embedding-item-item (atom nil))
(def *suggest (atom nil))
(def *background (atom nil))
(def *state-directory (atom nil))
(def *cf-parameters (atom nil))
(def *anime-list-by-letter (atom nil))
(def *manga-list-by-letter (atom nil))
