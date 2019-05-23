# MXNet Continuous Delivery

## Introduction

MXNet aims to support a variety of frontends, e.g. Python, Java, Perl, R, etc. as well as environments (Windows, Linux, Mac, with or without GPU, with or without MKL support, etc.). This package contains a small continuous delivery (CD) framework used to automate the delivery nightly and release builds across our delivery channels.

<!-- TODO: Add links to the actual jobs, once this is live on PROD -->

The CD process is driven by the [CD pipeline job](Jenkinsfile_cd_pipeline), which orchestrates the order in which the artifacts are delivered. For instance, first publish the libmxnet library before publishing the pip package. It does this by triggering the [release job](Jenkinsfile_release_job) with a specific set of parameters for each delivery channel. The release job executes the specific release pipeline for a delivery channel across all MXNet *variants*.

A variant is a specific environment or features for which MXNet is compiled. For instance CPU, GPU with CUDA v10.0, CUDA v9.0 with MKL support, etc. 

Currently, 10 variants are supported:

* *cpu*: CPU
* *mkl*: CPU w/ MKL
* *cu80*: CUDA 8.0
* *cu80mkl*: CUDA 8.0 w/ MKL-DNN
* *cu90*: CUDA 9.0
* *cu90mkl*: CUDA 9.0 w/ MKL-DNN
* *cu92*: CUDA 9.2
* *cu92mkl*: CUDA 9.2 w/ MKL-DNN
* *cu100*: CUDA 10
* *cu100mkl*: CUDA 10 w/ MKL-DNN

*For more on variants, see [here](https://github.com/apache/incubator-mxnet/issues/8671)*

## Framework Components

### CD Pipeline Job

The [CD pipeline job](Jenkinsfile_cd_pipeline) take three parameters:

 * **MXNET_SHA**: Commit ID from which to run the CD process (Optional). Defaults to the HEAD commit ID.
 * **RELEASE_BUILD**: Flags the run as a *release build*. The underlying jobs can then use this environment variable to disambiguate between nightly and release builds. Defaults to *false*.
 * **MXNET_VARIANTS**: A comma separated list of variants to build. Defaults to *all* variants.

This job defines and executes the CD pipeline. For example, first publish the MXNet library, then, in parallel, execute the python and maven releases. Every step of the pipeline executes a trigger for a release job](Jenkinsfile_release_job).

### Release Job

The [release job](Jenkinsfile_release_job) takes five parameters:

 * **MXNET_SHA**: Commit ID from which to run the CD process (Optional). Defaults to the HEAD commit ID.
 * **RELEASE_BUILD**: Flags the run as a *release build*. The underlying jobs can then use this environment variable to disambiguate between nightly and release builds. Defaults to *false*.
 * **MXNET_VARIANTS**: A comma separated list of variants to build. Defaults to *all* variants.
 * **RELEASE\_JOB\_NAME**: A name for this release job (Optional). Defaults to "Generic release job". It is used for debug output purposes.
 * **RELEASE\_JOB\_TYPE**: Defines the release pipeline you want to execute.

The release job executes, in parallel, the release pipeline for each of the variants (**MXNET_VARIANTS**) for the job type (**RELEASE\_JOB\_TYPE**). The job type the path to a directory (relative to the `cd` directory) that includes a `Jenkins_pipeline.groovy` file ([e.g.](mxnet_lib/static/Jenkins_pipeline.groovy)).

### Release Pipelines: Jenkins_pipeline.groovy

This file defines the release pipeline for a particular release channel. It defines a function `get_pipeline(mxnet_variant)`, which returns a closure with the pipeline to be executed. For instance:

```
def get_pipeline(mxnet_variant) {
  return {
    stage("${mxnet_variant}") {
      stage("Build") {
        timeout(time: max_time, unit: 'MINUTES') {
          build(mxnet_variant)
        }
      }
      stage("Test") {
        timeout(time: max_time, unit: 'MINUTES') {
          test(mxnet_variant)
        }
      }
      stage("Publish") {
        timeout(time: max_time, unit: 'MINUTES') {
          publish(mxnet_variant)
        }
      }
    }
  }
}

def build(mxnet_variant) {
  node(UBUNTU_CPU) {
    ...
  }
}
...
```

## Binary Releases

The "first mile" of the CD process is posting the mxnet binaries to the [artifact repository](utils/artifact_repository.md). Once this step is complete, the pipelines for the different release channels (PyPI, Maven, etc.) can begin from the compiled binary, and focus solely on packaging it, testing the package, and posting it to the particular distribution channel.

<!-- TODO: Once all the artifact repository Jenkins utility functions are in, list them here -->

## Adding New Release Pipelines

1. Create a directory under `cd` which represents your release channel, e.g. `python/pypi`.
2. Add a `Jenkins_pipeline.groovy` there with a `get_pipeline(mxnet_variant)` function that describes your pipeline.
3. Add a call to your pipeline to the [CD pipeline job](Jenkinsfile_cd_pipeline).

#### General Guidelines:

##### Timeout

We shouldn't set global timeouts for the pipelines. Rather, the `step` being executed should be rapped with a `timeout` function (as in the pipeline example above). The `max_time` is a global variable set at the [release job](Jenkinsfile_release_job) level. 

##### Node of execution

Ensure that either your steps, or the whole pipeline are wrapped in a `node` call. The jobs execute in an `utility` node. If you don't wrap your pipeline, or its individual steps, in a `node` call, this will lead to problems.

Examples of the two approaches:

<!-- TODO: Add links to examples once the all pipelines are in -->

**Whole pipeline**

The release pipeline is executed on a single node, depending on the variant building released.
This approach is fine, as long as the stages that don't need specialized hardware (e.g. compilation, packaging, publishing), are short lived.

```
def get_pipeline(mxnet_variant) {
  def node_type = mxnet_variant.startsWith('cu') ? NODE_LINUX_GPU : NODE_LINUX_CPU

  return {
    node (node_type) {
      stage("${mxnet_variant}") {
        stage("Build") {
          ...
        }
        stage("Test") {
          ...
        }
        ...
      }
    }
  }
}
```

**Per step**

Use this approach in cases where you have long running stages that don't depend on specialized/expensive hardware.

```
def get_pipeline(mxnet_variant) {
  return {
    stage("${mxnet_variant}") {
      stage("Build") {
        ...
      }
      ...
    }
  }
}

def build(mxnet_variant) {
  node(UBUNTU_CPU) {
    ...
  }
}

def test(mxnet_variant) {
  def node_type = mxnet_variant.startsWith('cu') ? NODE_LINUX_GPU : NODE_LINUX_CPU
  node(node_type) {
    ...
  }
}
```

##### MXNET_SHA

Be mindful of this parameter as it defines the state of the repository that you are building. When checking out the project to execute your step, use `ci_utils.init_git(env.MXNET_SHA)`. You should ensure that what is being built is consistent across the variants.

