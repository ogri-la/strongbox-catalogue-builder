#!/bin/bash
# runs a daily update
# commits changes to state and pushes
# must be called with a commit message as the first argument

set -eux

commit_msg=$1

lein run --action scrape-catalogue

(
    cd state
    git add .
    git commit -am "$commit_msg"
    git push
)

cp state/*.json ../strongbox-catalogue/
