(ns scb.wowi-test
  (:require
   ;;[taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clj-http.fake :refer [with-global-fake-routes-in-isolation]]
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
  (testing "typical case, single download, tabber box"
    (let [fixture (fixture-path "wowinterface--addon-detail--single-download--tabber.html")
          url "https://www.wowinterface.com/downloads/info8149-IceHUD.html"

          expected
          {:parsed
           [{:source :wowinterface,
             :source-id 8149,
             ;;:name "icehud" ;; html title cant be trusted, so neither can the name derived from it.
             :updated-date "2022-03-28T09:32:00Z",
             :tag-set #{:buffs :classic :combat :debuffs :the-burning-crusade-classic :ui :unit-frames}
             :game-track-set #{:retail}
             :wowi/title "IceHUD"
             :wowi/url "https://www.wowinterface.com/downloads/info8149-IceHUD.html",
             :wowi/category-set #{"Classic - General" "Combat Mods" "Casting Bars, Cooldowns"
                                  "Buff, Debuff, Spell" "The Burning Crusade Classic" "Unit Mods"}
             :wowi/favourites 1154,
             :wowi/checksum "13c91112524b783847d857a3f84832f0",
             :wowi/latest-release-versions [["Version" "1.13.13"]],
             :wowi/latest-release [{:download-url "https://www.wowinterface.com/downloads/landing.php?fileid=8149",
                                    :game-track :retail}],
             :wowi/web-updated-date "03-28-22 09:32 AM",
             :wowi/downloads 419918,
             :wowi/compatibility ["Visions of N'Zoth (8.3.0)"
                                  "BfA content patch (8.2.5)"]
             :wowi/archived-files
             [{:name "IceHUD",
               :download-url "https://www.wowinterface.com/downloads/getfile.php?id=8149=aid=122738",
               :version "1.13.13-alpha1",
               :size "1MB",
               :author "Parnic",
               :date "2022-03-24T11:25:00Z"}
              {:name "IceHUD",
               :download-url "https://www.wowinterface.com/downloads/getfile.php?id=8149=aid=122631",
               :version "1.13.12",
               :size "1MB",
               :author "Parnic",
               :date "2022-03-22T22:18:00Z"}
              {:name "IceHUD",
               :download-url "https://www.wowinterface.com/downloads/getfile.php?id=8149=aid=122555",
               :version "1.13.12-alpha1",
               :size "1MB",
               :author "Parnic",
               :date "2022-03-22T15:03:00Z"}
              {:name "IceHUD",
               :download-url "https://www.wowinterface.com/downloads/getfile.php?id=8149=aid=122518",
               :version "1.13.11",
               :size "1MB",
               :author "Parnic",
               :date "2022-01-28T11:23:00Z"}
              {:name "IceHUD",
               :download-url "https://www.wowinterface.com/downloads/getfile.php?id=8149=aid=121572",
               :version "1.13.10",
               :size "1MB",
               :author "Parnic",
               :date "2021-11-11T13:02:00Z"}]
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
                    :game-track-list [:retail]
                    :updated-date "2022-03-28T09:32:00Z",
                    :download-count 419918
                    :tag-list [:buffs :classic :combat :debuffs :the-burning-crusade-classic :ui :unit-frames]}]

      (is (= expected (wowi/-to-catalogue-addon addon-data))))))
