#!/bin/bash
set -e

# always ratchet *upwards*
fail_threshold=55

function finish {
    # 'lein clean' wipes out the 'target' directory, including the Cloverage report.
    # remove any coverage reports from previous run
    rm -rf ./coverage/
    if [ -d target/coverage ]; then
        echo
        echo "wrote coverage/index.html"
        mv target/coverage coverage
    fi

    lein clean
}
trap finish EXIT

lein cloverage --fail-threshold "$fail_threshold" --html
