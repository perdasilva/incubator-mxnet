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

// Sets the name of the release job in Jenkins.
// That is the release job that has been created in Jenkins based
// on the Jenkinsfile_release_job definition.
def set_release_job_name(release_job) {
  RELEASE_JOB = release_job
}

// Triggers a downstream jenkins job responsible for building, testing
// and publishing all the variants for a particular 'job_type'.
// The 'job_type' should be the name of the directory that contains the 
// 'Jenkins_pipeline.groovy' file and has the pipeline definition for the 
// artifact (docker image, binary, pypi or maven package, etc.) that should
// be published.
def trigger_release_job(job_name, job_type, mxnet_variants) {
  def run = build(
    job: RELEASE_JOB, 
    parameters: [
      string(name: 'MXNET_SHA', value: "${env.MXNET_SHA}"),
      string(name: 'RELEASE_JOB_NAME', value: "${job_name}"),
      string(name: 'RELEASE_JOB_TYPE', value: "${job_type}"),
      string(name: 'RELEASE_JOB_TYPE', value: "${job_type}"),
      string(name: 'MXNET_VARIANTS', value: "${mxnet_variants}"),
      booleanParam(name: 'RELEASE_BUILD', value: "${env.RELEASE_BUILD}")
    ],
    // If propagate is true, any result other than successful will
    // mark this call as failure (inc. unstable).
    // https://jenkins.io/doc/pipeline/steps/pipeline-build-step
    propagate: false)

  def result = run.getResult()

  // In case the underlying release job is unstable,
  // e.g. one or more (but not all) the variants failed, or;
  // it is aborted (e.g. one of steps timed out),
  // continue with the pipeline and try to post as many releases as possible
  // but mark it as unstable
  if (result == "UNSTABLE" || result == "ABORTED") {
    currentBuild.result = "UNSTABLE" 
  }

  // Throw an exception on failure, because this would mean the whole
  // pipeline failed (i.e. for every variant)
  if (result == "FAILURE") {
    error "Downstream job: ${job_name} failed"
  }
}

// Wraps variant pipeline with error catching and
// job status setting code
// If there's an error in one of the pipelines, set status to UNSTABLE
// If all pipelines fail, set to FAILURE
// This is to be used in conjunction with the error_checked_parallel
def wrap_variant_pipeline_fn(variant_pipeline, total_num_pipelines) {
  // do not add def - seems to affect the scope
  count = 0
  return {
    try {
      variant_pipeline()
    } catch (ex) {
      count++
      currentBuild.result = "UNSTABLE"

      if (count == total_num_pipelines) {
        currentBuild.result = "FAILURE"
        throw ex
      }
    }
  }
}

// Takes a map of key -> closure values to be executed in parallel.
// The outcome of the execution of each parallel step will affect
// the result (SUCCESS, FAILURE, ABORTED, UNSTABLE) of the overall job.
// If all steps fail or are aborted, the job will be set to failed.
// If some steps fail or are aborted, the job will be set to unstable. 
def error_checked_parallel(variant_pipelines) {
  pipelines = variant_pipelines.inject([:]) { mp, key, value ->
    mp << ["${key}": wrap_variant_pipeline_fn(value, variant_pipelines.size())]
  }
  parallel pipelines
}

// pushes artifact to repository
def push_artifact(libmxnet_path, variant, is_dynamic = true, license_paths = '', dependency_paths = '') {
  if(license_paths == null) license_paths = ''
  if(dependency_paths == null) dependency_paths = ''
  def libtype = is_dynamic ? 'dynamic' : 'static'
  sh "./ci/cd/utils/artifact_repository.py --push --verbose --${libtype} --variant ${variant} --libmxnet ${libmxnet_path} --licenses ${license_paths} --dependencies ${dependency_paths}"
}

return this
