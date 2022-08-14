(ns scb.tukui
  (:require
   [slugify.core :refer [slugify]]
   [clojure.spec.alpha :as s]
   [clojure.string :refer [lower-case]]
   [orchestra.core :refer [defn-spec]]
   ;;[taoensso.timbre :as log :refer [debug info warn error spy]]
   [scb
    [tags :as tags]
    [http :as http]
    [utils :as utils]
    [specs :as sp]]))

(defn spy
  [x]
  (println "spy:" x)
  x)

(def summary-list-url "https://www.tukui.org/api.php?addons")
(def classic-summary-list-url "https://www.tukui.org/api.php?classic-addons")
(def classic-tbc-summary-list-url "https://www.tukui.org/api.php?classic-tbc-addons")

(def proper-url "https://www.tukui.org/api.php?ui=%s")
(def tukui-proper-url (format proper-url "tukui"))
(def elvui-proper-url (format proper-url "elvui"))

;; catalogue building

(defn download
  [url]
  (-> url http/download-with-backoff :body utils/from-json))

(defn-spec tukui-date-to-rfc3339 ::sp/inst
  "convert a tukui-style datestamp into a mighty RFC3339 formatted one. assumes UTC."
  [tukui-dt string?]
  (let [[date time] (clojure.string/split tukui-dt #" ")]
    (if-not time
      (str date "T00:00:00Z") ;; tukui and elvui addons proper have no time component
      (str date "T" time "Z"))))

(defn-spec process-tukui-item :addon/summary
  "process an item from a tukui catalogue into an addon-summary. slightly different values by game-track."
  [tukui-item map?, game-track ::sp/game-track]
  (let [ti tukui-item
        ;; single case of an addon with no category :(
        ;; 'SkullFlower UI', source-id 143
        category-list (if-let [c (:category ti)]
                        [c]
                        [])
        addon-summary
        {:source (case game-track
                   :retail :tukui
                   :classic :tukui-classic
                   :classic-tbc :tukui-classic-tbc)
         :source-id (-> ti :id utils/to-int)

         ;; 2020-03: disabled in favour of :tag-list
         ;;:category-list category-list
         :tag-list (tags/category-set-to-tag-set "tukui" category-list)
         :download-count (-> ti :downloads utils/to-int)
         :game-track-list [game-track]
         :label (:name ti)
         :name (slugify (:name ti))
         :description (:small_desc ti)
         :updated-date (-> ti :lastupdate tukui-date-to-rfc3339)
         :url (:web_url ti)

         ;; both of these are available in the main download
         ;; however the catalogue is updated weekly and strongbox uses a mechanism of
         ;; checking each for updates rather than relying on the catalogue.
         ;; perhaps in the future when we scrape daily
         ;;:version (:version ti)
         ;;:download-url (:url ti)
         }]

    addon-summary))

(defn-spec -download-proper-summary :addon/summary
  "downloads either the elvui or tukui addon that exists separately and outside of the catalogue"
  [url ::sp/url]
  (let [game-track :retail ;; use retail catalogue
        addon-summary (-> url download (process-tukui-item game-track))]
    (assoc addon-summary :game-track-list [:classic :retail])))

(defn-spec download-elvui-summary :addon/summary
  "downloads the elvui addon that exists separately and outside of the catalogue"
  []
  (-download-proper-summary elvui-proper-url))

(defn-spec download-tukui-summary :addon/summary
  "downloads the tukui addon that exists separately and outside of the catalogue"
  []
  (-download-proper-summary tukui-proper-url))

(defn-spec download-retail-summaries :addon/summary-list
  "downloads and processes all items in the tukui 'live' (retail) catalogue"
  []
  (mapv #(process-tukui-item % :retail) (download summary-list-url)))

(defn-spec download-classic-summaries :addon/summary-list
  "downloads and processes all items in the tukui classic catalogue"
  []
  (mapv #(process-tukui-item % :classic) (download classic-summary-list-url)))

(defn-spec download-classic-tbc-summaries :addon/summary-list
  "downloads and processes all items in the tukui classic catalogue"
  []
  (mapv #(process-tukui-item % :classic-tbc) (download classic-tbc-summary-list-url)))

(defn-spec download-all-summaries :addon/summary-list
  "downloads and process all items from the tukui 'live' (retail) and classic catalogues"
  []
  (vec (concat (download-retail-summaries)
               (download-classic-summaries)
               (download-classic-tbc-summaries)
               [(download-tukui-summary)]
               [(download-elvui-summary)])))

(defn-spec parse-user-string (s/or :ok :addon/source-id, :error nil?)
  "extracts the addon ID from the given `url`, handling the edge cases of for retail tukui and elvui"
  [url ::sp/url]
  (let [[numeral string] (some->> url java.net.URL. .getQuery (re-find #"(?i)(?:id=(\d+)|ui=(tukui|elvui))") rest)]
    (if numeral
      (utils/to-int numeral)
      (case (-> string (or "") lower-case)
        "tukui" -1
        "elvui" -2
        nil))))

(defn build-catalogue
  []
  (utils/with-instrumentation false
    ;; there is a lot of spec checking here that we can save until writing to disk
    (download-all-summaries)))
