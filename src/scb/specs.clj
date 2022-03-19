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

(s/def ::inst (s/and string? #(try
                                (clojure.instant/read-instant-date %)
                                (catch RuntimeException e
                                  false))))

;;

(s/def :addon/updated-date ::inst)
(s/def :addon/source #{:wowinterface :curseforge :tukui})
(s/def :addon/source-id (s/or ::integer-id? int? ;; tukui has negative ids
                              ::string-id? string?))
(s/def :addon/category string?)
(s/def :addon/category-list (s/coll-of :addon/category :kind set?))

;; a partial set of addon data
(s/def :addon/part (s/keys :req-un [:addon/source-id :addon/source] ;; we need to identify which bit of the addon
                           :opt-un [:addon/name
                                    :addon/label
                                    :addon/url ::url
                                    :addon/updated-date
                                    :addon/download-count
                                    :addon/category-list ::set-of-strings

                                    ]))

(s/def :result/download (s/coll-of ::url-map))
(s/def :result/parsed (s/coll-of :addon/part))

(s/def :result/map (s/keys :opt-un [:result/download :result/parsed :result/error]))

(s/def :http/headers map?)
(s/def :http/response (s/or :html-or-json string?
                            :bytes bytes?
                            ;; ...
                            ))
(s/def :http/response (s/keys :req-un [:http/headers
                                       :http/response
                                       ;; ...
                                       ]))

(s/def :result/downloaded-item (s/keys :req-un [::url :http/response]
                                       :opt-un [::label]))
