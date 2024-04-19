(ns scb.main
  (:require
   [orchestra.core :refer [defn-spec]]
   [taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [me.raynes.fs :as fs]
   [clojure.tools.cli]
   [clojure.string]
   [scb
    [core :as core]
    [specs :as specs]
    [catalogue :as catalogue]
    [wowi :as wowi]
    [github :as github]
    [utils :as utils]
    [http :as http]]))

(defn wait-for-empty-queues
  "blocks the thread until all queues are empty for a few seconds"
  []
  (let [;; count items in all queues ...
        check #(apply + (mapv core/queue-size core/queue-list))]
    ;; then loop until they're 0 three times in a row
    (loop [num-checks 0
           total-queue-items (check)]
      (println (str (format "checking ... %s total queue items" total-queue-items)
                    (if (> num-checks 0) (format " (%s)" (inc num-checks)) "")))
      (when (or (< num-checks 2)
                (not (zero? total-queue-items)))
        (Thread/sleep (if (< total-queue-items 1000) 2000 5000)) ;; sleep longer when the number of items is over 1000
        (recur (if (not (zero? total-queue-items)) 0 (inc num-checks)) ;; only increment checks if the number of items has hit zero
               (check))))))

(defn clear-recent-urls
  "because processing a url may yield urls that have already been processed in the current run,
  recent-urls builds up a list of urls in state as addons are being scraped and then skips processing of already-visited urls.
  this list of urls needs to be wiped between scrapes otherwise urls will be skipped."
  []
  (swap! core/state assoc :recent-urls #{})
  nil)

(defn download-url
  "adds a url to the queue to be downloaded."
  [url]
  (clear-recent-urls)
  (core/put-item (core/get-state :download-queue) url)
  nil)

(defn-spec write-addon-details nil?
  "generates a `detail.json` file"
  [source :addon/source, source-id :addon/source-id]
  (let [output-path (core/paths :state-path (name source) source-id "detail.json")
        addon-data (core/to-addon-detail (core/find-read-addon-data source source-id))]
    (when addon-data
      (core/write-addon-data output-path addon-data))))

(defn-spec write-all-addon-details nil?
  "write `detail.json` for all addons"
  []
  (let [;; [[:wowinterface "5673"], [:wowinterface "5607"], ...]
        id-key (juxt (comp keyword str fs/base-name fs/parent)
                     (comp str fs/base-name))]
    (dorun (pmap #(apply write-addon-details (id-key %)) (core/state-paths-matching "*/*")))))

;; ---

(defn refresh-data
  []
  ;; rm all .json files in state/
  ;; ...

  ;; remove memory of any recently scraped urls
  (clear-recent-urls)

  ;; scrape all of the html
  (download-url "https://www.wowinterface.com/addons.php")
  ;; scrape all of the api
  (download-url wowi/api-file-list))

(defn delete-cache-path
  "deletes a cache file rooted in the `:cache-http-path` directory."
  [[cache-path url]]
  (warn "deleting cache:" url)
  (utils/safe-delete-file cache-path (core/paths :cache-http-path))
  nil)

(defn delete-addon-cache
  "deletes cache files for given addon"
  [source source-id]
  (run! delete-cache-path
        (core/cache-paths-matching (re-pattern (format "info%s$|%s\\.json$" source-id source-id)))))

;; ---

(defn marshall-catalogue
  "reads addon data for each source in given `source-list` (or all known sources) and returns a single list of addons."
  [source-list]
  (let [source-map {:wowinterface wowi/build-catalogue
                    :github github/build-catalogue}
        source-map (select-keys source-map (or source-list (keys source-map)))
        addon-list (vec (mapcat #((second %)) source-map))
        addon-list (filterv (fn [addon]
                              (let [valid-addon? (specs/valid-or-nil :addon/summary addon)]
                                (when (not valid-addon?)
                                  (warn (format "failed to validate final addon for catalogue, excluding: %s (%s)" (:source-id addon) (:source addon))))
                                valid-addon?)) addon-list)]
    (catalogue/format-catalogue-data-for-output addon-list (utils/datestamp-now-ymd))))

(defn write-catalogue
  "generates a catalogue for each source in `source-list` and writes the corresponding catalogue to disk.
  if `source-list` is empty/nil, then a catalogue for all known sources, a shortened and full catalogue are written to disk."
  [& [{:keys [source-list]}]]
  (let [all-catalogue-data (marshall-catalogue source-list)

        source (fn [& source-list]
                 (fn [row]
                   (some #{(:source row)} (set source-list))))

        source-map {:wowinterface #(catalogue/filter-catalogue (source :wowinterface) %)
                    :github #(catalogue/filter-catalogue (source :github) %)
                    :short #(catalogue/shorten-catalogue %)
                    :full identity}

        source-path-map {:wowinterface "wowinterface-catalogue.json"
                         :github "github-catalogue.json"
                         :short "short-catalogue.json"
                         :full "full-catalogue.json"}

        source-order [:wowinterface :github :full :short]

        source-list (if (empty? source-list) source-order source-list)]

    (doseq [source source-list
            :let [data ((get source-map source) all-catalogue-data)
                  path (get source-path-map source)]]
      (info source)
      (catalogue/write-catalogue data (core/paths :catalogue-path path)))))

;; ---

(defn daily-wowi-update
  "deletes the listing pages and API filelist cache,
  refreshes addon data, downloading missing cache files as necessary,
  finds addons updated in the last day using fresh data,
  deletes their cache, refreshes data again.
  remember: we can't trust the API filelist or listing pages to be complete, we have to consult both."
  []
  (run! delete-cache-path (core/cache-paths-matching #"page=|cat|filelist"))
  (refresh-data)
  (wait-for-empty-queues)

  (let [age 7 ;; days. 2 days will ensure nothing is missed

        updated-recently-from-listings
        (->> (core/state-paths-matching "wowinterface/*/listing--*")
             (group-by (comp str fs/base-name fs/parent)) ;; {"1234" [/path/to/state/1234/listing--combat-mods, ...], ...}
             vals
             (map first) ;; there will be 0 or many listing--* files, we want the first
             (remove nil?) ;; if there are zero, first will give us nils
             (map core/read-addon-path)
             (filterv (comp (partial utils/less-than-n-days-old? age) :updated-date)))

        updated-recently-from-filedetails
        (->> (wowi/parse-api-file-list {:url wowi/api-file-list
                                        :response (http/download wowi/api-file-list {})})
             :parsed
             (map :updated-date)
             (remove nil?)
             (filterv (partial utils/less-than-n-days-old? age)))

        ;; #{ [:wowinterface 1234], [:wowinterface 4321], ... }
        addon-id (juxt :source :source-id)
        updated-recently (set (into (map addon-id updated-recently-from-listings)
                                    (map addon-id updated-recently-from-filedetails)))]

    (run! (partial apply delete-addon-cache) updated-recently)
    (refresh-data) ;; shouldn't we just re-scrape those updated recently? it's cached data but still slow ...
    (wait-for-empty-queues)
    (write-all-addon-details)

    nil))

(defn daily-addon-update
  [& [{:keys [source-list]}]]
  (let [source-map {:wowinterface daily-wowi-update}
        source-map (select-keys source-map (or source-list (keys source-map)))]
    (run! #((second %)) source-map)
    ;; important! pass the original source-list and not one derived from the source-map here.
    (write-catalogue {:source-list source-list})))

;; ---

(def action-map
  {:scrape-catalogue #(daily-addon-update)
   :scrape-wowinterface-catalogue #(daily-addon-update {:source-list [:wowinterface]})
   ;; no distinction between scraping and writing
   :scrape-github-catalogue #(daily-addon-update {:source-list [:github]})

   :write-catalogue #(write-catalogue)
   :write-wowinterface-catalogue #(write-catalogue {:source-list [:wowinterface]})
   ;; no distinction between scraping and writing
   :write-github-catalogue #(write-catalogue {:source-list [:github]})})

(defn start
  [{:keys [action]}]
  (core/start)
  ((action action-map)))

(defn stop
  []
  (core/stop)
  ;; shouldn't need this if workers are cleaned up properly
  ;;(shutdown-agents)
  )

(defn shutdown-hook
  "called as app exits"
  []
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. ^Runnable stop)))

(def catalogue-actions (set (keys action-map)))
(def catalogue-action-str (clojure.string/join ", " (mapv #(format "'%s'" (name %)) (sort catalogue-actions))))

(def cli-options
  [["-h" "--help"]

   ["-v" "--verbosity LEVEL" "level is one of 'debug', 'info', 'warn', 'error', 'fatal'. default is 'info'"
    :id :min-level
    :default :info
    :parse-fn #(-> % clojure.string/lower-case keyword)
    :validate [#(contains? #{:debug :info :warn :error :fatal} %)]]

   ["-a" "--action ACTION" (str "perform action and exit. action is one of: 'list', 'list-updates', 'update-all'," catalogue-action-str)
    :id :action
    :default :scrape-catalogue
    :parse-fn #(-> % clojure.string/lower-case keyword)
    :validate [#(contains? action-map %)]]])

(defn usage
  [parsed-opts]
  (str "Usage: ./scb [--action <action>]\n\n" (:summary parsed-opts)))

(defn validate
  [parsed]
  (let [{:keys [options errors]} parsed]
    (cond
      (= "root" (System/getProperty "user.name")) {:ok? false, :exit-message "do not run as the 'root' user"}

      (:help options) {:ok? true, :exit-message (usage parsed)}

      errors {:ok? false, :exit-message (str "The following errors occurred while parsing command:\n\n"
                                             (clojure.string/join \newline errors))}
      :else parsed)))

(defn parse
  [args]
  (clojure.tools.cli/parse-opts args cli-options))

(defn exit
  [status & [msg]]
  (when msg
    (println msg))
  (System/exit status))

(defn -main
  [& args]
  (let [{:keys [options exit-message ok?]} (-> args parse validate)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (do (shutdown-hook)
          (start options)
          (exit 0)))))
