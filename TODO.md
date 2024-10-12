# done

* tukui, add wotlk support
    - done

* merge catalogues
    - even though we only have the one catalogue right now
    - done

* prefer descriptions scraped from html
    - they have the tags stripped out

* tags, make `tags/category-set-to-tag-set` return an ordered list
    - done

* shorten catalogue
    - done

* move github catalogue generation from strongbox
    - having multiple catalogues will help the design adjust itself

# todo 

* new example of all game tracks supports
    - https://www.wowinterface.com/downloads/info12373


* commit changes to catalogue and addon json once a day
    - need to be able to invoke a task like in strongbox
        - lein run --args
    - need to manipulate the ./state directory
        - like git commit

* run on a schedule like strongbox-catalogue
    - update.sh -> starts catalogue builder -> starts a daily scrape -> commits results -> pushes

# todo bucket (no order)

* wowi, descriptions, filter common leading words in descriptions
    - "about", "description", "general", "general description", "what", "info", "information", "credits", "features", "intro", "introduction", "note", "overview", "preamble" (really), "purpose", "synopsis"
    - "donate", "donation", "paypal", "support", "patreon"
    - $name-of-addon, $version-string, "version"
    - "discontinued" seems popular ...
    - "english", "enGB", "enUS"
    - "hello", "hey", "hi"
    - "important", "news", "update", "updated", "urgent", "warning"
    - "summary", "special thanks", "special note"
    - "what is it", "what does it do", "what is ...", "what it is", "what's this"

    - cat full-catalogue.json | jq '."addon-summary-list"[].description' | sort | less

* bug, handle addons with no game-track-list
    - investigate but just default to retail
    - all addons in catalogue have a game track
        - this could have been a bug before non-api addons were excluded
            - in this case, if we try to add non-api addons *back* in to the catalogue, we'll have to deal with them then.
* a per-addon 'state' file that accumulates changes
    - like latest-release-list that has game track data not present elsewhere
    - like downloads
    - how to associate these changes with a date?
* quantifiy discrepancy between API filelist and wowi website
* write snippets to a db
    - I also want to browse the db like a file system so I can diff changes
        - https://github.com/twopoint718/SQLiteFS
        - https://github.com/narumatt/sqlitefs

* 'game-track' in latest-release-set is wrong
    - it uses 'retail' from the html when it's obviously not retail
        - it is difficult to guess at this without leaving some addons with no game tracks at all
        - I'd rather them more leniant than strict
* I want to see throughput rate on these workers
    - for example, parsed-content worker is processing 100 items/second
* migrate all of strongbox catalogue scraping here
    - delete there. means a new major version
* test coverage
* http, add retry support
* github support
* CI

## wowi

* wowi, key spec
* wowi, description, parse these away (bbcode, instaparse):
    - [SIZE=\"5\"][B][I][COLOR=\"Red\"]
* wowi, description, strip images and links and empty new lines *then* take the first row

---

* smarter crawling.
    - addon scheduling, release peeking
* more considerate crawling.
    - rate limiting, connection pooling, obeys server caching rules, obeys `robots.txt`
* preserve historical changes to addon data.
    - mostly for download counts
        - can always walk through git history for this one
            - thats a bit meh. perhaps the filesystem and git isn't the best place for this?

# won't do

* wowi, parse latest feed
    - https://www.wowinterface.com/rss/latest.xml
        - not sure I can trust the feed to be complete.

