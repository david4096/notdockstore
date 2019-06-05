#!/usr/bin/env bash
# This script decrypts our test database 
# WARNING: Edit decrypt.template.mustache not decrypt.sh which is a generated file
# Bash3 Boilerplate. Copyright (c) 2014, kvz.io

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

if [[ "${TESTING_PROFILE}" == *"integration-tests"* ]] || [[ "${TESTING_PROFILE}" == "automated-review" ]]; then
    openssl aes-256-cbc -K $encrypted_5374cd0784ea_key -iv $encrypted_5374cd0784ea_iv -in secrets.tar.enc -out secrets.tar -d
    tar xvf secrets.tar
fi
