#!/bin/bash
set -ev

echo "${TRAVIS_BRANCH}"
echo "${TRAVIS_PULL_REQUEST}"
echo "${TRAVIS_PULL_REQUEST_BRANCH}"
echo "${TRAVIS_PULL_REQUEST_SHA}"

if [ ${#EXTRA_MAVEN_VAR} -eq 0 ]; then
        # always do non-coverage builds
	mvn -B clean install -DskipITs=false -Pconfidential-tests ${EXTRA_MAVEN_VAR}
else
        # for coverage builds, we need to be more picky, let's exclude builds from branches other than develop and master
        if [ "${TRAVIS_BRANCH}" = "master" ] || [ "${TRAVIS_BRANCH}" = "develop" ]; then
		mvn -B clean install -DskipITs=false -Pconfidential-tests ${EXTRA_MAVEN_VAR}
        fi
fi
