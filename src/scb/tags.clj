(ns scb.tags
  (:require
   [clojure.string :refer [trim lower-case]]
   [clojure.spec.alpha :as s]
   [orchestra.core :refer [defn-spec]]
   ;;[taoensso.timbre :refer [debug info warn error spy]]
   [scb.specs :as sp]))

sp/placeholder

;; rules of thumb:
;; * prefer singluar tags rather than plural.
;; * prefer broadly common over very speific
;; * ...

(def wowi-replacements
  "wowinterface-specific categories that are replaced"
  {"Character Advancement" [:quests :leveling :achievements]
   "Other" [:misc]
   "Suites" [:compilation]
   "Graphic UI Mods" [:ui :ui-replacement]
   "UI Media" [:ui] ;; audio video ?
   "ROFL" [:misc :mini-game]
   "Combat Mods" [:combat]
   "Buff, Debuff, Spell" [:buffs :debuffs]
   "Casting Bars, Cooldowns" [:buffs :debuffs :ui]
   "Map, Coords, Compasses" [:map :minimap :coords :ui]
   "RolePlay, Music Mods" [:role-play :audio]
   "Chat Mods" [:chat]
   "Unit Mods" [:unit-frames]
   "Raid Mods" [:unit-frames :raid-frames]
   "Data Mods" [:data]
   "Developer Utilities" [:dev]
   "Utility Mods" [:utility] ;; misc?
   "WoW Tools & Utilities" [:dev]
   "Action Bar Mods" [:action-bar :ui]
   "Tradeskill Mods" [:tradeskill]
   "Classic - General" [:classic]

   "Guild Compilations" [:guild :compilation]
   "Minimalistic Compilations" [:compilation]
   "Generic Compilations" [:compilation]
   "Tank Compilations" [:tank :compilation]
   "Healer Compilations" [:healers :compilation]
   "Graphical Compilations" [:compilation]
   "Class Compilations" [:class :compilation]
   "DPS Compilations" [:dps :compilation]

   "Plug-Ins & Patches" [:plugins]
   "Beta-version AddOns" [:beta]
   "Discontinued and Outdated Mods" [:discontinued]})

(def wowi-supplements
  "wowinterface-specific categories that gain new tags"
  {"Pets" [:battle-pets :companions]
   "Data Broker" [:data]
   "Titan Panel" [:plugins]
   "FuBar" [:plugins]
   "Mail" [:ui]})

(def curse-replacements
  "curseforge-specific categories that are replaced"
  {"HUDs" [:ui :unit-frames]
   "Minigames" [:mini-games]
   "Auction & Economy" [:auction-house]
   "Chat & Communication" [:chat]
   "Development Tools" [:dev]
   "Libraries" [:libs]
   "Damage Dealer" [:dps]
   "Boss Encounters" [:boss]
   "Twitch Integration" []})

(def curse-supplements
  "curseforge-specific categories that gain new tags"
  {"Quests & Leveling" [:achievements]
   "Arena" [:pvp]
   "Battleground" [:pvp]
   "Battle Pets" [:pets :companions :mounts]
   "Map & Minimap" [:coords :ui]
   "Raid Frames" [:unit-frames]
   "Data Export" [:data]
   "Data Broker" [:data]
   "Titan Panel" [:plugins]})

(def tukui-replacements
  "tukui-specific categories that are replaced"
  {"Edited UIs & Compilations" [:ui :compilation]
   "Full UI Replacements" [:ui]
   "Skins" [:ui]
   "Tooltips" [:tooltip] ;; singular
   "Plugins: Other" [:plugins :misc]})

(def tukui-supplements
  "tukui-specific categories that gain new tags"
  {"Map & Minimap" [:coords :ui]})

(def general-replacements
  "categories shared by all addon hosts already that are replaced"
  {"Miscellaneous" [:misc]})

(def general-supplements
  "categories shared by all addon hosts that gain new tags"
  {"Druid" [:class]
   "Warlock" [:class]
   "Warrior" [:class]
   "Rogue" [:class]
   "Healers" [:role]
   "Death Knight" [:class]
   "Paladin" [:class]
   "Mage" [:class :caster]
   "Priest" [:class :caster]
   "Tank" [:class]
   "Monk" [:class]
   "Shaman" [:class :caster]
   "Demon Hunter" [:class]
   "Hunter" [:class]

   "Alchemy" [:profession]
   "Cooking" [:profession]
   "Mining" [:profession]
   "Engineering" [:profession]
   "Jewelcrafting" [:profession]
   "Tailoring" [:profession]
   "First Aid" [:profession]
   "Fishing" [:profession]
   "Leatherworking" [:profession]
   "Enchanting" [:profession]
   "Blacksmithing" [:profession]
   "Inscription" [:profession]
   "Skinning" [:profession]
   "Archaeology" [:profession]
   "Herbalism" [:profession]})

(def replacement-map
  {:wowinterface (merge general-replacements wowi-replacements)
   :curseforge (merge general-replacements curse-replacements)
   :tukui (merge general-replacements tukui-replacements)})

(def supplement-map
  {:wowinterface (merge general-supplements wowi-supplements)
   :curseforge (merge general-supplements curse-supplements)
   :tukui (merge general-supplements tukui-supplements)})

;;

(defn-spec category-to-tag (s/or :ok :addon/tag, :bad nil?)
  [category :addon/category]
  (when-not (empty? category)
    (-> category
        lower-case
        trim
        (clojure.string/replace #" +" "-") ;; hyphenate white space
        keyword)))

(defn-spec category-to-tag-list (s/or :singluar :addon/tag, :composite :addon/tag-list)
  "given a `category` string, converts it into one or many tags."
  [addon-host :addon/source, category :addon/category]
  (let [replacements (get replacement-map addon-host, general-replacements)
        supplements (get supplement-map addon-host, general-supplements)

        replacement-tags (get replacements category [])
        supplementary-tags (get supplements category [])

        tag-list (into replacement-tags supplementary-tags)]
    (if-not (empty? replacement-tags)
      ;; we found a set of replacement tags so we're done
      tag-list

      ;; couldn't find a replacement set of tags so parse the category string
      (let [bits (clojure.string/split category #"( & |, |: )+")]
        (->> bits (map category-to-tag) (into tag-list) (remove nil?) vec)))))

(defn-spec category-set-to-tag-set :addon/tag-set
  "given a list of category strings, converts them into a distinct list of tags by calling `category-to-tag-list`."
  [addon-host :addon/source, category-set :addon/category-set]
  ;; sorting cuts down on noise in diffs.
  ;; `set` because curseforge has duplicate categories and supplemental tags may introduce duplicates
  (->> category-set (map (partial category-to-tag-list addon-host)) flatten set))
