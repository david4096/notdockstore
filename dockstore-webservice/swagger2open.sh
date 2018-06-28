#!/usr/bin/env bash

# Using https://mermade.org.uk/openapi-converter, generate openapi schema sources.
HTTP_STATUS=`curl -F "filename=@src/main/resources/swagger.yaml" https://mermade.org.uk/api/v1/convert -o src/main/resources/open_api.yaml -w "%{http_code}"`
if [ "$?" -eq 0 ] && [ "$HTTP_STATUS" -eq 200  ]; then
  # Parse out position variable not compatible with open-api schemas
  sed -n '/position/d;s/<html><body><pre>//;w src/main/resources/openapi1.yaml' src/main/resources/open_api.yaml
  # Aggregate correct url for dockstore api. Using pipe instead of forward slash, avoid dealing with scape chars
  sed -n 's|url: /|url: https://dockstore.org:8443|; w src/main/resources/openapi2.yaml' src/main/resources/openapi1.yaml

  # Additional tag for smart-api compliance.
  # Note: Need "\ \" at beginning of subsequent line in order to work on both Linux and Mac
  sed '/- name: GA4GHV1/i \
\ \ - name: NIHdatacommons
  ' src/main/resources/openapi2.yaml > src/main/resources/openapi.yaml

  # Clean directory from excess files.
  rm src/main/resources/open_api.yaml src/main/resources/openapi1.yaml src/main/resources/openapi2.yaml
else
  echo "Error converting swagger to openapi, skipping openapi generation"
fi
exit 0
