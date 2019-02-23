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
// This file contains the steps that will be used in the 
// Jenkins pipelines

utils = load('ci/Jenkinsfile_utils.groovy')

// Python wheels
mx_pip = 'wheel_build/dist/*.whl'

mx_static_lib = 'lib/libmxnet.so, lib/libgfortran.so.3, lib/libquadmath.so.0'
mx_mkldnn_static_lib = 'lib/libmxnet.so, lib/libgfortran.so.3, lib/libquadmath.so.0, lib/libiomp5.so, lib/libmkldnn.so.0, lib/libmklml_intel.so, MKLML_LICENSE'

def docker_run(platform, function_name, use_nvidia, shared_mem = '500m', env_vars = "") {
  def runtime_functions_path = 'ci/publish/python/runtime_functions.sh'
  utils.docker_run(platform, function_name, use_nvidia, shared_mem, env_vars, runtime_functions_path)
}

// Python unittest for CPU
// Python 2
def python2_ut(docker_container_name) {
  timeout(time: max_time, unit: 'MINUTES') {
    docker_run(docker_container_name, 'unittest_ubuntu_python2_cpu', false)
  }
}

// Python 3
def python3_ut(docker_container_name) {
  timeout(time: max_time, unit: 'MINUTES') {
    docker_run(docker_container_name, 'unittest_ubuntu_python3_cpu', false)
  }
}

// Python 3
def python3_ut_asan(docker_container_name) {
  timeout(time: max_time, unit: 'MINUTES') {
    docker_run(docker_container_name, 'unittest_ubuntu_python3_cpu_asan', false)
  }
}

def python3_ut_mkldnn(docker_container_name) {
  timeout(time: max_time, unit: 'MINUTES') {
    docker_run(docker_container_name, 'unittest_ubuntu_python3_cpu_mkldnn', false)
  }
}

// GPU test has two parts. 1) run unittest on GPU, 2) compare the results on
// both CPU and GPU
// Python 2
def python2_gpu_ut(docker_container_name) {
  timeout(time: max_time, unit: 'MINUTES') {
    docker_run(docker_container_name, 'unittest_ubuntu_python2_gpu', true)
  }
}

// Python 3
def python3_gpu_ut(docker_container_name) {
  timeout(time: max_time, unit: 'MINUTES') {
    docker_run(docker_container_name, 'unittest_ubuntu_python3_gpu', true)
  }
}

// Python 3 NOCUDNN
def python3_gpu_ut_nocudnn(docker_container_name) {
  timeout(time: max_time, unit: 'MINUTES') {
    docker_run(docker_container_name, 'unittest_ubuntu_python3_gpu_nocudnn', true)
  }
}

//------------------------------------------------------------------------------------------

def compile_static_unix_cpu() {
  return ["CPU: Static Build": {
    node(NODE_LINUX_CPU) {
      ws("workspace/build-static-cpu") {
        timeout(time: max_time, unit: 'MINUTES') {
          utils.init_git()
          docker_run('publish.ubuntu1404_cpu', 'build_static_python cpu', false)
          utils.pack_lib('cpu_static', mx_static_lib, false)
        }
      }
    }
  }]
}

def compile_static_unix_cpu_mkl() {
  return ["CPU-MKL: Static Build": {
    node(NODE_LINUX_CPU) {
      ws("workspace/build-static-cpu-mkl") {
        timeout(time: max_time, unit: 'MINUTES') {
          utils.init_git()
          docker_run('publish.ubuntu1404_cpu', 'build_static_python mkl', false)
          utils.pack_lib('mkldnn_cpu_static', mx_mkldnn_static_lib, false)
        }
      }
    }
  }]
}

// cuda_variant: cu80, cu90, cu91, cu92, cu100
def compile_static_unix_gpu(cuda_variant) {
  return ["${cuda_variant.toUpperCase()}: static": {
    node(NODE_LINUX_CPU) {
      ws("workspace/build-static-${cuda_variant}") {
        timeout(time: max_time, unit: 'MINUTES') {
          utils.init_git()
          docker_run('publish.ubuntu1404_cpu', "build_static_python ${cuda_variant}", false)
          utils.pack_lib("${cuda_variant}_static", mx_static_lib, false)
        }
      }
    }
  }]
}

// cuda_variant: cu80, cu90, cu91, cu92, cu100
def compile_static_unix_gpu_mkl(cuda_variant) {
  return ["${cuda_variant.toUpperCase()}-MKL: static": {
    node(NODE_LINUX_CPU) {
      ws("workspace/build-static-${cuda_variant}mkl") {
        timeout(time: max_time, unit: 'MINUTES') {
          utils.init_git()
          docker_run('publish.ubuntu1404_cpu', "build_static_python ${cuda_variant}mkl", false)
          utils.pack_lib("mkldnn_${cuda_variant}_static", mx_mkldnn_static_lib, false)
        }
      }
    }
  }]
}

//------------------------------------------------------------------------------------------

def pip_package_unix() {
  return ['Package: CPU': {
    node(NODE_LINUX_CPU) {
      ws('workspace/package-static-cpu') {
        timeout(time: max_time, unit: 'MINUTES') {
          utils.unpack_and_init("cpu_static", mx_static_lib, false)
          docker_run('ubuntu_cpu', "package_static_python cpu", false)
          utils.pack_lib("pip_cpu", mx_pip, false)
          archiveArtifacts artifacts: mx_pip, fingerprint: true
        }
      }
    }
  }]
}

def pip_package_unix_mkl() {
return ['Package: CPU-MKL': {
    node(NODE_LINUX_CPU) {
      ws("workspace/package-static-cpu-mkl") {
        timeout(time: max_time, unit: 'MINUTES') {
          utils.unpack_and_init("mkldnn_cpu_static", mx_mkldnn_static_lib, false)
          docker_run('ubuntu_cpu', "package_static_python mkl", false)
          utils.pack_lib("pip_mkldnn_cpu", mx_pip, false)
          archiveArtifacts artifacts: mx_pip, fingerprint: true
        }
      }
    }
  }]
}

def pip_package_unix_gpu(cuda_variant) {
  return ["Package: GPU-${cuda_variant}": {
      node(NODE_LINUX_GPU) {
        ws("workspace/package-static-gpu-${cuda_variant}") {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.unpack_and_init("${cuda_variant}_static", mx_static_lib, false)
            docker_run("ubuntu_gpu_${cuda_variant}", "package_static_python ${cuda_variant}", true)
            utils.pack_lib("pip_${cuda_variant}", mx_pip, false)
            archiveArtifacts artifacts: mx_pip, fingerprint: true
          }
        }
      }
    }]
}

def pip_package_unix_gpu_mkl(cuda_variant) {
  return ["Package: GPU-MKL-${cuda_variant}": {
      node(NODE_LINUX_GPU) {
        ws("workspace/package-static-gpu-mkl-${cuda_variant}") {
          timeout(time: max_time, unit: 'MINUTES') {
            utils.unpack_and_init("mkldnn_${cuda_variant}_static", mx_mkldnn_static_lib, false)
            docker_run("ubuntu_gpu_${cuda_variant}", "package_static_python ${cuda_variant}mkl", true)
            utils.pack_lib("pip_mkldnn_${cuda_variant}", mx_pip, false)
            archiveArtifacts artifacts: mx_pip, fingerprint: true
          }
        }
      }
    }]
}

//------------------------------------------------------------------------------------------

def is_release_build() {
  return (env.IS_RELEASE_BUILD == 'true')
}

def get_mxnet_version_for_docker_tag() {
  if (is_release_build()) {
    return sh(returnStdout: true, script: """grep -Ei '__version__ = "[0-9].[0-9].[0-9]"' python/mxnet/libinfo.py | grep -oEi '[0-9].[0-9].[0-9]'""").trim()
    } else {
      return 'nightly'
    }
}

def get_python_cmd(python3) {
  return python3 ? 'python3' : 'python'
}

def apply_modifiers(str_in, python3, mkl) {
  def str_out = "${str_in}%MKL%%PY3%"
  str_out = str_out.replaceAll('%MKL%', mkl ? '_mkl' : '')
  str_out = str_out.replaceAll('%PY3%', python3 ? '_py3' : '')
  return str_out
}

def docker_push(tag) {
  def retry_number = 0
  retry(5) {
    sleep(retry_number++ * 60)
    sh "./ci/publish/python/docker_login.py"
    sh "docker push ${tag}"
  }
}

def test_docker_image(tag, variant, python3) {
  def runtime = variant.startsWith('cu') ? '--runtime=nvidia' : ''
  def python_cmd = get_python_cmd(python3)
  sh """docker run ${runtime} -v `pwd`:/mxnet ${tag} bash -c "${python_cmd} /mxnet/tests/python/train/test_conv.py" """
  sh """docker run ${runtime} -v `pwd`:/mxnet ${tag} bash -c "${python_cmd} /mxnet/example/image-classification/train_mnist.py" """
}

def get_base_image(variant) {
  switch (variant) {
      case 'cu80':
          return'nvidia/cuda:8.0-cudnn7-runtime'
      case 'cu90':
          return 'nvidia/cuda:9.0-cudnn7-runtime'
      case 'cu91':
          return 'nvidia/cuda:9.1-cudnn7-runtime'
      case 'cu92':
          return 'nvidia/cuda:9.2-cudnn7-runtime'
      case 'cu100':
          return 'nvidia/cuda:10.0-cudnn7-runtime'
      default:
          return 'ubuntu:16.04'
  }
}

def docker_build(tag, variant, python3) {
  def dockerfile = 'ci/publish/python/Dockerfile'
  def context = 'wheel_build/dist'
  def python_cmd = get_python_cmd(python3)
  def base_image = get_base_image(variant)
  sh "docker build -t ${tag} -f ${dockerfile} --build-arg BASE_IMAGE=${base_image} --build-arg PYTHON_CMD=${python_cmd} ${context}"
}

def get_docker_tag(repo, variant, python3, mkl) {
  def mxnet_version = get_mxnet_version_for_docker_tag()
  def tag = "${repo}/python:${mxnet_version}_${variant}"
  return apply_modifiers(tag, python3, mkl)
}

def build_docker_release(variant, python3, mkl) {
  def repo = 'perdasilva' // TODO: Make Jenkins env var
  def tag = get_docker_tag(repo, variant, python3, mkl)
  def workspace = apply_modifiers("docker_build_${variant}", python3, mkl)
  def node_type = variant.startsWith('cu') ? NODE_LINUX_GPU : NODE_LINUX_CPU
  def stash = mkl ? "pip_mkldnn_${variant}" : "pip_${variant}"

  return ["Docker Build: ${tag}": {
    node(node_type) {
      ws("${workspace}") {
        timeout(time: max_time, unit: 'MINUTES') {
          utils.unpack_and_init("${stash}", mx_pip, false)
          docker_build(tag, variant, python3)
          test_docker_image(tag, variant, python3)
          docker_push(tag)
        }
      }
    }
  }]
}

//------------------------------------------------------------------------------------------

def test_unix_python2_cpu() {
    return ['Python2: CPU': {
      node(NODE_LINUX_CPU) {
        ws('workspace/ut-python2-cpu') {
          try {
            utils.unpack_and_init('cpu_static', mx_static_lib)
            python2_ut('ubuntu_cpu')
          } finally {
            utils.collect_test_results_unix('nosetests_unittest.xml', 'nosetests_python2_cpu_unittest.xml')
            utils.collect_test_results_unix('nosetests_train.xml', 'nosetests_python2_cpu_train.xml')
            utils.collect_test_results_unix('nosetests_quantization.xml', 'nosetests_python2_cpu_quantization.xml')
          }
        }
      }
    }]
}

def test_unix_python2_gpu(cuda_variant) {
    return ["Python2: GPU-${cuda_variant.toUpperCase()}": {
      node(NODE_LINUX_GPU) {
        ws("workspace/ut-python2-${cuda_variant}") {
          try {
            utils.unpack_and_init("${cuda_variant}_static", mx_static_lib)
            python2_gpu_ut("ubuntu_gpu_${cuda_variant}")
          } finally {
            utils.collect_test_results_unix('nosetests_gpu.xml', 'nosetests_python2_gpu.xml')
          }
        }
      }
    }]
}

def test_unix_python2_quantize_gpu(cuda_variant) {
    return ["Python2: Quantize GPU-${cuda_variant}": {
      node(NODE_LINUX_GPU_P3) {
        ws("workspace/ut-python2-quantize-${cuda_variant}") {
          timeout(time: max_time, unit: 'MINUTES') {
            try {
              utils.unpack_and_init("${cuda_variant}_static", mx_static_lib)
              docker_run("ubuntu_gpu_${cuda_variant}", 'unittest_ubuntu_python2_quantization_gpu', true)
            } finally {
              utils.collect_test_results_unix('nosetests_quantization_gpu.xml', 'nosetests_python2_quantize_gpu.xml')
            }
          }
        }
      }
    }]
}

def test_unix_python2_mkldnn_gpu(cuda_variant) {
    return ["Python2: MKLDNN-GPU-${cuda_variant}": {
      node(NODE_LINUX_GPU) {
        ws("workspace/ut-python2-mkldnn-${cuda_variant}") {
          try {
            utils.unpack_and_init("mkldnn_${cuda_variant}_static", mx_mkldnn_static_lib)
            python2_gpu_ut("ubuntu_gpu_${cuda_variant}")
          } finally {
            utils.collect_test_results_unix('nosetests_gpu.xml', 'nosetests_python2_mkldnn_gpu.xml')
          }
        }
      }
    }]
}

def test_unix_python3_cpu() {
    return ['Python3: CPU': {
      node(NODE_LINUX_CPU) {
        ws('workspace/ut-python3-cpu') {
          try {
            utils.unpack_and_init('cpu_static', mx_static_lib)
            python3_ut('ubuntu_cpu')
          } finally {
            utils.collect_test_results_unix('nosetests_unittest.xml', 'nosetests_python3_cpu_unittest.xml')
            utils.collect_test_results_unix('nosetests_quantization.xml', 'nosetests_python3_cpu_quantization.xml')
          }
        }
      }
    }]
}

def test_unix_python3_mkl_cpu() {
    return ['Python3: MKL-CPU': {
      node(NODE_LINUX_CPU) {
        ws('workspace/ut-python3-cpu') {
          try {
            utils.unpack_and_init('mkldnn_cpu_static', mx_static_lib)
            python3_ut('ubuntu_cpu')
          } finally {
            utils.collect_test_results_unix('nosetests_unittest.xml', 'nosetests_python3_cpu_unittest.xml')
            utils.collect_test_results_unix('nosetests_quantization.xml', 'nosetests_python3_cpu_quantization.xml')
          }
        }
      }
    }]
}

def test_unix_python3_gpu(cuda_variant) {
    return ["Python3: GPU-${cuda_variant.toUpperCase()}": {
      node(NODE_LINUX_GPU) {
        ws("workspace/ut-python3-${cuda_variant}") {
          try {
            utils.unpack_and_init("${cuda_variant}_static", mx_static_lib)
            python3_gpu_ut("ubuntu_gpu_${cuda_variant}")
          } finally {
            utils.collect_test_results_unix('nosetests_gpu.xml', 'nosetests_python3_gpu.xml')
          }
        }
      }
    }]
}

def test_unix_python3_quantize_gpu(cuda_variant) {
    return ["Python3: Quantize GPU-${cuda_variant.toUpperCase()}": {
      node(NODE_LINUX_GPU_P3) {
        ws("workspace/ut-python3-quantize-${cuda_variant.toUpperCase()}") {
          timeout(time: max_time, unit: 'MINUTES') {
            try {
              utils.unpack_and_init("${cuda_variant}_static", mx_static_lib)
              docker_run("ubuntu_gpu_${cuda_variant}", 'unittest_ubuntu_python3_quantization_gpu', true)
            } finally {
              utils.collect_test_results_unix('nosetests_quantization_gpu.xml', 'nosetests_python3_quantize_gpu.xml')
            }
          }
        }
      }
    }]
}

def test_unix_python2_mkldnn_cpu() {
    return ['Python2: MKLDNN-CPU': {
      node(NODE_LINUX_CPU) {
        ws('workspace/ut-python2-mkldnn-cpu') {
          try {
            utils.unpack_and_init('mkldnn_cpu_static', mx_mkldnn_static_lib)
            python2_ut('ubuntu_cpu')
          } finally {
            utils.collect_test_results_unix('nosetests_unittest.xml', 'nosetests_python2_mkldnn_cpu_unittest.xml')
            utils.collect_test_results_unix('nosetests_train.xml', 'nosetests_python2_mkldnn_cpu_train.xml')
            utils.collect_test_results_unix('nosetests_quantization.xml', 'nosetests_python2_mkldnn_cpu_quantization.xml')
          }
        }
      }
    }]
}

def test_unix_python3_mkldnn_cpu() {
  return ['Python3: MKLDNN-CPU': {
    node(NODE_LINUX_CPU) {
      ws('workspace/ut-python3-mkldnn-cpu') {
        try {
          utils.unpack_and_init('mkldnn_cpu_static', mx_mkldnn_static_lib)
          python3_ut_mkldnn('ubuntu_cpu')
        } finally {
          utils.collect_test_results_unix('nosetests_unittest.xml', 'nosetests_python3_mkldnn_cpu_unittest.xml')
          utils.collect_test_results_unix('nosetests_mkl.xml', 'nosetests_python3_mkldnn_cpu_mkl.xml')
        }
      }
    }
  }]
}

def test_unix_python3_mkldnn_mkl_cpu() {
  return ['Python3: MKLDNN-MKL-CPU': {
    node(NODE_LINUX_CPU) {
      ws('workspace/ut-python3-mkldnn-mkl-cpu') {
        try {
          utils.unpack_and_init('mkldnn_cpu_static', mx_mkldnn_static_lib)
          python3_ut_mkldnn('ubuntu_cpu')
        } finally {
          utils.collect_test_results_unix('nosetests_unittest.xml', 'nosetests_python3_mkldnn_cpu_unittest.xml')
          utils.collect_test_results_unix('nosetests_mkl.xml', 'nosetests_python3_mkldnn_cpu_mkl.xml')
        }
      }
    }
  }]
}

def test_unix_python3_mkldnn_gpu(cuda_variant) {
  return ["Python3: MKLDNN-GPU-${cuda_variant.toUpperCase()}": {
    node(NODE_LINUX_GPU) {
      ws("workspace/ut-python3-mkldnn-gpu-${cuda_variant}") {
        try {
          utils.unpack_and_init("mkldnn_${cuda_variant}_static", mx_mkldnn_static_lib)
          python3_gpu_ut("ubuntu_gpu_${cuda_variant}")
        } finally {
          utils.collect_test_results_unix('nosetests_gpu.xml', 'nosetests_python3_mkldnn_gpu.xml')
        }
      }
    }
  }]
}

def test_unix_python3_integration_gpu(cuda_variant) {
  return ["Python Integration GPU-${cuda_variant.toUpperCase()}": {
    node(NODE_LINUX_GPU) {
      ws("workspace/it-python-${cuda_variant}") {
        timeout(time: max_time, unit: 'MINUTES') {
          utils.unpack_and_init("${cuda_variant}_static", mx_static_lib)
          docker_run("ubuntu_gpu_${cuda_variant}", 'integrationtest_ubuntu_gpu_python', true)
        }
      }
    }
  }]
}

def test_unix_distributed_kvstore_cpu() {
  return ['dist-kvstore tests CPU': {
    node(NODE_LINUX_CPU) {
      ws('workspace/it-dist-kvstore') {
        timeout(time: max_time, unit: 'MINUTES') {
          utils.unpack_and_init('cpu_static', mx_static_lib)
          docker_run('ubuntu_cpu', 'integrationtest_ubuntu_cpu_dist_kvstore', false)
        }
      }
    }
  }]
}

def test_unix_distributed_kvstore_gpu(cuda_variant) {
  return ["dist-kvstore tests GPU-${cuda_variant.toUpperCase()}": {
    node(NODE_LINUX_GPU) {
      ws("workspace/it-dist-kvstore-${cuda_variant}") {
        timeout(time: max_time, unit: 'MINUTES') {
          utils.unpack_and_init("${cuda_variant}_static", mx_static_lib)
          docker_run("ubuntu_gpu_${cuda_variant}", 'integrationtest_ubuntu_gpu_dist_kvstore', true)
        }
      }
    }
  }]
}

return this
