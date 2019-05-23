#!/usr/bin/env bash

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# Executes runtime images pipeline functions: build, test, publish
# Assumes script is run from the root of the mxnet repository
# Assumes script is being run within MXNet CD infrastructure

set -xe

usage="Usage: runtime_images.sh <build|test|publish> MXNET-VARIANT"

command=${1:?$usage}
mxnet_variant=${2:?$usage}

ci_utils='cd/utils'

docker_tags=($(./${ci_utils}/docker_tag.sh ${mxnet_variant}))
main_tag=${docker_tags[0]}
base_image=$(./${ci_utils}/mxnet_base_image.sh ${mxnet_variant})
repository="runtime"
image_name="${repository}:${main_tag}"

if [ ! -z "${RELEASE_DOCKERHUB_REPOSITORY}" ]; then
    image_name="${RELEASE_DOCKERHUB_REPOSITORY}/${image_name}"
fi

resource_path='cd/runtime_images'

# Builds runtime image
build() {
    docker build -t "${image_name}" --build-arg BASE_IMAGE="${base_image}" --build-arg MXNET_COMMIT_ID=${GIT_COMMIT} -f ${resource_path}/Dockerfile .
}

# Tests the runtime image by executing runtime_images/test_image.sh within the image
# Assumes image exists locally
test() {
    runtime_param=""
    if [[ ${mxnet_variant} == cu* ]]; then
        runtime_param="--runtime=nvidia"
    fi
    local test_image_name="${image_name}_test"
    docker build -t "${test_image_name}" --build-arg USER_ID=`id -u` --build-arg GROUP_ID=`id -g` --build-arg BASE_IMAGE="${image_name}" -f ${resource_path}/Dockerfile.test .
    docker run ${runtime_param} -u `id -u`:`id -g` -v `pwd`:/mxnet "${test_image_name}" ${resource_path}/test_runtime_image.sh "${mxnet_variant}"
}

# Pushes the runtime image to the repository
# Assumes image exists locally
publish() {
    if [ -z "${RELEASE_DOCKERHUB_REPOSITORY}" ]; then
        echo "Cannot publish image without RELEASE_DOCKERHUB_REPOSITORY environment variable being set."
        exit 1
    fi
    ./${ci_utils}/docker_login.py && docker push "${image_name}"

    # Iterate over remaining tags, if any
    for ((i=1;i<${#docker_tags[@]};i++)); do
        local docker_tag="${docker_tags[${i}]}"
        local latest_image_name="${RELEASE_DOCKERHUB_REPOSITORY}/${repository}:${docker_tag}"
        docker tag "${image_name}" "${latest_image_name}"
        docker push "${latest_image_name}"
    done 
}

case ${command} in
    "build")
        build
        ;;

    "test")
        test
        ;;

    "publish")
        publish
        ;;

    *)
        echo $usage
        exit 1
esac
