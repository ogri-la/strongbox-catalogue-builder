(ns scb.wowi-test
  (:require
   ;;[taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [clojure.test :refer [deftest is testing use-fixtures]]
   ;;[clj-http.fake :refer [with-global-fake-routes-in-isolation]]
   [scb.helper :as helper :refer [fixture-path]]
   [scb
    [core :as core]
    [wowi :as wowi]]))

(use-fixtures :each helper/no-http)
;;(use-fixtures :each helper/temp-state-dir)

(defn as-downloaded-item
  [url fixture]
  {:url url
   :response {:body (slurp fixture)}})

(deftest parse-addon-detail-page
  (testing "typical case, multiple downloads, tabber box"
    (let [fixture (fixture-path "wowinterface--addon-detail--multiple-downloads--tabber.html")
          url "https://www.wowinterface.com/downloads/info8149-IceHUD.html"
          expected
          {:parsed [{:created-date "2010-05-14T12:14:00Z",
                     :game-track-set #{:classic :classic-tbc :retail},
                     :latest-release-set #{{:download-url "https://www.wowinterface.com/downloads/landing.php?fileid=16711",
                                            :game-track :retail,
                                            :version "9.2.0.0"}
                                           {:download-url "https://www.wowinterface.com/downloads/dlfile2723/Broker_PlayedTime-9.1.5.0-classic.zip",
                                            :game-track :classic,
                                            :version "9.1.5.0"}
                                           {:download-url "https://www.wowinterface.com/downloads/dlfile2722/Broker_PlayedTime-9.1.5.0-bcc.zip",
                                            :game-track :classic-tbc,
                                            :version "9.1.5.0"}}
                     :short-web-description "DataBroker plugin to track played time across all your characters.",
                     :source :wowinterface,
                     :source-id 8149,
                     :tag-set #{:achievements
                                :data
                                :data-broker
                                :leveling
                                :quests},
                     :updated-date "2022-02-23T07:14:00Z",
                     :wowi/archived-files [{:date "2021-11-23T04:12:00Z",
                                            :wowi/author "LudiusMaximus",
                                            :wowi/download-url "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=122023",
                                            :wowi/name "Broker Played Time",
                                            :wowi/size "17kB",
                                            :wowi/version "9.1.5.1"}
                                           {:date "2021-11-06T16:26:00Z",
                                            :wowi/author "LudiusMaximus",
                                            :wowi/download-url "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=120940",
                                            :wowi/name "Broker Played Time",
                                            :wowi/size "17kB",
                                            :wowi/version "9.1.5.0"}
                                           {:date "2021-07-01T16:56:00Z",
                                            :wowi/author "LudiusMaximus",
                                            :wowi/download-url "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=119841",
                                            :wowi/name "Broker Played Time",
                                            :wowi/size "17kB",
                                            :wowi/version "9.1.0"}
                                           {:date "2021-03-10T02:49:00Z",
                                            :wowi/author "LudiusMaximus",
                                            :wowi/download-url "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=118006",
                                            :wowi/name "Broker Played Time",
                                            :wowi/size "15kB",
                                            :wowi/version "9.0.5.0"}
                                           {:date "2020-11-23T15:21:00Z",
                                            :wowi/author "LudiusMaximus",
                                            :wowi/download-url "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=115169",
                                            :wowi/name "Broker Played Time",
                                            :wowi/size "15kB",
                                            :wowi/version "9.0.2.0"}
                                           {:date "2020-10-05T15:49:00Z",
                                            :wowi/author "LudiusMaximus",
                                            :wowi/download-url "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=112973",
                                            :wowi/name "Broker Played Time",
                                            :wowi/size "15kB",
                                            :wowi/version "9.0.1.0"}],
                     :wowi/category-set #{"Character Advancement" "Data Broker"},
                     :wowi/checksum "b1a552e9942f865bb34ae4ea80e3f22b",
                     :wowi/compatibility ["Classic Patch (1.14.2)"
                                          "Eternity's End (9.2.0)"
                                          "TBC Patch (2.5.3)"]
                     :wowi/downloads 8093,
                     :wowi/favorites 42,
                     :wowi/latest-release-list [{:download-url "https://www.wowinterface.com/downloads/landing.php?fileid=16711",
                                                 :game-track :retail}
                                                {:download-url "https://www.wowinterface.com/downloads/dlfile2723/Broker_PlayedTime-9.1.5.0-classic.zip",
                                                 :game-track :classic}
                                                {:download-url "https://www.wowinterface.com/downloads/dlfile2722/Broker_PlayedTime-9.1.5.0-bcc.zip",
                                                 :game-track :classic-tbc}],
                     :wowi/latest-release-versions [["Version" "9.2.0.0"]
                                                    ["Classic" "9.1.5.0"]],
                     :wowi/title "Broker Played Time",
                     :wowi/url "https://www.wowinterface.com/downloads/info8149-IceHUD.html",
                     :wowi/web-description ["DataBroker plugin to track played time across all your characters."
                                            "It differs from similar addons in that it only tracks played time; it does not track other things like experience or money."
                                            "The tooltip lists all of your characters across both factions on all servers. The current character is listed first for quick reference. Character names are colored by class. Faction icons, class icons, and character levels can also be shown if desired."
                                            "Right-click the plugin for options.Language Support"
                                            "Works in all locales. Translated into English, Deutsch, Espa�ol, Fran�ais, Italiano, Portugu�s, Русский, 한국어, 简体中文, and 正體中文."
                                            "To add or update translations for any locale, enter them on the Broker Played Time localization page on CurseForge, and then leave a comment, or send me a PM here or on CurseForge, to let me know that you�ve made changes. If you don�t have a Curse account and don�t want to create one, you can PM me your translations instead. Thanks!"]}]}]

      (is (= expected (wowi/parse-addon-detail-page (as-downloaded-item url fixture)))))))

(deftest parse-addon-detail-page-2
  (testing "typical case, single download, tabber box"
    (let [fixture (fixture-path "wowinterface--addon-detail--single-download--tabber.html")
          url "https://www.wowinterface.com/downloads/info8149-IceHUD.html"

          expected
          {:parsed
           [{:source :wowinterface,
             :source-id 8149,
             ;;:name "icehud" ;; html title cant be trusted, so neither can the name derived from it.
             :created-date "2022-03-28T09:32:00Z",
             :updated-date "2022-03-29T09:32:00Z",
             :tag-set #{:buffs :classic :combat :debuffs :the-burning-crusade-classic :ui :unit-frames}
             :game-track-set #{:retail :classic-tbc}
             :latest-release-set #{{:download-url "https://www.wowinterface.com/downloads/landing.php?fileid=8149"
                                    :game-track :retail
                                    :version "1.13.13"}}
             :wowi/title "IceHUD"
             :wowi/url "https://www.wowinterface.com/downloads/info8149-IceHUD.html",
             :wowi/category-set #{"Classic - General" "Combat Mods" "Casting Bars, Cooldowns"
                                  "Buff, Debuff, Spell" "The Burning Crusade Classic" "Unit Mods"}
             :wowi/favorites 1154,
             :wowi/checksum "13c91112524b783847d857a3f84832f0",
             :wowi/latest-release-versions [["Version" "1.13.13"]],
             :wowi/latest-release-list [{:download-url "https://www.wowinterface.com/downloads/landing.php?fileid=8149",
                                         :game-track :retail}],
             ;;:wowi/web-created-date "03-28-22 09:32 AM",
             ;;:wowi/web-updated-date "03-29-22 09:32 AM",
             :wowi/downloads 419918,
             :wowi/compatibility ["Visions of N'Zoth (8.3.0)"
                                  "BfA content patch (8.2.5)"]
             :wowi/archived-files
             [{:wowi/name "IceHUD",
               :wowi/download-url "https://www.wowinterface.com/downloads/getfile.php?id=8149=aid=122738",
               :wowi/version "1.13.13-alpha1",
               :wowi/size "1MB",
               :wowi/author "Parnic",
               :date "2022-03-24T11:25:00Z"}
              {:wowi/name "IceHUD",
               :wowi/download-url "https://www.wowinterface.com/downloads/getfile.php?id=8149=aid=122631",
               :wowi/version "1.13.12",
               :wowi/size "1MB",
               :wowi/author "Parnic",
               :date "2022-03-22T22:18:00Z"}
              {:wowi/name "IceHUD",
               :wowi/download-url "https://www.wowinterface.com/downloads/getfile.php?id=8149=aid=122555",
               :wowi/version "1.13.12-alpha1",
               :wowi/size "1MB",
               :wowi/author "Parnic",
               :date "2022-03-22T15:03:00Z"}
              {:wowi/name "IceHUD",
               :wowi/download-url "https://www.wowinterface.com/downloads/getfile.php?id=8149=aid=122518",
               :wowi/version "1.13.11",
               :wowi/size "1MB",
               :wowi/author "Parnic",
               :date "2022-01-28T11:23:00Z"}
              {:wowi/name "IceHUD",
               :wowi/download-url "https://www.wowinterface.com/downloads/getfile.php?id=8149=aid=121572",
               :wowi/version "1.13.10",
               :wowi/size "1MB",
               :wowi/author "Parnic",
               :date "2021-11-11T13:02:00Z"}]
             :short-web-description "Feel free to  if you enjoy using IceHUD and feel generous.",
             :wowi/web-description
             ["Feel free to  if you enjoy using IceHUD and feel generous."
              "Summary"
              "IceHUD is a highly configurable and customizable HUD addon in the spirit of DHUD, MetaHUD, and others designed to keep your focus in the center of the screen where your character is."
              "What it is"
              "Player and target health and mana bars, casting and mirror bars, pet health and mana bars, druid mana bar in forms, extensive target info, ToT display, and much more"
              "Short feature listLots of different bar shapes and patterns to make the HUD look like you want"
              "Class-specific modules such as combo point counters, Slice'n'dice timers, druid mana trackers, Eclipse bar, Holy Power monitoring, Warlock shard tracking, and more"
              "Target-of-target bars, Crowd Control timers, Range Finders, Threat meters, and plenty of other helpful modules"
              "Lots of DogTag-supported strings for extreme customizability (with the option to completely disable DogTag support for those that dislike the CPU toll that it takes)"
              "Cast lag indicator (optional)"
              "Alpha settings for in combat, target selected, etc."
              "Fully customizable bars and counters capable of tracking buff/debuff applications on any unit, spell/ability cooldowns, and the health/mana of any unit you specify. The custom health/mana bars will even work with crazy unit specifications like \"focustargettargetfocustarget\" if you want!"
              "Highly configurable (can totally re-arrange all bars, change text display, etc.)"
              "Slash commands"
              "/icehud - opens the configuration UI to tweak any setting"
              "/icehudCL - command-line access to tweak IceHUD settings (for use with macros, etc.)"]}]}]
      (is (= expected (wowi/parse-addon-detail-page (as-downloaded-item url fixture)))))))

(deftest -to-catalogue-addon
  (testing "addon detail page can extract enough for a valid category addon"
    (let [fixture (fixture-path "wowinterface--addon-detail--single-download--tabber.html")
          url "https://www.wowinterface.com/downloads/info8149-IceHUD.html"
          result (wowi/parse-addon-detail-page (as-downloaded-item url fixture))
          addon-data (-> result :parsed first)

          expected {:source :wowinterface,
                    :source-id 8149,
                    :url "https://www.wowinterface.com/downloads/info8149-IceHUD.html"
                    ;;:name "icehud" ;; html title can't be trusted so neither can the name derived from it
                    :label "IceHUD"
                    :description "Feel free to  if you enjoy using IceHUD and feel generous.",
                    :game-track-list [:classic-tbc :retail]
                    :created-date "2022-03-28T09:32:00Z",
                    :updated-date "2022-03-29T09:32:00Z",
                    :download-count 419918
                    :tag-list [:buffs :classic :combat :debuffs :the-burning-crusade-classic :ui :unit-frames]}]

      (is (= expected (wowi/-to-catalogue-addon addon-data))))))
