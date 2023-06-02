(ns scb.tukui-test
  (:require
   ;;[taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [scb.helper :as helper :refer [fixture-path]]
   [scb
    [core :as core]
    [utils :as utils]
    [tukui :as tukui]]))

(deftest tukui-date-to-rfc3339
  (let [cases [["2022-09-11" "2022-09-11T00:00:00Z"]
               ["2022-09-11 02:05:24" "2022-09-11T02:05:24Z"]]]
    (doseq [[given expected] cases]
      (is (= expected (tukui/tukui-date-to-rfc3339 given))))))

(deftest process-tukui-item--retail
  (let [expected {:description "AlhanaUI is an external Tukui edit that adds additional features and function to the existing Tukui. ",
                  :download-count 48922,
                  :game-track-list [:retail],
                  :label "AlhanaUI",
                  :name "alhanaui",
                  :source :tukui,
                  :source-id 42,
                  :tag-list #{:compilations :ui},
                  :updated-date "2019-07-25T17:00:42Z",
                  :url "https://www.tukui.org/addons.php?id=42"}
        fixture (-> "tukui--retail-addon.json" fixture-path utils/json-slurp first)]
    (is (= expected (tukui/process-tukui-item fixture :retail)))))

(deftest process-tukui-item--classic
  (let [expected {:description "clean and modern UI with Tank/DPS and Heal Layout. transparent and Classcolor Themes are available.",
                  :download-count 5325,
                  :game-track-list [:classic],
                  :label "AlysiaUI_Classic",
                  :name "alysiaui-classic",
                  :source :tukui-classic,
                  :source-id 36,
                  :tag-list #{:elvui :plugins},
                  :updated-date "2020-05-05T20:17:14Z",
                  :url "https://www.tukui.org/classic-addons.php?id=36"}
        fixture (-> "tukui--classic-addon.json" fixture-path utils/json-slurp first)]
    (is (= expected (tukui/process-tukui-item fixture :classic)))))

