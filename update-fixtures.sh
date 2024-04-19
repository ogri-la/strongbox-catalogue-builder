#!/bin/bash
set -e

curl -s https://www.wowinterface.com/downloads/info25287-Skillet-Classic.html > test/fixtures/wowinterface--addon-detail--multiple-downloads--no-tabber.html

curl -s https://wowinterface.com/downloads/info16711-BrokerPlayedTime.html > test/fixtures/wowinterface--addon-detail--multiple-downloads--tabber.html

curl -s https://www.wowinterface.com/downloads/info24906-AtlasWorldMapClassic.html > test/fixtures/wowinterface--addon-detail--removed-author-request.html
