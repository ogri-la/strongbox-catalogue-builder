# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 0.0.3

### Added

* github catalogue support
* tukui catalogue support
* catalogue filtering and shortening
* cli and an update script
* classic-wotlk support

### Changed

* tag-list and game-track set ordering pushed from source (wowi/tukui/github) into catalogue output formatting
* addons are now ordered by name and then by source.
    - for example, two addons named 'foo' will have the 'github' source appear before the 'wowinterface' source.

### Fixed

### Removed

## 0.0.2

### Added

### Changed

### Fixed

### Removed

## 0.0.1

### Added

* core, a system for queuing urls to download and parsing content.
* wowi, api and html scraping.
* catalogue, addon data coercion into a format suitable for a strongbox catalogue item.
* catalogue, formatting and writing catalogues to disk.
* 'user', a ns for interacting with the app.
* tags, copied from strongbox with a few additions.
* http, another bloody http library with caching copied from strongbox but simpler downloading.

