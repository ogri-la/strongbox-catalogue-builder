(ns scb.main
  (:require
   [clojure.tools.cli]
   [clojure.string]
   [scb
    [core :as core]
    [user :as user]]))

(defn start
  [{:keys [action]}]
  (core/start)
  (case action
    :write-catalogue (user/write-catalogue)
    :scrape-catalogue (user/daily-addon-update)
    :scrape-wowinterface-catalogue (user/daily-addon-update)
    ;;;:scrape-github-catalogue ...
    ))

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

(def catalogue-actions
  #{:write-catalogue
    :scrape-catalogue
    ;;:scrape-github-catalogue
    :scrape-wowinterface-catalogue
    ;;:scrape-tukui-catalogue
    })

(def catalogue-action-str (clojure.string/join ", " (mapv #(format "'%s'" (name %)) (sort catalogue-actions))))

(def cli-options
  [["-h" "--help"]

   ["-v" "--verbosity LEVEL" "level is one of 'debug', 'info', 'warn', 'error', 'fatal'. default is 'info'"
    :parse-fn #(-> % clojure.string/lower-case keyword)
    :validate [#(some #{%} #{:debug :info :warn :error :fatal})]]

   ["-a" "--action ACTION" (str "perform action and exit. action is one of: 'list', 'list-updates', 'update-all'," catalogue-action-str)
    :id :action
    :default :scrape-catalogue
    :parse-fn #(-> % clojure.string/lower-case keyword)
    :validate [#(some #{%} catalogue-actions)]]])

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
