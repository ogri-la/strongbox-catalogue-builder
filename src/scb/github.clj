(ns scb.github
  (:require
   [scb
    [utils :as utils]
    [specs :as sp]]
   [clojure.spec.alpha :as s]
   [clojure.data.csv :as csv]
   [slugify.core :refer [slugify]]
   [orchestra.core :refer [defn-spec]]
   [scb.http :as http]))

(defn-spec build-catalogue (s/or :ok :addon/summary-list, :error nil?)
  "converts a CSV list of addons to a strongbox-compatible catalogue of addon summaries."
  []
  (let [url "https://raw.githubusercontent.com/ogri-la/github-wow-addon-catalogue/main/addons.csv"
        result (-> url
                   http/download-with-backoff
                   http/sink-error
                   :body
                   clojure.string/trim-newline
                   csv/read-csv)

        result-list (apply utils/csv-map result)

        split* (fn [string]
                 (clojure.string/split string #","))

        to-summary
        (fn [row]
          (let [addon {:url (:url row)
                       :name (slugify (:name row))
                       :label (:name row)
                       :tag-list []
                       :updated-date (-> row :last_updated utils/todt str)
                       :download-count 0
                       :source :github
                       :source-id (:full_name row)
                       :description (-> row :description utils/nilable)
                       :game-track-list (->> row
                                             :flavors
                                             split*
                                             (map utils/guess-game-track)
                                             (remove nil?)
                                             vec
                                             utils/nilable)}]
            (utils/drop-nils addon [:description :game-track-list])))]
    (mapv to-summary result-list)))

;;

(defn make-url
  "returns a URL to the given addon data"
  [{:keys [source-id]}]
  (when source-id
    (str "https://github.com/" source-id)))
