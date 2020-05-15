#!/usr/bin/env bash

# Exit script if a statement returns a non-true return value.
set -o errexit

# Use the error status of the first failure, rather than that of the last item in a pipeline.
set -o pipefail

MYDIR="$(dirname "$0")"
cd "$MYDIR"

# shellcheck source=registry-config.sh disable=SC1091
source ../registry-config.sh

# shellcheck source=opennms-container/version-n-tags.sh disable=SC1091
source ../version-tags.sh

RPMDIR="$(cd ../../target/rpm/RPMS/noarch; pwd -P)"
IPADDR="$(../yum-server/get_ip.sh)"

cat <<END >rpms/opennms-docker.repo
[opennms-repo-docker-common]
name=Local RPMs to Install from Docker
baseurl=http://${IPADDR}:19990/
enabled=1
gpgcheck=0
END

../yum-server/launch_yum_server.sh "$RPMDIR"

docker build -t horizon \
  --network bridge \
  --build-arg BUILD_DATE="$(date -u +\"%Y-%m-%dT%H:%M:%S%z\")" \
  --build-arg VERSION="${VERSION}" \
  --build-arg SOURCE="${CIRCLE_REPOSITORY_URL}" \
  --build-arg REVISION="$(git describe --always)" \
  --build-arg BUILD_JOB_ID="${CIRCLE_WORKFLOW_JOB_ID}" \
  --build-arg BUILD_NUMBER="${CIRCLE_BUILD_NUM}" \
  --build-arg BUILD_URL="${CIRCLE_BUILD_URL}" \
  --build-arg BUILD_BRANCH="${CIRCLE_BRANCH}" \
  .

if [ -n "${CIRCLE_BUILD_NUM}" ]; then
  IMAGE_VERSION+=("${BASE_IMAGE_VERSION}-b${CIRCLE_BUILD_NUM}")
fi

docker image save horizon -o images/container.oci

rm -f rpms/*.repo

../yum-server/stop_yum_server.sh
