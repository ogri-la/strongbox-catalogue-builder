# done

# todo 0.2 release

* scheduler and cache busting
    - scrape a thing N times a day
        - the 'latest' feed. rather than parsing the content, just wipe the cached data
    - commit changes to json once a day
    - wowi
        - we have access to the last updated date in the listing pages
            - wipe out the listing pages daily
            - inspect the updated date and compare to age (and type?) of cached file

# todo bucket (no order)

* write snippets to distinct files and ordered merging
    - defer any derived value scraping until then
* write snippets to a db
    - I also want to browse the db like a file system so I can diff changes
        - https://github.com/twopoint718/SQLiteFS
        - https://github.com/narumatt/sqlitefs
* wowi, parse latest feed
    - https://www.wowinterface.com/rss/latest.xml
* 'game-track' in latest-release-set is wrong
    - it uses 'retail' from the html when it's obviously not retail
        - it is difficult to guess at this without leaving some addons with no game tracks at all
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
