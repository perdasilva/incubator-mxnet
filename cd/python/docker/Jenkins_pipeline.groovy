// -*- mode: groovy -*-

// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//
// Jenkins pipeline
// See documents at https://jenkins.io/doc/book/pipeline/jenkinsfile/

// NOTE: 
// ci_utils and cd_utils are loaded by the originating Jenkins job, e.g. jenkins/Jenkinsfile_release_job

IMAGE_NAME = "python"
IMAGE_ROOT_DIR = "cd/python/docker"

def get_pipeline(mxnet_variant) {
  def node_type = mxnet_variant.startsWith('cu') ? NODE_LINUX_GPU : NODE_LINUX_CPU
  return cd_utils.generic_pipeline(mxnet_variant, this, node_type)
}

// Returns the (Docker) environment for the given variant
// The environment corresponds to the docker files in the 'docker' directory
def get_environment(mxnet_variant) {
  if (mxnet_variant.startsWith("cu")) {
    // Remove 'mkl' suffix from variant to properly format test environment
    return "ubuntu_gpu_${mxnet_variant.replace('mkl', '')}"
  }
  return "ubuntu_cpu"
}

def get_mxnet_docker_image_args(mxnet_variant) {
    return [
        "--image-name", "python",
        "--mxnet-variant", "${mxnet_variant}",
    ]
}

def build(mxnet_variant) {
  ws("workspace/python_docker/${mxnet_variant}/${env.BUILD_NUMBER}") {
    ci_utils.init_git()
    cd_utils.restore_static_libmxnet(mxnet_variant)

    // package universal wheel file
    def nvidia_docker = mxnet_variant.startsWith('cu')
    def environment = get_environment(mxnet_variant)
    ci_utils.docker_run(environment, "cd_package_pypi ${mxnet_variant}", nvidia_docker)

    // build python docker images
    docker_build_context_dir = "./wheel_build"
    py3_args = ["--build-arg", "PYTHON_CMD=python3", "--tag-postfix", "py3"]
    py2_args = ["--build-arg", "PYTHON_CMD=python", "--tag-postfix", "py2"]
    cd_utils.build_mxnet_image(IMAGE_NAME, mxnet_variant, IMAGE_ROOT_DIR, docker_build_context_dir, py3_args)
    cd_utils.build_mxnet_image(IMAGE_NAME, mxnet_variant, IMAGE_ROOT_DIR, docker_build_context_dir, py2_args)
  }
}

def test(mxnet_variant) {
  ws("workspace/python_docker/${mxnet_variant}/${env.BUILD_NUMBER}") {
    // test python docker images
    py3_args = ["--tag-postfix", "py3"]
    py2_args = ["--tag-postfix", "py2"]
    py3_test_image_cmd = "${IMAGE_ROOT_DIR}/test_image.sh ${mxnet_variant} python3"
    py2_test_image_cmd = "${IMAGE_ROOT_DIR}/test_image.sh ${mxnet_variant} python"
    cd_utils.test_mxnet_image(IMAGE_NAME, mxnet_variant, IMAGE_ROOT_DIR, py3_test_image_cmd, py3_args)
    cd_utils.test_mxnet_image(IMAGE_NAME, mxnet_variant, IMAGE_ROOT_DIR, py2_test_image_cmd, py2_args)
  }
}

def push(mxnet_variant) {
  ws("workspace/python_docker/${mxnet_variant}/${env.BUILD_NUMBER}") {
    // push python docker images
    py3_args = ["--tag-postfix", "py3"]
    py2_args = ["--tag-postfix", "py2"]
    cd_utils.push_mxnet_image(IMAGE_NAME, mxnet_variant, py3_args)
    cd_utils.push_mxnet_image(IMAGE_NAME, mxnet_variant, py2_args)
  }
}

return this
