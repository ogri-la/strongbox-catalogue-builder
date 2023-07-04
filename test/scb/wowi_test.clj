(ns scb.wowi-test
  (:require
   ;;[taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [clojure.test :refer [deftest is testing use-fixtures]]
   ;;[clj-http.fake :refer [with-global-fake-routes-in-isolation]]
   [scb.helper :as helper :refer [fixture-path]]
   [scb
    ;;[core :as core]
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
                     :short-description "DataBroker plugin to track played time across all your characters.",
                     :source :wowinterface,
                     :source-id 8149,
                     :filename "web--detail.json"
                     :tag-set #{:achievements
                                :data
                                :data-broker
                                :leveling
                                :quests},
                     :name "broker-played-time",
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
                     :wowi/created-date "05-14-10 12:14 PM"
                     :wowi/updated-date "02-23-22 07:14 AM"
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
                     :wowi/description ["DataBroker plugin to track played time across all your characters."
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
             :filename "web--detail.json"
             :name "icehud"
             :created-date "2022-03-28T09:32:00Z",
             :updated-date "2022-03-29T09:32:00Z",
             :tag-set #{:buffs :classic :combat :debuffs :the-burning-crusade-classic :ui :unit-frames}
             :game-track-set #{:classic-tbc :retail}
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
             :wowi/created-date "03-28-22 09:32 AM",
             :wowi/updated-date "03-29-22 09:32 AM",
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
             :short-description "Feel free to  if you enjoy using IceHUD and feel generous.",
             :wowi/description
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

(deftest -to-addon-summary
  (testing "addon detail page can extract enough for a valid category addon"
    (let [fixture (fixture-path "wowinterface--addon-detail--single-download--tabber.html")
          url "https://www.wowinterface.com/downloads/info8149-IceHUD.html"
          result (wowi/parse-addon-detail-page (as-downloaded-item url fixture))
          addon-data (-> result :parsed first vector)

          expected {:source :wowinterface,
                    :source-id 8149,
                    :url "https://www.wowinterface.com/downloads/info8149-IceHUD.html"
                    :name "icehud"
                    :label "IceHUD"
                    :description "Feel free to  if you enjoy using IceHUD and feel generous.",
                    :game-track-list #{:classic-tbc :retail}
                    :created-date "2022-03-28T09:32:00Z",
                    :updated-date "2022-03-29T09:32:00Z",
                    :download-count 419918
                    :tag-list #{:buffs :classic :combat :debuffs :the-burning-crusade-classic :ui :unit-frames}}]

      (is (= expected (wowi/-to-addon-summary addon-data))))))

;;;

(deftest format-wowinterface-dt
  (testing "conversion"
    (is (= "2018-09-07T13:27:00Z" (wowi/format-wowinterface-dt "09-07-18 01:27 PM")))))

#_(deftest scrape-category-list
    (let [fixture (slurp "test/fixtures/wowinterface-category-list.html")
          fake-routes {"https://www.wowinterface.com/downloads/foobar"
                       {:get (fn [_] {:status 200 :body fixture})}}
          num-categories 52
          first-category {:label "Action Bar Mods",
                          :url "https://www.wowinterface.com/downloads/index.php?cid=19&sb=dec_date&so=desc&pt=f&page=1"}
          last-category {:label "Discontinued and Outdated Mods",
                         :url "https://www.wowinterface.com/downloads/index.php?cid=44&sb=dec_date&so=desc&pt=f&page=1"}]
      (testing "a page of categories can be scraped"
        (with-global-fake-routes-in-isolation fake-routes
          (let [results (wowi/scrape-category-group-page "foobar")]
            (is (= num-categories (count results)))
            (is (= first-category (first results)))
            (is (= last-category (last results))))))))

#_(deftest scrape-category-page-range
    (testing "number of pages in a category is extracted correctly"
      (let [category {:label "dummy" :url "https://www.wowinterface.com/downloads/cat19.html"}
            fixture (slurp "test/fixtures/wowinterface-category-page.html")
            fake-routes {#".*" {:get (fn [_] {:status 200 :body fixture})}}
            expected (range 1 10)]
        (with-global-fake-routes-in-isolation fake-routes
          (is (= expected (wowi/scrape-category-page-range category)))))))

#_(deftest scrape-addon-list
    (testing "a single page of results from a category can be scraped"
      (let [category {:label "dummy" :url "https://www.wowinterface.com/downloads/cat19.html"}
            fixture (slurp "test/fixtures/wowinterface-category-page.html")
            fake-routes {"https://www.wowinterface.com/downloads/cat19.html"
                         {:get (fn [_] {:status 200 :body fixture})}}
            page 1
            num-addons 25
            first-addon {:url "https://www.wowinterface.com/downloads/info25079",
                         :name "rotation-master",
                         :label "Rotation Master",
                         :updated-date "2019-07-29T21:37:00Z",
                         :download-count 80,
                         :category-list #{"dummy"}
                         :source "wowinterface"
                         :source-id 25079}
            last-addon  {:url "https://www.wowinterface.com/downloads/info24805",
                         :name "mattbars-mattui",
                         :label "MattBars (MattUI)",
                         :updated-date "2018-10-30T17:56:00Z",
                         :download-count 1911,
                         :category-list #{"dummy"}
                         :source "wowinterface"
                         :source-id 24805}]
        (with-global-fake-routes-in-isolation fake-routes
          (let [results (wowi/scrape-addon-list category page)]
            (is (= num-addons (count results)))
            (is (= first-addon (first results)))
            (is (= last-addon (last results))))))))

#_(deftest addon-game-tracks-detected
    (testing "retail, classic and classic-tbc are successfully detected"
      (let [filelist {24876 [{:UIDownloadTotal "1223",
                              :UID 24876,
                              :UIDir ["TSM_StringConverter"],
                              :UIName "TradeSkillMaster String Converter",
                              :UISiblings nil,
                              :UIFileInfoURL "https://www.wowinterface.com/downloads/info24876-TradeSkillMasterStringConverter.html",
                              :UIDate 1619013116000,
                              :UIDonationLink "https://www.wowinterface.com/downloads/info24876#donate",
                              :UIIMGs ["https://cdn-wow.mmoui.com/preview/pvw70766.jpg" "https://cdn-wow.mmoui.com/preview/pvw70767.jpg" "https://cdn-wow.mmoui.com/preview/pvw70768.jpg"],
                              :UIDownloadMonthly "30",
                              :UICompatibility [{:version "2.5.1", :name "The Burning Crusade Classic"}
                                                {:version "9.0.5", :name "Shadowlands patch"}
                                                {:version "1.13.7", :name "Classic Patch"}],
                              :UIAuthorName "myrroddin",
                              :UIVersion "2.0.7",
                              :UIIMG_Thumbs ["https://cdn-wow.mmoui.com/preview/tiny/pvw70766.jpg" "https://cdn-wow.mmoui.com/preview/tiny/pvw70767.jpg" "https://cdn-wow.mmoui.com/preview/tiny/pvw70768.jpg"],
                              :UICATID "40",
                              :UIFavoriteTotal "1"}]}

            addon  {:url "https://www.wowinterface.com/downloads/info24876",
                    :name "tradeskillmaster-string-converter",
                    :label "TradeSkillMaster String Converter",
                    :source "wowinterface",
                    :source-id 24876,
                    :updated-date "2021-04-21T07:51:00Z",
                    :download-count 1223,
                    :category-list #{"Bags, Bank, Inventory"}}

            expected {:game-track-list [:classic :classic-tbc :retail],
                      :url "https://www.wowinterface.com/downloads/info24876"
                      :name "tradeskillmaster-string-converter",
                      :label "TradeSkillMaster String Converter",
                      :source "wowinterface",
                      :source-id 24876,
                      :updated-date "2021-04-21T07:51:00Z",
                      :download-count 1223,
                      :category-list #{"Bags, Bank, Inventory"}}]

        (is (= expected (wowi/expand-addon-with-filelist filelist addon))))))

#_(deftest addon-game-tracks-detected--null-compatibility
    (testing "when 'UICompatibility' is `null` we default to `:retail`."
      (let [expected [:retail]]
        (is (= expected (wowi/ui-compatibility-to-gametrack-list nil))))))

