(ns scb.specs
  (:require
   [clojure.spec.alpha :as s]
   [me.raynes.fs :as fs]))

(s/def ::url (s/and string?
                    #(try (instance? java.net.URL (java.net.URL. %))
                          (catch java.net.MalformedURLException e
                            false))))

(s/def ::file (s/and string?
                     #(try (and % (java.io.File. %))
                           (catch java.lang.IllegalArgumentException e
                             false))))

(s/def ::extant-file (s/and ::file fs/exists?))
(s/def ::path (s/and ::file #(-> % str (.startsWith "/"))))

;; directory must be a string and a valid File object, but not necessarily exist (yet).
;; can't test if it's a directory until it exists.
(s/def ::dir ::file)
(s/def ::extant-dir (s/and ::dir fs/directory?))

;;

(s/def ::label string?)
(s/def ::url-map (s/keys :req-un [::url]
                         :opt-un [::label]))
