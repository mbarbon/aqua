(ns aqua.web.globals
  ; no dependencies on aqua.* here
  )

; this is in a separate, rarely modified file to avoid the globals
; being empty on reload

(def *maldump-directory (atom nil))
(def *data-source-rw (atom nil))
(def *data-source-ro (atom nil))
(def *users (atom nil))
(def *anime (atom nil))
(def *co-occurrency (atom nil))
(def *lfd-anime (atom nil))
(def *lfd-anime-airing (atom nil))
(def *lfd-users (atom nil))
(def *suggest (atom nil))
(def *background (atom nil))
(def *state-directory (atom nil))
(def *cf-parameters (atom nil))
