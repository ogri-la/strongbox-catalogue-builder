(ns scb.wowi-test
  (:require
   [me.raynes.fs :as fs]
   ;;[taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [clojure.test :refer [deftest is testing use-fixtures]]
   ;;[clj-http.fake :refer [with-global-fake-routes-in-isolation]]
   [scb.helper :as helper :refer [fixture-path]]
   [clojure.pprint]
   [scb
    ;;[core :as core]
    [wowi :as wowi]]))

(use-fixtures :each helper/no-http)
;;(use-fixtures :each helper/temp-state-dir)

(defn spy
  [x]
  (clojure.pprint/pprint x)
  x)

(defn as-downloaded-item
  [url fixture]
  {:url url
   :response {:body (slurp fixture)}})

(deftest parse-addon-detail-page
  (testing "typical case, multiple downloads, tabber box"
    (let [fixture (fixture-path "wowinterface--addon-detail--multiple-downloads--tabber.html")
          url "https://www.wowinterface.com/downloads/info8149-IceHUD.html"

          expected
          {:parsed
           [{:wowi/url
             "https://www.wowinterface.com/downloads/info8149-IceHUD.html",
             :created-date "2010-05-14T12:14:00Z",
             :wowi/archived-files
             [{:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=140355",
               :wowi/version "10.2.5.0",
               :wowi/size "16kB",
               :wowi/author "LudiusMaximus",
               :date "2024-01-17T03:22:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=138953",
               :wowi/version "10.2.0.1",
               :wowi/size "16kB",
               :wowi/author "LudiusMaximus",
               :date "2023-11-21T01:58:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=137590",
               :wowi/version "10.2.0.0",
               :wowi/size "16kB",
               :wowi/author "LudiusMaximus",
               :date "2023-11-11T17:21:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=137255",
               :wowi/version "10.1.7.1",
               :wowi/size "16kB",
               :wowi/author "LudiusMaximus",
               :date "2023-10-15T17:48:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=136578",
               :wowi/version "10.1.7.0",
               :wowi/size "16kB",
               :wowi/author "LudiusMaximus",
               :date "2023-09-08T01:08:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=135917",
               :wowi/version "10.1.5.2",
               :wowi/size "16kB",
               :wowi/author "LudiusMaximus",
               :date "2023-08-23T11:45:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=135496",
               :wowi/version "10.1.5.1",
               :wowi/size "16kB",
               :wowi/author "LudiusMaximus",
               :date "2023-07-18T15:57:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=134927",
               :wowi/version "10.1.5.0",
               :wowi/size "15kB",
               :wowi/author "LudiusMaximus",
               :date "2023-07-12T14:15:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=134743",
               :wowi/version "10.1.0.0",
               :wowi/size "15kB",
               :wowi/author "LudiusMaximus",
               :date "2023-05-04T00:47:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=133276",
               :wowi/version "10.0.7.0",
               :wowi/size "15kB",
               :wowi/author "LudiusMaximus",
               :date "2023-04-05T01:35:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=132694",
               :wowi/version "10.0.5.0",
               :wowi/size "15kB",
               :wowi/author "LudiusMaximus",
               :date "2023-03-18T18:35:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=132151",
               :wowi/version "10.0.2.0",
               :wowi/size "16kB",
               :wowi/author "LudiusMaximus",
               :date "2022-11-25T18:15:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=129059",
               :wowi/version "9.2.7.0",
               :wowi/size "17kB",
               :wowi/author "LudiusMaximus",
               :date "2022-08-30T09:44:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=125239",
               :wowi/version "9.2.5.0",
               :wowi/size "17kB",
               :wowi/author "LudiusMaximus",
               :date "2022-06-12T02:48:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=124093",
               :wowi/version "9.2.5.0",
               :wowi/size "17kB",
               :wowi/author "LudiusMaximus",
               :date "2022-06-12T02:48:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=124088",
               :wowi/version "9.2.5.0",
               :wowi/size "17kB",
               :wowi/author "LudiusMaximus",
               :date "2022-06-12T02:48:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=124081",
               :wowi/version "9.2.0.0",
               :wowi/size "17kB",
               :wowi/author "LudiusMaximus",
               :date "2022-02-23T07:14:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=122023",
               :wowi/version "9.1.5.1",
               :wowi/size "17kB",
               :wowi/author "LudiusMaximus",
               :date "2021-11-23T04:12:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=120940",
               :wowi/version "9.1.5.0",
               :wowi/size "17kB",
               :wowi/author "LudiusMaximus",
               :date "2021-11-06T16:26:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=119841",
               :wowi/version "9.1.0",
               :wowi/size "17kB",
               :wowi/author "LudiusMaximus",
               :date "2021-07-01T16:56:00Z"}
              {:wowi/name "Broker Played Time",
               :wowi/download-url
               "https://www.wowinterface.com/downloads/getfile.php?id=16711=aid=118006",
               :wowi/version "9.0.5.0",
               :wowi/size "15kB",
               :wowi/author "LudiusMaximus",
               :date "2021-03-10T02:49:00Z"}],
             :updated-date "2024-03-22T16:59:00Z",
             :wowi/created-date "05-14-10 12:14 PM",
             :short-description
             "DataBroker plugin to track played time across all your characters.",
             :name "broker-played-time",
             :wowi/description
             ["DataBroker plugin to track played time across all your characters."
              "It differs from similar addons in that it only tracks played time; it does not track other things like experience or money."
              "The tooltip lists all of your characters across both factions on all servers. The current character is listed first for quick reference. Character names are colored by class. Faction icons, class icons, and character levels can also be shown if desired."
              "Right-click the plugin for options.Language Support"
              "Works in all locales. Translated into English, Deutsch, Espa�ol, Fran�ais, Italiano, Portugu�s, Русский, 한국어, 简体中文, and 正體中文."
              "To add or update translations for any locale, enter them on the Broker Played Time localization page on CurseForge, and then leave a comment, or send me a PM here or on CurseForge, to let me know that you�ve made changes. If you don�t have a Curse account and don�t want to create one, you can PM me your translations instead. Thanks!Option examples"],
             :latest-release-set
             #{{:download-url
                "https://www.wowinterface.com/downloads/landing.php?fileid=16711",
                :game-track :retail,
                :version "10.2.6.0"}
               {:download-url
                "https://www.wowinterface.com/downloads/landing.php?fileid=16711",
                :game-track :classic-wotlk,
                :version "10.2.6.0"}
               {:download-url
                "https://www.wowinterface.com/downloads/landing.php?fileid=16711",
                :game-track :classic,
                :version "10.2.6.0"}
               {:download-url
                "https://www.wowinterface.com/downloads/landing.php?fileid=16711",
                :game-track :classic-tbc,
                :version "10.2.6.0"}},
             :wowi/updated-date "03-22-24 04:59 PM",
             :source :wowinterface,
             :wowi/checksum "74a57b977aa3dc108ca47ce0365c33c8",
             :wowi/compatible-with "Compatible with Retail, Classic & TBC",
             :wowi/latest-release-versions [["Version" "10.2.6.0"]],
             :wowi/title "Broker Played Time",
             :filename "web--detail.json",
             :tag-set #{:data-broker :leveling :quests :achievements :data},
             :source-id 8149,
             :wowi/latest-release-list
             [{:download-url
               "https://www.wowinterface.com/downloads/landing.php?fileid=16711",
               :game-track :retail}],
             :game-track-set #{:classic-wotlk :retail :classic-tbc :classic},
             :wowi/downloads 8522,
             :wowi/category-set #{"Data Broker" "Character Advancement"},
             :wowi/compatibility
             ["Plunderstorm (10.2.6)"
              "Classic (1.15.1)"
              "WOTLK Patch (3.4.3)"
              "TBC Patch (2.5.4)"]
             :wowi/favorites 43}]}

          actual (wowi/parse-addon-detail-page (as-downloaded-item url fixture))
          ;;_ (spy actual)
          ]

      (is (= expected actual)))))

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

(deftest wowi-html-addon-detail
  (testing "foo"
    (let [url "https://www.wowinterface.com/downloads/info25287-Skillet-Classic.html"
          fixture (->> "test/fixtures/wowinterface--addon-detail--multiple-downloads--no-tabber.html"
                       fs/absolute
                       fs/normalized
                       str)

          expected
          {:parsed
           [{:created-date "2019-09-11T14:22:00Z",
             :updated-date "2024-04-05T14:32:00Z",
             :wowi/created-date "09-11-19 02:22 PM",
             :short-description
             "Skillet-Classic: A trade skill window replacement for Classic WoW",
             :name "skillet-classic",
             :wowi/description
             ["Skillet-Classic: A trade skill window replacement for Classic WoW"
              "This download supports both Classic Era and Burning Crusade Classic."
              "For Skillet on Shadowlands, see https://www.wowinterface.com/downloa...1-Skillet.html"
              "If you find any bugs, you can report them here: https://github.com/b-morgan/Skillet-Classic/issues"
              "If you would like to contribute to localization, you can do so at https://www.curseforge.com/wow/addon...c/localization"
              "FeaturesLarger the the standard tradeskill window"
              "Built-in queue for creating multiple, different items"
              "Queued items are saved when you log out and are restored on log in"
              "Automatically buy reagents for queued recipes when visiting a vendor"
              "If you can craft a reagent needed by a recipe, then clicking on that reagent will take you to its recipe (same features as Reverse Engineering)"
              "If the item to be crafted requires a minimum level to use, that level can be displayed along with the recipe (disabled by default)"
              "The shopping list of items needed for all queued recipes for all alts can be displayed at banks, auction houses, or from the command line"
              "Items needed for crafting queued items can be automatically retrieved from your bank (by using the shopping list)"
              "User editable list of notes attached to reagents and crafted items"
              "Queued counts added to (optional) notes display"
              "Crafted counts can be adjusted with Right-click and Shift-right-click on the item icon in the detail frame"
              "Recipes can be filtered by name, whether or not you could level when creating the item, and whether or not you have the mats available"
              "Sorting of recipes (name, difficulty, level, and quality of crafted item)"
              "Tracking inventory on alternate characters"
              "Plugin support for (limited) modification of the Skillet frame by other addons"
              "Custom grouping"
              "User managed Ignored Materials List"
              "Complete or mostly complete localizations for deDE, esES, frFR, ruRU, koKR, zhCN, zhTW"
              "FAQWhat are the numbers in the middle and how to hide them? - It's the number of craftable items using reagents in your bag, bank, alts. Right+Click on the bag icon to turn them off"
              "How to search in the item name only? - Start your search phrase with exclamation mark: !ink"
              "How to search in Auction House? - Alt+Click on shopping list"
              "How to retrieve items from bank? - Turn on \"Display shopping list at banks\""
              "How to turn off Skillet temporarily? - Shift+Click your profession button/link."],
             :latest-release-set
             #{{:download-url
                "https://www.wowinterface.com/downloads/landing.php?fileid=25287",
                :game-track :retail,
                :version "1.83"}
               {:download-url
                "https://www.wowinterface.com/downloads/dlfile5448/Skillet-Classic-1.83-cata.zip",
                :game-track :classic-wotlk,
                :version "Skillet-Classic-1.83-cata"}
               {:download-url
                "https://www.wowinterface.com/downloads/dlfile3678/Skillet-Classic-1.47-beta1-bcc.zip",
                :game-track :classic-tbc,
                :version "Skillet-Classic-1.47-beta1-bcc"}},
             :wowi/updated-date "04-05-24 02:32 PM",
             :source :wowinterface,
             :source-id 25287,
             :wowi/checksum "b5974917c993fa5c998463da73bd52d9",
             :wowi/latest-release-versions [["Version" "1.83"]],
             :wowi/title "Skillet-Classic",
             :filename "web--detail.json",
             :tag-set #{:tradeskill-mods :the-burning-crusade-classic :classic},
             :wowi/latest-release-list
             [{:download-url
               "https://www.wowinterface.com/downloads/landing.php?fileid=25287",
               :game-track :retail}
              {:download-url
               "https://www.wowinterface.com/downloads/dlfile3678/Skillet-Classic-1.47-beta1-bcc.zip",
               :game-track :classic-tbc}
              {:download-url
               "https://www.wowinterface.com/downloads/dlfile5448/Skillet-Classic-1.83-cata.zip",
               :game-track :classic-wotlk}],
             :game-track-set #{:classic-wotlk :retail :classic-tbc},
             :wowi/downloads 18027,
             :wowi/category-set
             #{"Classic - General" "TradeSkill Mods"
               "The Burning Crusade Classic"},
             :wowi/compatibility ["WOTLK Patch (3.4.3)"],
             :wowi/favorites 68
             :wowi/url "https://www.wowinterface.com/downloads/info25287-Skillet-Classic.html"}]}

          fixture (as-downloaded-item url fixture)
          actual (wowi/parse-addon-detail-page fixture)
          ;; just drop all those releases
          actual (update-in actual [:parsed 0] #(dissoc % :wowi/archived-files))]
      (is (= expected actual)))))

(deftest removed-author-request
  (testing "foo"
    (let [url "https://www.wowinterface.com/downloads/info24906-AtlasWorldMapClassic.html"
          fixture (->> "test/fixtures/wowinterface--addon-detail--removed-author-request.html"
                       fs/absolute
                       fs/normalized
                       str)
          fixture (as-downloaded-item url fixture)]
      (is (true? (wowi/dead-page? (wowi/to-html fixture)))))))

(deftest single-download--supports-all
  (testing "foo"
    (let [url "https://www.wowinterface.com/downloads/info11551-MapCoords.html"
          fixture (->> "test/fixtures/wowinterface--addon-detail--single-download--supports-all.html"
                       fs/absolute
                       fs/normalized
                       str
                       (as-downloaded-item url))

          expected
          {:parsed [{:created-date "2008-11-03T19:22:00Z",
                     :filename "web--detail.json",
                     :game-track-set #{:classic
                                       :classic-tbc
                                       :classic-wotlk
                                       :retail},
                     :latest-release-set #{{:download-url "https://www.wowinterface.com/downloads/landing.php?fileid=11551",
                                            :game-track :classic,
                                            :version "1.6"}
                                           {:download-url "https://www.wowinterface.com/downloads/landing.php?fileid=11551",
                                            :game-track :classic-wotlk,
                                            :version "1.6"}
                                           {:download-url "https://www.wowinterface.com/downloads/landing.php?fileid=11551",
                                            :game-track :retail,
                                            :version "1.6"}},
                     :name "mapcoords",
                     :short-description "Mapcoords displays your current coordinates on the minimap.",
                     :source :wowinterface,
                     :source-id 11551,
                     :tag-set #{:classic
                                :coords
                                :map
                                :minimap
                                :the-burning-crusade-classic
                                :ui
                                :wotlk-classic},
                     :updated-date "2024-03-15T17:49:00Z",
                     :wowi/archived-files [],
                     :wowi/category-set #{"Classic - General"
                                          "Map, Coords, Compasses"
                                          "The Burning Crusade Classic"
                                          "WOTLK Classic"},
                     :wowi/checksum "36dada275361859df29fa2dbe2ee2755",
                     :wowi/compatibility ["Classic (1.15.1)"
                                          "Seeds of Renewal (10.2.5)"
                                          "Classic (1.15.0)"
                                          "Guardians of the Dream (10.2.0)"
                                          "WOTLK Patch (3.4.3)"
                                          "Hot Fix (10.1.7)"
                                          "Classic (1.14.4)"
                                          "Fractures in Time (10.1.5)"
                                          "WotLK Patch (3.4.2)"
                                          "Dragonflight patch (10.0.7)"
                                          "Embers of Neltharion (10.1.0)"
                                          "WOTLK Patch (3.4.1)"
                                          "Dragonflight patch (10.0.5)"
                                          "Dragonflight (10.0.2)"],
                     :wowi/compatible-with "Compatible with Retail, Classic & TBC",
                     :wowi/created-date "11-03-08 07:22 PM",
                     :wowi/description ["Mapcoords displays your current coordinates on the minimap."
                                        "Contact:I'm open to questions and suggestions. Feel free to message me here or post in the comments."
                                        "I'm also available for support on the WoWUIDev Discord, @mention me (SDPhantom) or send a DM."],
                     :wowi/downloads 37146,
                     :wowi/favorites 102,
                     :wowi/latest-release-list [{:download-url "https://www.wowinterface.com/downloads/landing.php?fileid=11551",
                                                 :game-track :retail}],
                     :wowi/latest-release-versions [["Version" "1.6"]],
                     :wowi/title "MapCoords",
                     :wowi/updated-date "03-15-24 05:49 PM",
                     :wowi/url "https://www.wowinterface.com/downloads/info11551-MapCoords.html"}]}

          actual (wowi/parse-addon-detail-page fixture)]
      (is (= expected actual)))))


(deftest single-download--tabber
  (testing "foo"
    (let [url "https://www.wowinterface.com/downloads/info8149-IceHUD.html"
          fixture (->> "test/fixtures/wowinterface--addon-detail--single-download--tabber.html"
                       fs/absolute
                       fs/normalized
                       str
                       (as-downloaded-item url))

          expected
          {:parsed [{:created-date "2022-03-28T09:32:00Z",
           :filename "web--detail.json",
           :game-track-set #{:classic-tbc :retail},
           :latest-release-set #{{:download-url "https://www.wowinterface.com/downloads/landing.php?fileid=8149",
                                  :game-track :retail,
                                  :version "1.13.13"}},
           :name "icehud",
           :short-description "Feel free to  if you enjoy using IceHUD and feel generous.",
           :source :wowinterface,
           :source-id 8149,
           :tag-set #{:buffs
                      :classic
                      :combat
                      :debuffs
                      :the-burning-crusade-classic
                      :ui
                      :unit-frames},
           :updated-date "2022-03-29T09:32:00Z",
           :wowi/archived-files [{:date "2022-03-24T11:25:00Z",
                                  :wowi/author "Parnic",
                                  :wowi/download-url "https://www.wowinterface.com/downloads/getfile.php?id=8149=aid=122738",
                                  :wowi/name "IceHUD",
                                  :wowi/size "1MB",
                                  :wowi/version "1.13.13-alpha1"}
                                 {:date "2022-03-22T22:18:00Z",
                                  :wowi/author "Parnic",
                                  :wowi/download-url "https://www.wowinterface.com/downloads/getfile.php?id=8149=aid=122631",
                                  :wowi/name "IceHUD",
                                  :wowi/size "1MB",
                                  :wowi/version "1.13.12"}
                                 {:date "2022-03-22T15:03:00Z",
                                  :wowi/author "Parnic",
                                  :wowi/download-url "https://www.wowinterface.com/downloads/getfile.php?id=8149=aid=122555",
                                  :wowi/name "IceHUD",
                                  :wowi/size "1MB",
                                  :wowi/version "1.13.12-alpha1"}
                                 {:date "2022-01-28T11:23:00Z",
                                  :wowi/author "Parnic",
                                  :wowi/download-url "https://www.wowinterface.com/downloads/getfile.php?id=8149=aid=122518",
                                  :wowi/name "IceHUD",
                                  :wowi/size "1MB",
                                  :wowi/version "1.13.11"}
                                 {:date "2021-11-11T13:02:00Z",
                                  :wowi/author "Parnic",
                                  :wowi/download-url "https://www.wowinterface.com/downloads/getfile.php?id=8149=aid=121572",
                                  :wowi/name "IceHUD",
                                  :wowi/size "1MB",
                                  :wowi/version "1.13.10"}],
           :wowi/category-set #{"Buff, Debuff, Spell"
                                "Casting Bars, Cooldowns"
                                "Classic - General"
                                "Combat Mods"
                                "The Burning Crusade Classic"
                                "Unit Mods"},
           :wowi/checksum "13c91112524b783847d857a3f84832f0",
           :wowi/compatibility ["Visions of N'Zoth (8.3.0)"
                                "BfA content patch (8.2.5)"],
           :wowi/created-date "03-28-22 09:32 AM",
           :wowi/description ["Feel free to  if you enjoy using IceHUD and feel generous."
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
                              "/icehudCL - command-line access to tweak IceHUD settings (for use with macros, etc.)"],
           :wowi/downloads 419918,
           :wowi/favorites 1154,
           :wowi/latest-release-list [{:download-url "https://www.wowinterface.com/downloads/landing.php?fileid=8149",
                                       :game-track :retail}],
           :wowi/latest-release-versions [["Version" "1.13.13"]],
           :wowi/title "IceHUD",
           :wowi/updated-date "03-29-22 09:32 AM",
           :wowi/url "https://www.wowinterface.com/downloads/info8149-IceHUD.html"}]}

          actual (wowi/parse-addon-detail-page fixture)
          
          ]
      (is (= expected actual)))))
