# done

* scheduler and cache busting
    - scrape a thing N times a day
        - the 'latest' feed. rather than parsing the content, just wipe the cached data
        - done
    - wowi
        - we have access to the last updated date in the listing pages
            - wipe out the listing pages daily
                - done
            - inspect the updated date and compare to age (and type?) of cached file
                - done
                    - addons updated in the last N days have their cache wiped and the data is refreshed again

* write snippets to distinct files
    - done

# todo 0.2 release

* order snippet merging
    - defer some derived values until then
    - I reckon github will get the same treatment
        - repo data, release data, html data, etc

# todo 0.3 release

* write catalogues to a temporary repo
    - just until workflow is stable
    - will eventually be strongbox-catalogue I suspect
    
* write result of merging wowi data to the temporary repo
    - this is what strongbox will check for the list of releases and archived files

* shorten catalogue

* merge catalogues
    - even though we only have the one catalogue right now

* commit changes to catalogue and addon json once a day
    - need to be able to invoke a task like in strongbox

# todo bucket (no order)

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
* tukui support
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

