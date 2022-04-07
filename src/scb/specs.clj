(ns scb.specs
  (:require
   [clojure.spec.alpha :as s]
   [me.raynes.fs :as fs]))

(s/def ::url (s/and string?
                    #(try (instance? java.net.URL (java.net.URL. %))
                          (catch java.net.MalformedURLException e
                            false))))

(s/def ::file (s/and string?
                     #(try (and % (java.io.File. ^String %))
                           (catch java.lang.IllegalArgumentException e
                             false))))

(s/def ::extant-file (s/and ::file fs/exists?))
(s/def ::path (s/and ::file #(-> % str (.startsWith "/"))))

;; directory must be a string and a valid File object, but not necessarily exist (yet).
;; can't test if it's a directory until it exists.
(s/def ::dir ::file)
(s/def ::extant-dir (s/and ::dir fs/directory?))

;;

(s/def ::name string?)
(s/def ::label string?)
(s/def ::url-map (s/keys :req-un [::url]
                         :opt-un [::label]))

(s/def ::inst (s/and string? #(try
                                (clojure.instant/read-instant-date %)
                                (catch RuntimeException e
                                  false))))

(s/def ::empty-coll (s/and coll? empty?))

;;

(s/def ::game-track #{:retail :classic :classic-tbc})
(s/def ::game-track-list (s/coll-of ::game-track :kind vector? :distinct true))

;;

(s/def :addon/created-date ::inst)
(s/def :addon/updated-date ::inst)

;;(s/def ::ymd-dt (s/and string? #(try
;;                                  (java-time/local-date %)
;;                                  (catch RuntimeException e
;;                                    false))))

(s/def :addon/source #{:wowinterface :curseforge :tukui})
(s/def :addon/source-id (s/or ::integer-id? int? ;; tukui has negative ids
                              ::string-id? string?))
(s/def :addon/category string?)
(s/def :addon/category-list (s/coll-of :addon/category :kind set?))

(s/def :addon/download-count int?)

;; a partial set of addon data
(s/def :addon/part (s/keys :req-un [:addon/source-id :addon/source] ;; we need to identify which bit of the addon
                           :opt-un [::name
                                    ::label
                                    ::url
                                    :addon/created-date
                                    :addon/updated-date
                                    :addon/download-count
                                    :addon/category-list ::set-of-strings]))

(s/def :addon/tag keyword?)
(s/def :addon/tag-list (s/or :ok (s/coll-of :addon/tag)
                             :empty ::empty-coll))

;; a catalogue entry, essentially
(s/def :addon/summary
  (s/keys :req-un [::url
                   ::name
                   ::label
                   :addon/tag-list
                   :addon/updated-date
                   ::download-count
                   :addon/source
                   :addon/source-id]
          :opt-un [::description ;; github may not have a description.
                   :addon/created-date ;; wowinterface summaries have no created date
                   ::game-track-list   ;; more of a set, really
                   ]))
(s/def :addon/summary-list (s/coll-of :addon/summary))

;; --- catalogues

(s/def :catalogue/version int?)
(s/def :catalogue/spec (s/keys :req-un [:catalogue/version]))
(s/def :catalogue/datestamp ::inst)
(s/def :catalogue/total int?)
(s/def :catalogue/addon-summary-list :addon/summary-list)

(s/def :catalogue/catalogue (s/and (s/keys :req-un [:catalogue/spec :catalogue/datestamp :catalogue/total :catalogue/addon-summary-list])
                                   (fn [data]
                                     (= (:total data) (count (:addon-summary-list data))))))

;; --- results

(s/def :result/download (s/coll-of (s/or :map ::url-map, :string ::url)))
(s/def :result/parsed (s/coll-of :addon/part))

(s/def :result/map (s/keys :opt-un [:result/download :result/parsed]))

(s/def :http/headers map?)

(s/def :http/body (s/or :html-or-json string?
                        :bytes bytes?
                        ;; ...
                        ))

(s/def :http/response (s/keys :req-un [:http/body
                                       ;; ...
                                       ]
                              :opt-un [:http/headers ;; typically not present during testing
                                       ]))

(s/def :result/downloaded-item (s/keys :req-un [::url :http/response]
                                       :opt-un [::label]))
