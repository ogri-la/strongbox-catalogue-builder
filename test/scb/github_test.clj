(ns scb.github-test
  (:require
   ;;[taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clj-http.fake :refer [with-global-fake-routes-in-isolation]]
   [scb.helper :as helper]
   [scb
    [github :as github]]))

(deftest build-catalogue
  (let [expected [{:description "Allows filtering of premade applicants using advanced filter expressions.",
                   :download-count 0,
                   :game-track-list [:retail],
                   :label "premade-applicants-filter",
                   :name "premade-applicants-filter",
                   :source "github",
                   :source-id "0xbs/premade-applicants-filter",
                   :tag-list [],
                   :updated-date "2021-12-26T09:40:18Z"
                   :url "https://github.com/0xbs/premade-applicants-filter"}
                  {:download-count 0,
                   :game-track-list [:classic-tbc :classic :retail],
                   :label "ArenaLeaveConfirmer",
                   :name "arenaleaveconfirmer",
                   :source "github",
                   :source-id "AlexFolland/ArenaLeaveConfirmer",
                   :tag-list [],
                   :updated-date "2021-07-04T22:12:06Z",
                   :url "https://github.com/AlexFolland/ArenaLeaveConfirmer"}
                  {:download-count 0,
                   :game-track-list [:classic-tbc :classic :retail],
                   :label "BattlegroundSpiritReleaser",
                   :name "battlegroundspiritreleaser",
                   :source "github",
                   :source-id "AlexFolland/BattlegroundSpiritReleaser",
                   :tag-list [],
                   :updated-date "2021-07-04T21:55:31Z",
                   :url "https://github.com/AlexFolland/BattlegroundSpiritReleaser"}
                  {:description "AltReps is an addon that allows you to track reputations across your characters",
                   :download-count 0,
                   :game-track-list [:retail],
                   :label "AltReps",
                   :name "altreps",
                   :source "github",
                   :source-id "Alastair-Scott/AltReps",
                   :tag-list [],
                   :updated-date "2021-12-03T00:26:27Z",
                   :url "https://github.com/Alastair-Scott/AltReps"}
                  {:description "Makes system chat messages prettier and tidier, and reduces the need for multiple chat windows."
                   :download-count 0,
                   :game-track-list [:classic-tbc :classic :retail],
                   :label "ChatCleaner",
                   :name "chatcleaner",
                   :source "github",
                   :source-id "GoldpawsStuff/ChatCleaner",
                   :tag-list [],
                   :updated-date "2021-12-15T21:17:51Z"
                   :url "https://github.com/GoldpawsStuff/ChatCleaner"}]
        fixture (slurp (helper/fixture-path "github-catalogue--dummy.csv"))
        fake-routes {"https://raw.githubusercontent.com/ogri-la/github-wow-addon-catalogue/main/addons.csv"
                     {:get (fn [req] {:status 200 :body fixture})}}]
    (with-global-fake-routes-in-isolation fake-routes
      (is (= expected (github/build-catalogue))))))
