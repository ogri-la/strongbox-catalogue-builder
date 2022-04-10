# done

* wowi, fetch structured data
    - fileList as well as each individual details
    - done
* order keys for diffing
    - done
* wowi, description and changelog
    - done
* tag/category normalisation
    - done
* wowi, namespace keys
    - wowi/archived-files, wowi/date-updated, etc
    - preserve the original values
    - derived/formatted values go into a local ns 
    - done
* write catalogue
    - done


# todo 0.1 release

* wowi, parse latest feed
    - https://www.wowinterface.com/rss/latest.xml
* wowi, scrape html description
    - we have cases where there is no api detail to pull description from
* wowi, add created date
* deal with tag "discontinued-and-outdated-mods"
    - something common across all hosts
* wowi, description, strip any lines with no alpha-numeric in them
* wowi, tags, turn this 
        ["class-compilations", "classic", "dps-compilations",
        "generic-compilations", "graphical-compilations",
        "minimalistic-compilations"],
   into 
        [compilation]

* add obvious game tracks without guessing
    - for example, if it hasn't been updated since classic was introduced, it was retail
    - if it has the tag 'the-burning-crusade-classic', then give it :classic-tbc
    - ...

* wowi, sort addon data keys
* wowi, archived-files not namespaced
* wowi, deal with 
    'latest-release-list' and 
    'archived-files' list and 
    'wowi/latest-release' list
    'wowi/latest-release-versions' list
    jeez

# todo bucket (no order)

* I want to see throughput rate on these workers
    - for example, parsed-content worker is processing 100 items/second
* scheduler
    - scrape a thing N times a day
    - commit changes to json once a day
* migrate all of strongbox catalogue scraping here
* test coverage
* http, add retry support
* tukui support
* github support

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
