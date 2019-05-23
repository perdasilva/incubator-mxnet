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

def get_pipeline(mxnet_variant) {
  def node_type = mxnet_variant.startsWith('cu') ? NODE_LINUX_GPU : NODE_LINUX_CPU
  return cd_utils.generic_pipeline(mxnet_variant, this, node_type)
}

def build(mxnet_variant) {
  ws("workspace/runtime_docker/${mxnet_variant}/${env.BUILD_NUMBER}") {
    ci_utils.init_git()
    cd_utils.restore_artifact(mxnet_variant, 'dynamic')
    sh "./cd/runtime_images/runtime_images.sh build ${mxnet_variant}"
  }
}

def test(mxnet_variant) {
  ws("workspace/runtime_docker/${mxnet_variant}/${env.BUILD_NUMBER}") {
    sh "./cd/runtime_images/runtime_images.sh test ${mxnet_variant}"
  }
}

def push(mxnet_variant) {
  ws("workspace/runtime_docker/${mxnet_variant}/${env.BUILD_NUMBER}") {
    retry(5) {
      sh "./cd/runtime_images/runtime_images.sh publish ${mxnet_variant}"
    }
  }
}

return this
